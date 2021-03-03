(ns teet.enum.enum-queries
  "Fetch enumeration values"
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [clojure.spec.alpha :as s]
            [teet.environment :as environment]))


(s/def ::attribute keyword?)
(defquery :enum/values
  {:doc "Fetch values for given enumeration"
   :context {db :db}
   :args {:keys [attribute database]}
   :spec (s/keys :req-un [::attribute])

   :project-id nil
   :authorization {}}

  {:query '{:find [(pull ?e [*])]
            :in [$ ?attr]
            :where [[?e :enum/attribute ?attr]]}
   :args [(if (and (= :asset database)
                   (environment/feature-enabled? :asset-db))
            (environment/asset-db) db) attribute]
   :result-fn (partial mapv first)})
