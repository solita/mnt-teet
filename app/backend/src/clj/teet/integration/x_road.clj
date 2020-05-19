(ns teet.integration.x-road
  (:require [clojure.xml :as xml]
            [clojure.data.zip.xml :as z]
            [clojure.zip]
            [clojure.data.zip]
            [clojure.string]
            [hiccup.core :as hiccup]
            [org.httpkit.client :as htclient]
            [taoensso.timbre :as log]
            [clojure.java.io :as io])
  (:import (java.util UUID)))

;; see also https://x-tee.ee/catalogue-data/ee-dev/ee-dev/GOV/70008440/rr/155.wsdl

(defn rr442-request-hiccup [{:keys [instance-id requesting-eid subject-eid]}]
  [:soap:Envelope {:xmlns:soap "http://schemas.xmlsoap.org/soap/envelope/"
                   :xmlns:xrd "http://x-road.eu/xsd/xroad.xsd"
                   :xmlns:ns5 "http://rr.x-road.eu/producer"
                   :xmlns:id "http://x-road.eu/xsd/identifiers"}
   [:soap:Header
    [:xrd:userId requesting-eid]
    [:xrd:id (UUID/randomUUID)]
    [:xrd:protocolVersion "4.0"]
    [:xrd:client {:id:objectType "SUBSYSTEM"}
     [:id:xRoadInstance instance-id]
     [:id:memberClass "GOV"]
     [:id:memberCode "70001490"]
     [:id:subsystemCode "liiklusregister"]]
    [:xrd:service {:id:objectType "SERVICE"}
     [:id:xRoadInstance instance-id]
     [:id:memberClass "GOV"]
     [:id:memberCode "70008440"]
     [:id:subsystemCode "rr"]
     [:id:serviceCode "RR442"]
     [:id:serviceVersion "v3"]]]
   [:soap:Body
    [:ns5:RR442
     [:request
      [:Isikukood subject-eid]]]]])

