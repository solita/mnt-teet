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
