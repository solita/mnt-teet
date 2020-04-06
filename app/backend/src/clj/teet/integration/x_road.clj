(ns teet.integration.x-road
  (:require [clojure.xml :as xml]
            [clojure.data.zip.xml :as z]
            [clojure.zip]
            [hiccup.core :as hiccup]
            [org.httpkit.client :as htclient]
            [taoensso.timbre :as log]
            ;; [clj-http.client :as client]
            ;; [clj-soap.client :as soap]
            ))

;; see also https://x-tee.ee/catalogue-data/ee-dev/ee-dev/GOV/70008440/rr/155.wsdl

(defn rr442-request-hiccup [eid instance-id]
  [:soap:Envelope {:xmlns:soap "http://schemas.xmlsoap.org/soap/envelope/"
                   :xmlns:xrd "http://x-road.eu/xsd/xroad.xsd"
                   :xmlns:ns5 "http://rr.x-road.eu/producer"
                   :xmlns:id "http://x-road.eu/xsd/identifiers"}
   [:soap:Header
    [:xrd:userId "EE47003280318"]
    [:xrd:id "170a5cb119b70008440895042451"]
    [:xrd:protocolVersion "4.0"]
    [:xrd:client {:id:objectType "SUBSYSTEM"}
     [:id:xRoadInstance instance-id]
     [:id:memberClass "GOV"]
     [:id:memberCode "70001490"]
     [:id:subsystemCode "liiklusregister"]]
    [:xrd:service {:id:objectType "SERVICE"}
     [:id:xRoadInstance "ee-dev"]
     [:id:memberClass "GOV"]
     [:id:memberCode "70008440"]
     [:id:subsystemCode "rr"]
     [:id:serviceCode "RR442"]
     [:id:serviceVersion "v3"]]]
   [:soap:Body
    [:ns5:RR442
     [:request
      [:Isikukood eid]]]]])

(defn rr442-request-xml [eid instance-id]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" (hiccup/html (rr442-request-hiccup eid instance-id))))

(defn rr442-parse-name [xml-string]
  (let [xml (xml/parse xml-string)
        _ (assert (some? xml))
        _ (def *xx xml)
        zipped-xml (clojure.zip/xml-zip xml)
        avaldaja (z/xml1-> zipped-xml :SOAP-ENV:Envelope :SOAP-ENV:Body :prod:RR442Response :response :Avaldaja)
        fields [:Eesnimi :Perenimi :Isikukood]
        ;; _ (def *x avaldaja)
        fieldname->kvpair (fn [fieldname]
                            [fieldname (z/xml1-> avaldaja fieldname z/text)])]
    (into {} (mapv fieldname->kvpair fields))))

(defn perform-rr442-request [url instance-id eid]
  (let [req (rr442-request-xml eid instance-id)

        resp-atom (htclient/post url {:body req
                                      :as :stream
                                      :headers {"Content-Type" "text/xml; charset=UTF-8"}})
        resp (deref resp-atom)]
    (if (= 200 (:status resp))
      {:status :ok
       :result (rr442-parse-name (:body resp))}
      ;; else
      {:status :error
       :result resp})))

(defn kr-kinnistu-d-request-xml [registriosa-nr instance-id]
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
          [:xrd:userId "EE47003280318"]
          [:xrd:id "170a5cb119b70008440895042451"]
          [:xrd:protocolVersion "4.0"]
          [:xrd:client {:id:objectType "SUBSYSTEM"}
           [:id:xRoadInstance instance-id]
           [:id:memberClass "GOV"]
           [:id:memberCode "70001490"]
           [:id:subsystemCode "generic-consumer"]]
          [:xrd:service {:id:objectType "SERVICE"}
           [:id:xRoadInstance instance-id]
           [:id:memberClass "GOV"]
           [:id:memberCode "70000310"]
           [:id:subsystemCode "kr"]
           [:id:serviceCode "Kinnistu_Detailandmed"]
           ;; [:id:serviceVersion ""] ;; was omitted in example SOAP?
           ]]
         [:soap:Body
          [:kr:Kinnistu_Detailandmed
           [:kr:request
            [:kin:jao_nr "0,1,2,3,4"]
            [:kin:kande_kehtivus "1"]
            [:kin:kasutajanimi]
            [:kin:parool]
            [:kin:registriosa_nr registriosa-nr]
            ]]]]]    
    (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" (hiccup/html req-hic))))


(defn d-cadastral-unit* [k-xml]
  {:ads_oid (z/xml1-> k-xml :a:KinnistuKatastriyksus :a:aadressobjekt :a:ads_oid z/text)
   :katastritunnus (z/xml1-> k-xml :a:KinnistuKatastriyksus :a:katastritunnus z/text)
   :katastriyksuse_aadress (z/xml1-> k-xml :a:KinnistuKatastriyksus :a:katastriyksuse_aadress z/text)
   :pindala (z/xml1-> k-xml :a:KinnistuKatastriyksus :a:pindala z/text)
   :pindala_yhik (z/xml1-> k-xml :a:KinnistuKatastriyksus :a:pindala_yhik z/text)

   })

(defn d-cadastral-units [kdr-xml]
  ;;; kdr = Kinnistu_DetailamdResponse from inside the soap body
  (let [k-data-seq (z/xml-> kdr-xml :jagu1 :a:Jagu1 :a:katastriyksused)]
    {:katastriyksus (mapv d-cadastral-unit* k-data-seq)}))

(defn d-property-owners [kdr-xml]
  ;;; kdr = Kinnistu_DetailamdResponse from inside the soap body
  (let [owner-xml-seq (z/xml-> kdr-xml :jagu2 :a:Jagu2 :a:omandiosad :a:Jagu2.Omandiosa)
        _ (def *ox owner-xml-seq)
        oo-get (fn oo-get [ox & path]
                  (apply z/xml1-> (concat [ox] path)))
        isikud-get (fn [ox & path] (apply oo-get ox (concat [:a:isikud] path [z/text])))
        omandi-get (fn [ox & path] (apply oo-get ox (concat path [z/text])))
        ;; j2-isikud-get (fn [& path] (apply j2-get (concat [:a:omandiosad :a:Jagu2.Omandiosa :a:isikud] path)))
        ]
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
           owner-xml-seq)}) 
  
  )


