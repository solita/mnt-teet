(ns teet.enum.enum-queries
  "Fetch enumeration values"
  (:require [teet.db-api.core :as db-api :refer [defquery]]))


(defquery :enum/values
  {:doc "Fetch values for given enumeration"
   :context {db :db}
   :args {:keys [attribute]}
   :pre [(keyword? attribute)]
   :project-id nil
   :authorization {}}
  {:query '{:find [(pull ?e [*])]
            :in [$ ?attr]
            :where [[?e :enum/attribute ?attr]]}
   :args [db attribute]
   :result-fn (partial mapv first)})
