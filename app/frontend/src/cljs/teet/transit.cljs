(ns teet.transit
  (:require [cognitect.transit :as t]))

(defn clj->transit
  "Convert given Clojure `data` to transit+json string."
  [data]
  (t/write (t/writer :json) data))

(defn transit->clj
  "Parse transit+json `in` to Clojure data."
  [in]
  (t/read (t/reader :json) in))
