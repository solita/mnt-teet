(ns teet.integration.x-road.core
  "Common core functionality for X-road integrations"
  (:require [hiccup.core :as hiccup]
            [clojure.xml :as xml]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.zip :as zip]
            [teet.log :as log]
            [org.httpkit.client :as client])
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
           [:id:serviceCode (:service-code service)]
           (when-let [v (:version service)]
             [:id:serviceVersion v])]]
   [:soap:Body body]])

(defn request-xml
  "Render request hiccup into XML string"
  [req-hic]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
       (hiccup/html req-hic)))

(defn unexceptional-xml-parse [input]
  (try
    (xml/parse input)
    (catch Exception _
      ;; caller has to log error about input resulting in nil parse
      nil)))

(defn unpeel-multipart [ht-resp]
  ;; Kludge to decode multipart/related, as this is not supported by the HTTP client lib
  (let [c-type (:content-type (:headers ht-resp))
        multipart-match (re-find #"multipart/related;.*boundary=\"([^\"]+)\"" c-type)
        body (slurp (:body ht-resp))
        xml-match (re-find #"(?is).*(<\?xml.*Envelope>)" body)
        soap-msg (second xml-match)
        boundary (second multipart-match)]
    (if (and boundary soap-msg (str/includes? soap-msg boundary))
      (throw (ex-info "received more than 1 part in multipart response, not implemented"
                      {}))
      (if-not multipart-match
        body
        soap-msg))))

(defn string->zipped-xml [xml-string]
  (with-open [in (io/input-stream (.getBytes xml-string "UTF-8"))]
    (zip/xml-zip (xml/parse in))))

(defn zipped-xml->string [zipped-xml]
  (with-out-str
    (xml/emit-element (zip/node zipped-xml))))

(defn parse-response
  "Parse XML in HTTP response and return XML zipper.
  If response status is not 200 OK, throws exception."
  [{:keys [status error] :as http-response}]
  (if (= 200 status)
    (string->zipped-xml (unpeel-multipart http-response))
    (do
      (log/error "HTTP error communicating with X-road, error:" error ", status:" status)
      (throw (ex-info "SOAP response returned non OK status."
                      {:error :invalid-x-road-response
                       :status status
                       :http-error error
                       :response http-response})))))

(defn perform-request [url request-xml]
  (-> url
      (client/post {:body request-xml
                    :as :stream
                    :headers {"Content-Type" "text/xml; charset=UTF-8"}})
      deref
      parse-response))
