(ns teet.integration.x-road.population-registry
  "Population registry.
  See also https://x-tee.ee/catalogue-data/ee-dev/ee-dev/GOV/70008440/rr/155.wsdl"
  (:require [teet.integration.x-road.core :as x-road]
            [clojure.data.zip.xml :as z]
            [teet.log :as log]))

(defn rr442-request-hiccup [{:keys [instance-id requesting-eid subject-eid]}]
  (x-road/request-envelope
   {:instance-id instance-id
    :requesting-eid requesting-eid
    :client {:member-code "70001490"
             :subsystem-code "liiklusregister"}
    :service {:member-code "70008440"
              :subsystem-code "rr"
              :service-code "RR442"}}
   [:ns5:RR442 {:xmlns:ns5 "http://rr.x-road.eu/producer"}
    [:request
     [:Isikukood subject-eid]]]))

(defn rr442-parse-name [zipped-xml]
  (let [fault (or
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
  (->> params
       (rr442-request-hiccup params)
       x-road/request-xml
       (x-road/perform-request url)
       rr442-parse-name))
