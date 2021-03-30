(ns teet.integration.x-road.property-registry
  (:require [clojure.data.zip.xml :as z]
            [clojure.zip]
            [clojure.data.zip]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [clojure.java.io :as io]
            [teet.integration.x-road.core :as x-road]
            [teet.integration.postgrest :as postgrest]
            [clojure.set :as set]))

(defn kr-kinnistu-d-request-xml [{:keys [instance-id xroad-kr-subsystem-id
                                         registriosa-nr requesting-eid]}]
  (x-road/request-xml
   (x-road/request-envelope
    {:instance-id instance-id
     :requesting-eid requesting-eid
     :client {:member-code "70001490"
              :subsystem-code (if xroad-kr-subsystem-id
                                xroad-kr-subsystem-id
                                (do (log/warn "x-road kr client subsystem id unconfigured, defaulting to generic-consumer")
                                    "generic-consumer"))}
     :service {:member-code "70000310"
               :subsystem-code "kr"
               :service-code "Kinnistu_Detailandmed"}}
    [:kr:Kinnistu_Detailandmed {:xmlns:kr "http://kr.x-road.eu"
                                :xmlns:kin "http://schemas.datacontract.org/2004/07/KinnistuService.DTO"}
     [:kr:request
      [:kin:jao_nr "0,1,2,3,4"]
      [:kin:kande_kehtivus "1"]
      ;; [:kin:kande_kehtivus "0"] ;; to also see not in force items
      [:kin:kasutajanimi]
      [:kin:parool]
      [:kin:registriosa_nr registriosa-nr]]])))

(def ^:private text (comp str/trim z/text))

(defn d-sihtotstarve [s-xml]
  {:sihtotstarve (z/xml1-> s-xml :a:Sihtotstarve :a:sihtotstarve text)
   :jrk (z/xml1-> s-xml :a:Sihtotstarve :a:jrk text)
   :protsent (z/xml1-> s-xml :a:Sihtotstarve :a:protsent text)
   :sihtotstarve_tekst (z/xml1-> s-xml :a:Sihtotstarve :a:sihtotstarve_tekst text)})

(defn d-cadastral-unit* [k-xml]
  {:ads_oid (z/xml1-> k-xml :a:aadressobjekt :a:ads_oid text)
   :katastritunnus (z/xml1-> k-xml :a:KinnistuKatastriyksus :a:katastritunnus text)
   :katastriyksuse_aadress (z/xml1-> k-xml :a:KinnistuKatastriyksus :a:katastriyksuse_aadress text)
   :pindala (z/xml1-> k-xml :a:KinnistuKatastriyksus :a:pindala text)
   :pindala_yhik (z/xml1-> k-xml :a:KinnistuKatastriyksus :a:pindala_yhik text)
   :plaanialusel (z/xml1-> k-xml :a:KinnistuKatastriyksus :a:plaanialusel text)
   :sihtotstarbed (mapv d-sihtotstarve (z/xml-> k-xml :a:KinnistuKatastriyksus :a:sihtotstarbed))})

(defn d-cadastral-units [kdr-xml]
  ;;; kdr = Kinnistu_DetailamdResponse from inside the soap body
  (let [k-data-seq (z/xml-> kdr-xml :jagu1 :a:Jagu1 :a:katastriyksused :a:KinnistuKatastriyksus)]
    {:katastriyksus (mapv d-cadastral-unit* k-data-seq)}))

