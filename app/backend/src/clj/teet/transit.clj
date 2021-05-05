(ns teet.transit
  "Transit format utilities"
  (:require [cognitect.transit :as t]
            [teet.util.collection :as cu]
            [ring.util.io :as ring-io])
  (:import (com.cognitect.transit WriteHandler)))

(defn transit->clj
  "Parse transit+json `in` to Clojure data."
  [in]
  (with-open [in (if (string? in)
                   (java.io.ByteArrayInputStream. (.getBytes in "UTF-8"))
                   in)]
    (t/read (t/reader in :json))))

(defn stringify-handler [format-fn]
  (t/write-handler (constantly "s") format-fn))

(defn write-options [type->handler]
  {:handlers
   (cu/map-vals
    (fn [handler]
      (cond
        (instance? WriteHandler handler) handler
        (fn? handler) (stringify-handler handler)
        :else (throw (ex-info "Expected write handler to be instance of WriteHandler or a formatting function."
                              {:handler handler}))))
    type->handler)})

(defn write-transit [out data]
  (let [opts (some-> data meta :transit write-options)]
    (t/write (t/writer out :json opts) data)))

(defn clj->transit
  "Convert given Clojure `data` to transit+json string."
  [data]
  (with-open [out (java.io.ByteArrayOutputStream.)]
    (write-transit out data)
    (.toString out "UTF-8")))

(defn transit-response [data]
  (let [last-modified (some-> data meta :last-modified)
        body (if last-modified
               ;; We have last-modified, data :body is a delay
               (ring-io/piped-input-stream
                (fn [out]
                  (write-transit out @(:body data))))
               (clj->transit data))]
    {:status 200
     :headers (merge {"Content-Type" "application/json+transit"}
                     (when last-modified
                       {"Last-Modified" last-modified}))
     :body body}))

(defn transit-request [{:keys [body params request-method] :as _req}]
  (case request-method
    :get (transit->clj (get params "q"))
    :post (transit->clj body)))

(defn with-write-options [type->handler data]
  (vary-meta data update :transit merge type->handler))