(defn rr442-request-xml [qmap]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" (hiccup/html (rr442-request-hiccup qmap))))

(defn rr442-parse-name [xml-string]
  (let [xml (xml/parse xml-string)
        zipped-xml (clojure.zip/xml-zip xml)
        fault (or
               (z/xml1-> zipped-xml :SOAP-ENV:Envelope :SOAP-ENV:Body :SOAP-ENV:Fault z/text)
               (z/xml1-> zipped-xml :SOAP-ENV:Body :prod:RR442Response :response :faultString z/text))
        avaldaja (z/xml1-> zipped-xml :SOAP-ENV:Envelope :SOAP-ENV:Body :prod:RR442Response :response :Avaldaja)
        fields [:Eesnimi :Perenimi :Isikukood]
        fieldname->kvpair (fn [fieldname]
                            [fieldname (z/xml1-> avaldaja fieldname z/text)])]
    (if fault
      (do
        (log/error "population register soap fault:", fault)
        {:status :error :fault (str "rr soap response reports fault: fault")})
      (if avaldaja
        (merge {:status :ok}
               (into {} (mapv fieldname->kvpair fields)))
        (do
          (log/error "pop register empty response without fault code")
          {:status :error
           :fault "no fault code or Avaldaja section found in pop register response"})))))

(defn perform-rr442-request
  "params needs to have keys :instance-id, :requesting-eid, & :subject-eid"
  [url params]
    (let [req (rr442-request-xml params)

        resp-atom (htclient/post url {:body req
                                      :as :stream
                                      :headers {"Content-Type" "text/xml; charset=UTF-8"}})
        resp (deref resp-atom)]
    (if (= 200 (:status resp))
      (rr442-parse-name (:body resp))
      ;; else
      (let [msg (str "http error communicating to x-road, error=" (:error resp) ", http status=" (:status resp))]
        (log/error msg)
        {:status :error
         :result msg}))))

(defn kr-kinnistu-d-request-xml [{:keys [instance-id xroad-kr-subsystem-id
                                         registriosa-nr requesting-eid]}]
  ;; xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
  ;; xmlns:xro="http://x-road.eu/xsd/xroad.xsd"
  ;; xmlns:iden="http://x-road.eu/xsd/identifiers"
  ;; xmlns:kr="http://kr.x-road.eu"
  ;; xmlns:kin="http://schemas.datacontract.org/2004/07/KinnistuService.DTO"
  (let [req-hic
        [:soap:Envelope {:xmlns:soap "http://schemas.xmlsoap.org/soap/envelope/"
                         :xmlns:xrd "http://x-road.eu/xsd/xroad.xsd"
                         :xmlns:kr "http://kr.x-road.eu"
                         :xmlns:kin "http://schemas.datacontract.org/2004/07/KinnistuService.DTO"
                         :xmlns:id "http://x-road.eu/xsd/identifiers"}
         [:soap:Header
          [:xrd:userId requesting-eid]
          [:xrd:id (UUID/randomUUID)]
          [:xrd:protocolVersion "4.0"]
          [:xrd:client {:id:objectType "SUBSYSTEM"}
           [:id:xRoadInstance instance-id]
           [:id:memberClass "GOV"]
           [:id:memberCode "70001490"]
           [:id:subsystemCode (if xroad-kr-subsystem-id
                                xroad-kr-subsystem-id
                                (do (log/warn "x-road kr client subsystem id unconfigured, defaulting to generic-consumer")
                                    "generic-consumer"))]]
          [:xrd:service {:id:objectType "SERVICE"}
           [:id:xRoadInstance instance-id]
           [:id:memberClass "GOV"]
           [:id:memberCode "70000310"]
           [:id:subsystemCode "kr"]
           [:id:serviceCode "Kinnistu_Detailandmed"]
           ;; [:id:serviceVersion ""] ;; was omitted in example SOAP as well
           ]]
         [:soap:Body
          [:kr:Kinnistu_Detailandmed
           [:kr:request
            [:kin:jao_nr "0,1,2,3,4"]
            [:kin:kande_kehtivus "1"]
            [:kin:kasutajanimi]
            [:kin:parool]
            [:kin:registriosa_nr registriosa-nr]]]]]]
    (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" (hiccup/html req-hic))))


(defn d-sihtotstarve [s-xml]
  {:sihtotstarve (z/xml1-> s-xml :a:Sihtotstarve :a:sihtotstarve z/text)
   :jrk (z/xml1-> s-xml :a:Sihtotstarve :a:jrk z/text)
   :protsent (z/xml1-> s-xml :a:Sihtotstarve :a:protsent z/text)
   :sihtotstarve_tekst (z/xml1-> s-xml :a:Sihtotstarve :a:sihtotstarve_tekst z/text)})

(defn d-cadastral-unit* [k-xml]
  {:ads_oid (z/xml1-> k-xml :a:aadressobjekt :a:ads_oid z/text)
   :katastritunnus (z/xml1-> k-xml :a:KinnistuKatastriyksus :a:katastritunnus z/text)
   :katastriyksuse_aadress (z/xml1-> k-xml :a:KinnistuKatastriyksus :a:katastriyksuse_aadress z/text)
   :pindala (z/xml1-> k-xml :a:KinnistuKatastriyksus :a:pindala z/text)
   :pindala_yhik (z/xml1-> k-xml :a:KinnistuKatastriyksus :a:pindala_yhik z/text)
   :plaanialusel (z/xml1-> k-xml :a:KinnistuKatastriyksus :a:plaanialusel z/text)
   :sihtotstarbed (mapv d-sihtotstarve (z/xml-> k-xml :a:KinnistuKatastriyksus :a:sihtotstarbed))})

(defn d-cadastral-units [kdr-xml]
  ;;; kdr = Kinnistu_DetailamdResponse from inside the soap body
  (let [k-data-seq (z/xml-> kdr-xml :jagu1 :a:Jagu1 :a:katastriyksused :a:KinnistuKatastriyksus)]
    {:katastriyksus (mapv d-cadastral-unit* k-data-seq)}))

(defn d-property-owners [kdr-xml]
  ;;; kdr = Kinnistu_DetailamdResponse from inside the soap body
  (let [owner-xml-seq (z/xml-> kdr-xml :jagu2 :a:Jagu2 :a:omandiosad :a:Jagu2.Omandiosa)
        ;; _ (def *ox owner-xml-seq)
        oo-get (fn oo-get [ox & path]
                  (apply z/xml1-> (concat [ox] path)))
        isikud-get (fn [ox & path] (apply oo-get ox (concat [:a:isikud] path [z/text])))
        omandi-get (fn [ox & path] (apply oo-get ox (concat path [z/text])))]
    {:omandiosad
     (mapv (fn [ox]
             {:isiku_tyyp (isikud-get ox :a:KinnistuIsik :a:isiku_tyyp)
              :r_kood (isikud-get ox :a:KinnistuIsik :a:isiku_koodid :a:Isiku_kood :a:r_kood)
              :r_riik (isikud-get ox :a:KinnistuIsik :a:isiku_koodid :a:Isiku_kood :a:r_riik)
              :nimi (isikud-get ox :a:KinnistuIsik :a:nimi)
              :eesnimi (isikud-get ox :a:KinnistuIsik :a:eesnimi)
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

(defn d-jagu34 [k-xml p1]
  (let [;; children will have eg subtree Jagu3  elements (from under the containing jagu3 element)
        children (z/xml-> k-xml p1 clojure.zip/children)
        jagu-maps (mapv #(d-jagu34* p1 (:content %) false)
                        children)]
    {p1 (mapv p1 jagu-maps)}))


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
  ;; prunes "lopetatud" records from the maps in the seq under :jagu3 and :jagu4.
  ;; {:jagu3 [... {:oiguse_seisund "L", ..} ...]
  (let [filter-fn #(filter (comp (partial not= "L") :oiguse_seisund) %)]
    (-> k-resp
        (update :jagu3 filter-fn)
        (update :jagu4 filter-fn))))

(defn cell-to-text [cell]
  ;; parse-kande-tekst util
  (mapv str (z/xml-> cell clojure.data.zip/descendants clojure.zip/node string?)))

(defn unexceptional-xml-parse [input]
  (try
    (xml/parse input)
    (catch Exception _
      ;; caller has to log error about input resulting in nil parse
      nil)))

(defn parse-kande-tekst [kande-tekst-str]
  ;; this assumes, like in all cases seen during development, that
  ;; kande_tekst field always contains a xhtml table with 2 columns,
  ;; with cells containing plain text interspersed with <br> elements
  (when (not (clojure.string/blank? kande-tekst-str))
    (let [row-seq (-> kande-tekst-str
                      (.getBytes "UTF-8")
                      io/input-stream
                      unexceptional-xml-parse
                      clojure.zip/xml-zip (z/xml-> :table :tr))
          parsed (for [row row-seq]
                   (mapv cell-to-text (z/xml-> row :td)))]
      (when (or (empty? parsed)
                (not= 2 (count (first parsed))))
        (log/error "couldn't parse non-empty kande_tekst without table:" kande-tekst-str)))))


(defn kinnistu-d-parse-response [xml-string]
  (let [xml (xml/parse (io/input-stream
                        (.getBytes xml-string "UTF-8")))
        zipped-xml (clojure.zip/xml-zip xml)
        d-response (z/xml1-> zipped-xml :s:Envelope :s:Body :Kinnistu_DetailandmedResponse)
        d-status (z/xml1-> zipped-xml :s:Envelope :s:Body :Kinnistu_DetailandmedResponse :teade z/text)
        d-fault (or (z/xml1-> zipped-xml :SOAP-ENV:Envelope :SOAP-ENV:Body :SOAP-ENV:Fault z/text)
                    (z/xml1-> zipped-xml :s:Envelope :s:Body :Kinnistu_DetailandmedResponse :faultString z/text))
        ;; _ (def *x d-response)
        ;; _ (def *x0 zipped-xml)
        ]
    (if d-response
      (if (= "OK" d-status)
        (merge {:status :ok}
               (d-cadastral-units d-response)
               (d-property-owners d-response)
               (d-jagu34 d-response :jagu3)
               (d-jagu34 d-response :jagu4))
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

(defn unpeel-multipart [ht-resp]
  ;; Kludge to decode multipart/related, as this is not supported by the HTTP client lib
  (let [c-type (:content-type (:headers ht-resp))
        multipart-match (re-find #"multipart/related;.*boundary=\"([^\"]+)\"" c-type)
        body (slurp (:body ht-resp))
        xml-match (re-find #"(?is).*(<\?xml.*Envelope>)" body)
        soap-msg (second xml-match)
        boundary (second multipart-match)]
    (if (and boundary soap-msg (clojure.string/includes? soap-msg boundary))
      (throw (ex-info "received more than 1 part in multipart response, not implemented"
                      {}))
      (if-not multipart-match
        body
        soap-msg))))

(defn perform-kinnistu-d-request
  "query-params map should kave keys :instance-id, :reg-nr and :requesting-eid"
  [url query-params]

  (let [req (kr-kinnistu-d-request-xml query-params)
        resp-atom (htclient/post url {:body req
                                      :as :stream
                                      :headers {"Content-Type" "text/xml; charset=UTF-8"}})
        resp (deref resp-atom)]
    (if (= 200 (:status resp))
      (kinnistu-d-parse-response (unpeel-multipart resp))
      ;; else
      (let [msg (str "http error communicating to x-road, error=" (:error resp) ", http status=" (:status resp))]
        (log/error msg)
        {:status :error
         :result msg}))))

;; repl notes:

;; test land reg ids: 233102, 284502, 308104 (last one has empty jag3/jag4)
;; (def *r  (perform-kinnistu-d-request "http://localhost:12073" {:instance-id "ee-dev" :registriosa-nr "308104" :requesting-eid <my-test-eid>}))

;; rr testing:
;; (def *r2 (perform-rr442-request "http://localhost:12073" {:instance-id "ee-dev" :subject-eid "47003280318" :requesting-eid "EE47003280318"}))
;; note - subject-eid without EE prefix, requesting-eid with EE prefix