(defn kinnistu-d-parse-response [xml-string]
  (let [xml (xml/parse (clojure.java.io/input-stream (.getBytes xml-string)))
        zipped-xml (clojure.zip/xml-zip xml)
        d-response (z/xml1-> zipped-xml :s:Envelope :s:Body :Kinnistu_DetailandmedResponse)
        _ (def *x d-response)
        ]
    (merge {}
           (d-cadastral-units d-response)
           (d-property-owners d-response))))

(defn unpeel-multipart [ht-resp]
  (let [c-type (:content-type (:headers ht-resp))
        multipart-match (re-find #"multipart/related;.*boundary=\"([^\"]+)\"" c-type)
        body (slurp (:body ht-resp))
        xml-match (re-find #"(?is).*(<\?xml.*Envelope>)" body)]
    (if (> (count xml-match) 2)
      (log/error "received more than 1 part in multipart response, not implemented")
      (if-not multipart-match
        body
        (get xml-match 1)))))

#_(defn unpeel-multipart [ht-resp]
  (let [c-type (:content-type (:headers ht-resp))
        rx-match (re-find #"multipart/related;.*boundary=\"([^\"]+)\"" c-type)]
    (if-not rx-match
      :kek ;; ht-resp
      ;; else
      (let [boundary (str "--" (get rx-match 1))
            b-len (count boundary)
            body (:body ht-resp)
            
            i1 (clojure.string/index-of body boundary)
            i2 (when (integer? i1)
                 (clojure.string/index-of body boundary (+ i1 (count boundary))))
            ;; _ (println i1 i2)
            part1-header+body (subs body i1 i2)
            p1-body-offset (clojure.string/index-of part1-header+body "\n\n")
            ]
        (subs body i1 i2))
      )))

(defn perform-kinnistu-d-request [url instance-id reg-nr]
  (let [req (kr-kinnistu-d-request-xml reg-nr instance-id)
        resp-atom (htclient/post url {:body req
                                      :as :stream
                                      :headers {"Content-Type" "text/xml; charset=UTF-8"}})
        resp (deref resp-atom)]
    ;; (println "req was" req)
    (def *rq req)
    (def *rr resp)
    (if (= 200 (:status resp))
      {:status :ok
       :result (kinnistu-d-parse-response (unpeel-multipart resp))
       }
      ;; else
      {:status :error
       :result resp})))

;; repl

;; test land reg ids: 233102, 284502, 308104 (last one has empty jag3/jag4)
;; (def *r  (perform-kinnistu-d-request "http://localhost:12073" "ee-dev" "308104"))
;; (kinnistu-d-parse-response (unpeel-multipart *rr))

;; rr testing: (def *r2 (perform-rr442-request "http://localhost:12073" "ee-dev" "47003280318"))
