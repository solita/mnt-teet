(ns teet.asset.asset-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [datomic.client.api :as d]
            [teet.environment :as environment]
            [teet.util.datomic :as du]
            [clojure.walk :as walk]))

(def ctype-pattern
  '[*
    {:ctype/_parent [*]}
    {:attribute/_ctype
     [*
      {:enum/_attribute [*]}]}])

(def type-library-pattern
  `[~'*
    {:fclass/_fgroup
     [~'* {:ctype/_parent ~ctype-pattern}]}])

(defn- ctype? [x]
  (and (map? x)
       (du/enum= (:asset-schema/type x) :asset-schema.type/ctype)))

(defn- child-ctypes [db ctype]
  (update ctype :ctype/_parent
          #(->> %
                (mapv
                 (fn [child]
                   (if (ctype? child)
                     (d/pull db ctype-pattern (:db/id child))
                     child))))))

(defn- asset-type-library [db]
  (walk/postwalk
   (fn [x]
     (if (ctype? x)
       (child-ctypes db x)
       x))

   (mapv first
         (d/q '[:find (pull ?fg p)
                :where [?fg :asset-schema/type :asset-schema.type/fgroup]
                :in $ p]
              db type-library-pattern))))

(defquery :asset/type-library
  {:doc "Query the asset types"
   :context _
   :unauthenticated? true
   :args _}
  (asset-type-library (environment/asset-db)))
