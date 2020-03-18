(ns teet.integration.x-road
  (:require [clojure.xml :refer [parse]]
            [clojure.data.zip.xml :as z]
            [hiccup.core :as hiccup]
            [org.httpkit.client :as htclient]))

(defn request-hiccup [eid]
  [:soap:Envelope {:xmlns:soap "http://schemas.xmlsoap.org/soap/envelope/"
                   :xmlns:xrd "http://x-road.eu/xsd/xroad.xsd"
                   :xmlns:ns5 "http://rr.x-road.eu/producer"
                   :xmlns:id "http://x-road.eu/xsd/identifiers"}
   [:soap:Header
    [:xrd:userId "EE47003280318"]
    [:xrd:id "170a5cb119b70008440895042451"]
    [:xrd:protocolVersion "4.0"]
    [:xrd:client {:id:objectType "SUBSYSTEM"}
     [:id:xRoadInstance "ee-dev"]
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

(defn request-xml [params]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" (hiccup/html (make-request* params))))

(defn make-request [url eid]
  (let [req (request-xml eid)
        resp-atom (htclient/post url {:body req})
        resp (deref resp-atom)]
    (if (= 200 (:status resp))
      (println "got successful resp, body:" (:body resp)))))
