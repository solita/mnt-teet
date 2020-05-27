(ns teet.integration.x-road.core
  "Common core functionality for X-road integrations"
  (:require [hiccup.core :as hiccup])
  (:import (java.util UUID)))

(defn- valid-header-system? [system]
  (and (map? system)
       (contains? system :member-code)
       (contains? system :subsystem-code)))

(defn request-envelope
  "Return a SOAP Envelope with header and body."
  [{:keys [instance-id requesting-eid
           client service]}
   body]
  {:pre [(valid-header-system? client)
         (valid-header-system? service)
         (contains? service :service-code)]}
  [:soap:Envelope {:xmlns:soap "http://schemas.xmlsoap.org/soap/envelope/"
                   :xmlns:xrd "http://x-road.eu/xsd/xroad.xsd"
                   :xmlns:id "http://x-road.eu/xsd/identifiers"}
         [:soap:Header
          [:xrd:userId requesting-eid]
          [:xrd:id (UUID/randomUUID)]
          [:xrd:protocolVersion "4.0"]
          [:xrd:client {:id:objectType "SUBSYSTEM"}
           [:id:xRoadInstance instance-id]
           [:id:memberClass "GOV"]
           [:id:memberCode (:member-code client)]
           [:id:subsystemCode (:subsystem-code client)]]
          [:xrd:service {:id:objectType "SERVICE"}
           [:id:xRoadInstance instance-id]
           [:id:memberClass "GOV"]
           [:id:memberCode (:member-code service)]
           [:id:subsystemCode (:subsystem-code service)]
           [:id:serviceCode (:service-code service)]]]
   [:soap:Body body]])

(defn request-xml
  "Render request hiccup into XML string"
  [req-hic]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
       (hiccup/html req-hic)))
