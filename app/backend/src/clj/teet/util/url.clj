(ns teet.util.url
  (:require [teet.environment :as environment]
            [teet.transit :as transit]
            [clojure.string :as str])
  (:import (java.net URLEncoder)))

(defn js-style-url-encode
  "Fixes the differences between js/encodeURIComponent and URLEncoder/encode, so the url is usable in the browser"
  [url]
  (-> url
      (URLEncoder/encode "UTF-8")
      (str/replace "%7E" "~")
      (str/replace "+" "%20")
      (str/replace "%27" "'")
      (str/replace "%28" "(")
      (str/replace "%29" ")")
      (str/replace "%21" "!")))


(defn- check-query-and-args [query args]
  (assert (keyword? query)
          "Must specify :query keyword that names the query to run")
  (assert (some? args) "Must specify :args for query"))

(defn query-url
  "Generate a full url for a specific query."
  [query args]
  (check-query-and-args query args)
  (str (environment/config-value :base-url)
       "/query/"
       "?q=" (-> (transit/clj->transit {:query query :args args})
                 js-style-url-encode)))
