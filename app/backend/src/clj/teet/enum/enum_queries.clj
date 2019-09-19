(ns teet.enum.enum-queries
  "Fetch enumeration values"
  (:require [teet.db-api.core :as db-api]))


(defmethod db-api/query :enum/values [{db :db} {:keys [attribute]}]
  {:query '{:find [?kw]
            :in [$ ?attr]
            :where [[?e :enum/attribute ?attr]
                    [?e :db/ident ?kw]]}
   :args [db attribute]
   :result-fn #(into #{} (map first) %)})
