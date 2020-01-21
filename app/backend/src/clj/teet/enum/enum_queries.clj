(ns teet.enum.enum-queries
  "Fetch enumeration values"
  (:require [teet.db-api.core :as db-api]))


(defmethod db-api/query :enum/values [{db :db} {:keys [attribute]}]
  {:query '{:find [(pull ?e [*])]
            :in [$ ?attr]
            :where [[?e :enum/attribute ?attr]]}
   :args [db attribute]
   :result-fn (partial mapv first)})
