(ns teet.asset.asset-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [datomic.client.api :as d]
            [teet.environment :as environment]
            [teet.util.datomic :as du]
            [clojure.walk :as walk]
            [teet.project.project-db :as project-db]))

(def ctype-pattern
  '[*
    {:ctype/_parent [*]}
    {:attribute/_parent
     [*
      {:enum/_attribute [*]}]}])

(def type-library-pattern
  `[~'*
    {:fclass/_fgroup
     [~'*
      {:attribute/_parent [~'* {:enum/_attribute [~'*]}]}
      {:ctype/_parent ~ctype-pattern}]}])

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

(defquery :asset/project-cost-items
  {:doc "Query project cost items"
   :context {:keys [db user]}
   :args {project-id :thk.project/id}
   :project-id [:thk.project/id project-id]
   ;; fixme: cost items authz
   :authorization {:project/read-info {}}}
  {:asset-type-library (asset-type-library (environment/asset-db))
   :fgroups []
   :project (project-db/project-by-id db [:thk.project/id project-id])})