(defn d-estate-name [kdr-xml]
  ;; There can be several instances of jagu0 information, which represents the change history.
  ;; Usually there seems to be only one record with the name, and possibly another time period when the name is blank.
  ;; If the need arises, we can implement more logic here in the future.
  (let [k-data-seq (z/xml-> kdr-xml :jagu0 :a:Jagu0 :a:nimi)
        res {:nimi (str/join ", "
                             (filter (complement str/blank?)
                                     (mapv #(z/xml1-> % text) k-data-seq)))}]
    res))

(defn d-property-owners [kdr-xml]
  ;;; kdr = Kinnistu_DetailamdResponse from inside the soap body
  (let [owner-xml-seq (z/xml-> kdr-xml :jagu2 :a:Jagu2 :a:omandiosad :a:Jagu2.Omandiosa)
        oo-get (fn oo-get [ox & path]
                  (apply z/xml1-> (concat [ox] path)))
        omandi-get (fn [ox & path] (apply oo-get ox (concat path [text])))]
    {:omandiosad
     (mapv (fn [ox]
             {:isik (mapv (fn [k-isik]
                            {:isiku_tyyp (z/xml1-> k-isik :a:isiku_tyyp z/text)
                             :isiku_liik_id (z/xml1-> k-isik :a:isiku_liik_ID z/text)
                             :isiku_liik (z/xml1-> k-isik :a:isiku_liik z/text)
                             :r_kood (z/xml1-> k-isik  :a:isiku_koodid :a:Isiku_kood :a:r_kood z/text)
                             :r_riik (z/xml1-> k-isik :a:isiku_koodid :a:Isiku_kood :a:r_riik z/text)
                             :nimi (z/xml1-> k-isik :a:nimi z/text)
                             :eesnimi (z/xml1-> k-isik :a:eesnimi z/text)})
                          (z/xml-> ox :a:isikud :a:KinnistuIsik))
              :omandi_liik (omandi-get ox :a:omandi_liik)
              :omandi_liik_tekst (omandi-get ox :a:omandi_liik_tekst)
              :omandi_algus (omandi-get ox :a:omandi_algus)
              :omandiosa_lugeja (omandi-get ox :a:omandiosa_lugeja)
              :omandiosa_nimetaja (omandi-get ox :a:omandiosa_nimetaja)
              :omandiosa_suurus (omandi-get ox :a:omandiosa_suurus)})
           owner-xml-seq)}))

(defn d-jagu34* [key children sequential-vals?]
  ;; This is the recursive part of the parsing.
  ;; the sequential-vals and maybe-vectorise business is about
  ;; getting the recursive subelements into a form that will be
  ;; handled suitably by (merge-with concat map1 map2 .. ), so we get eg this nested
  ;; structure right:

  ;; {:koormatise_rahaline_vaartus "0",
  ;;  :kande_alusdokumendid
  ;;  [{:Kinnistamisavaldus
  ;;    ({:aasta "0",
  ;;       [...]
  ;;      :tehingu_nr ""}
  ;;     {:aasta "2006",
  ;;      :avalduse_esitaja_liik "notar",
  ;;      [...]
  ;;
  ;; (possibly it would be better done with postwalk-ing the xml tree, maybe next iteration)


  (let [leaf? (fn [m] (->> m
                           :content
                           (every? (complement map?))))
        leaves (filterv leaf? children)
        subs (filterv (complement leaf?) children)
        tag-without-prefix #(some->> %
                             :tag
                             name
                             (partition-by (partial = \:))
                             last
                             clojure.string/join
                             keyword)
        joined-content (comp (partial clojure.string/join "\n") :content)
        recursed-map (apply (partial merge-with concat)
                            (mapv (fn [s]
                                    (d-jagu34* (tag-without-prefix s) (:content s) true)) subs))
        leaves-map (into {} (mapv
                      (juxt tag-without-prefix joined-content)
                      leaves))
        ;; maybe-vectorise will be false on topmost call, and true in all recursive calls,
        ;; because we know we won't need to merge the top level data with other maps.
        ;; otherwise we defensively vectorise all structured (non-string) children so
        ;; they can be merged with merge-with concat
        maybe-vectorise (if sequential-vals?
                          (fn [val] [val])
                          identity)]
    {key (maybe-vectorise
          (merge recursed-map
                 leaves-map))}))

(declare parse-kande-tekst)

(defn d-jagu34 [k-xml jagu-key]
  ;; jagu-key will be :jagu3 or :jagu4
  (let [;; children will have eg subtree Jagu3  elements (from under the containing jagu3 element)
        children (z/xml-> k-xml jagu-key clojure.zip/children)
        jagu-maps (mapv #(d-jagu34* jagu-key (:content %) false)
                        children)
        postprocess (fn [jagu-map]
                      (assert (:kande_tekst jagu-map))
                      (-> jagu-map
                          (update :kande_tekst parse-kande-tekst)
                          (update :kande_alus parse-kande-tekst)))]
    {jagu-key (mapv postprocess
                    (mapv jagu-key jagu-maps))}))


(def jagu34-summary-fields [:kande_liik_tekst
                            :kande_kehtivus
                            :kande_tekst
                            :oiguse_seisund_tekst])

(defn summary-jagu34-only [k-resp]
  ;; keeps only jagu34-summary-fields in the maps the maps in the seq under :jagu3 and :jagu4.
  (let [map-fn (fn [ms]
                 (map #(select-keys % jagu34-summary-fields)
                      ms))]
    (-> k-resp
        (update :jagu3 map-fn)
        (update :jagu4 map-fn))))

(defn active-jagu34-only [k-resp]
  ;; prunes "lopetatud" records from the maps in the seq under :jagu3 and keeps only "Aktiivne" in :jagu4.
  ;; {:jagu3 [... {:oiguse_seisund "L", ..} ...]
  (let [jagu3-filter-fn #(filter (comp (partial not= "L") :oiguse_seisund) %)
        jagu4-filter-fn #(filter (comp (partial = "Aktiivne") :oiguse_seisund_tekst) %)]
    (-> k-resp
        (update :jagu3 jagu3-filter-fn)
        (update :jagu4 jagu4-filter-fn))))

(defn cell-to-text [cell]
  ;; parse-kande-tekst util
  (mapv str (z/xml-> cell clojure.data.zip/descendants clojure.zip/node string?)))

(defn parse-kande-tekst [kande-tekst]
  ;; this assumes, like in all cases seen during development, that
  ;; kande_tekst field always contains a xhtml table with 2 columns,
  ;; with cells containing plain text interspersed with <br> elements
  (when (not (str/blank? kande-tekst))
    (let [row-seq (-> kande-tekst
                      (.getBytes "UTF-8")
                      io/input-stream
                      x-road/unexceptional-xml-parse
                      clojure.zip/xml-zip (z/xml-> :table :tr))
          parsed (for [row row-seq]
                   (mapv cell-to-text (z/xml-> row :td)))]
      (if (or (empty? parsed)
              (= 1 (count (first parsed))))
        (when (not= "<root/>" kande-tekst)
          (log/debug "parsed is" (vec parsed))
          (log/error "couldn't parse non-empty kande_tekst without table or <root/>:" kande-tekst))
        parsed))))

(defn kinnistu-d-parse-success
  "Parse estate information from successful SOAP response body element."
  [d-response]
  (merge {:status :ok}
         (d-cadastral-units d-response)
         (d-property-owners d-response)
         (d-estate-name d-response)
         (d-jagu34 d-response :jagu3)
         (d-jagu34 d-response :jagu4)))

(defn kinnistu-d-parse-response [zipped-xml]
  (let [d-response (z/xml1-> zipped-xml :s:Envelope :s:Body :Kinnistu_DetailandmedResponse)
        d-status (z/xml1-> zipped-xml :s:Envelope :s:Body :Kinnistu_DetailandmedResponse :teade text)
        d-fault (or (z/xml1-> zipped-xml :SOAP-ENV:Envelope :SOAP-ENV:Body :SOAP-ENV:Fault text)
                    (z/xml1-> zipped-xml :s:Envelope :s:Body :Kinnistu_DetailandmedResponse :faultString text))]
    (if d-response
      (if (= "OK" d-status)
        (kinnistu-d-parse-success d-response)
        ;; else
        (do
          (log/error "property register non-ok status string:" d-status)
          {:status :error
           :fault (str "teade: " d-status)}))
      ;; else
      (if d-fault
        (do
          (log/error "propety register soap fault:" d-fault)
          {:status :error
           :fault d-fault})
        ;; else
        (do
          (log/error "property register empty response")
          {:status :error
           :fault "empty response"})))))


(defn perform-kinnistu-d-request
  "query-params map should kave keys :instance-id, :reg-nr and :requesting-eid"
  [url query-params]
  (->> query-params
       kr-kinnistu-d-request-xml
       (x-road/perform-request url)))

(defn- store-cached-estate-payload [ctx estate-id payload]
  (postgrest/upsert! ctx :estate
                     [{:id estate-id
                       :payload payload}]))

(defn- fetch-and-cache-estate-info
  "Fetch estate info from property registry, cache into db, return parsed result"
  [ctx estate-id]
  (let [response-string
        (perform-kinnistu-d-request
          (:xroad-url ctx)
          (merge ctx {:registriosa-nr estate-id}))
        zipped-xml (x-road/string->zipped-xml response-string)]
    (store-cached-estate-payload ctx estate-id response-string)
    (kinnistu-d-parse-response zipped-xml)))

(defn fetch-all-estate-info
  "Fetch estate info for the given estate ids, trying the db cache
  first, then fetching the missing ones from the property registry."
  [ctx estate-ids]
  (let [estates (into {}
                      (map (juxt :id :payload))
                      (map #(update % :payload (fn [val]
                                                 (-> val
                                                     x-road/string->zipped-xml
                                                     kinnistu-d-parse-response)))
                           (postgrest/select ctx :estate #{:id :payload}
                                             {:id [:in estate-ids]})))
        missing (set/difference (set estate-ids) (set (keys estates)))]
    (into estates
          (for [id missing]
            [id (fetch-and-cache-estate-info ctx id)]))))

(defn ensure-cached-estate-info
  "Ensure the given estates are in the cache and fetch/store it if not."
  ([ctx estate-ids]
   (ensure-cached-estate-info ctx estate-ids false))
  ([ctx estate-ids force-refetch?]
   (let [cached (if force-refetch?
                  #{}
                  (into #{}
                        (map :id)
                        (postgrest/select ctx :estate #{:id} {:id [:in estate-ids]})))
         missing (set/difference (set estate-ids) cached)]
     (doseq [id missing]
       (log/info "Fetch and cache estate info:" id)
       (fetch-and-cache-estate-info ctx id)))))

;; repl notes:

;; test land reg ids: 233102, 284502, 308104 (last one has empty jag3/jag4)
;; (def *r  (perform-kinnistu-d-request "http://localhost:12073" {:instance-id "ee-dev" :registriosa-nr "308104" :requesting-eid <my-test-eid>}))

;; rr testing:
;; (def *r2 (perform-rr442-request "http://localhost:12073" {:instance-id "ee-dev" :subject-eid "47003280318" :requesting-eid "EE47003280318"}))
;; note - subject-eid without EE prefix, requesting-eid with EE prefix
