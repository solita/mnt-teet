(ns teet.transit
  "Transit format utilities"
  (:require [cognitect.transit :as t]
            [taoensso.timbre :as log]))

(defn transit->clj
  "Parse transit+json `in` to Clojure data."
  [in]
  (with-open [in (if (string? in)
                   (java.io.ByteArrayInputStream. (.getBytes in "UTF-8"))
                   in)]
    (t/read (t/reader in :json))))

(defn write-transit [out data]
  (t/write (t/writer out :json) data))

(defn clj->transit
  "Convert given Clojure `data` to transit+json string."
  [data]
  (with-open [out (java.io.ByteArrayOutputStream.)]
    (write-transit out data)
    (.toString out "UTF-8")))

(defn transit-response [data]
  {:status 200
   :headers {"Content-Type" "application/json+transit"}
   :body (clj->transit data)})

(defn transit-request [{:keys [body params request-method] :as req}]
  (log/info "TRANSIT-REQUEST: " req)
  (case request-method
    :get (transit->clj (get params "q"))
    :post (transit->clj body)))
