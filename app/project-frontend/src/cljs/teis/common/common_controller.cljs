(ns teis.common.common-controller
  "Common UI controller code"
  (:require [reagent.core :as r]
            [tuck.core :as t]
            [teis.routes :as routes]
            [clojure.string :as str]))


(defn query-param-atom [{:keys [page params query] :as app} param-name read-param write-param]
  (r/wrap (read-param (get query param-name))
          #(routes/navigate! page params (write-param query %))))

(defn query-param-boolean-atom [app param-name]
  (query-param-atom app param-name
                    #(= "1" %)
                    (fn [query value]
                      (assoc query param-name (if value "1" "0")))))

#_(defn query-param-ids-atom
  "Returns atom that reads/writes list of ids to the given query parameter"
  [app param-name]
  (query-param-atom app param-name
                    #(when %
                       (into []
                             (map js/parseInt)
                             (str/split #"," %)))
                    #(str/join "," %)))
