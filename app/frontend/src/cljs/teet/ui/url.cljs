(ns teet.ui.url
  "Define functions to generate URLs for any route"
  (:require [clojure.string :as str])
  (:require-macros [teet.route-macros :refer [define-url-functions]]))

(define-url-functions)

(defn with-params [url & param-names-and-values]
  (str url "?" (str/join "&"
                         (map (fn [[param-name param-value]]
                                (str (name param-name) "=" (js/encodeURIComponent (str param-value))))
                              (partition 2 param-names-and-values)))))

(def path-and-params-pattern
  "Regex pattern to split URL hash into path and parameters."
  #"([^\?]+)(\?.+)?$")


(def param-name-and-value-pattern
  "Regex pattern to extract parameter names and values."
  #"([\w-]+)=([^&]*)")

(defn- parse-hash [hash]
  (let [[_ path params-part] (re-matches path-and-params-pattern hash)
        params (when params-part
                 (re-seq param-name-and-value-pattern params-part))]
    {:path path
     :params (into {}
                   (map (fn [[_ param-name param-value]]
                          [(keyword param-name) param-value]))
                   params)}))
