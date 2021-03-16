(ns teet.asset.asset-commands
  "Commands to store asset information"
  (:require [teet.db-api.core :refer [defcommand] :as db-api]
            [datomic.client.api :as d]
            [teet.environment :as environment]
            [clojure.walk :as walk]
            [teet.asset.asset-db :as asset-db]
            [teet.asset.asset-type-library :as asset-type-library]))


(defn- prepare-asset-form-data
  "Prepare data from asset form to be saved in the database"
  [adb form-data]
  (def *adb adb)
  (let [rotl (asset-type-library/rotl-map
              (asset-db/asset-type-library adb))]

    (walk/prewalk
     (fn [x]
       (if-let [attr (and (map-entry? x)
                          (get rotl (first x)))]
         (case (get-in attr [:db/valueType :db/ident])
           :db.type/bigdec
           (update x 1 bigdec)

           :db.type/long
           (update x 1 #(Long/parseLong %))

           ;; No parsing
           x)
         x))
     form-data)))

(defcommand :asset/save-cost-item
  {:doc "Create/update an asset cost item"
   :context {:keys [user db]
             adb :asset-db
             aconn :asset-conn}
   :payload {project-id :project-id asset :asset}
   :project-id [:thk.project/id project-id]
   :authorization {:cost-items/edit-cost-items {}}}
  (let [asset (merge {:asset/project project-id}
                     (prepare-asset-form-data adb asset))]
    (def *asset asset)
    (:tempids
     (d/transact aconn
                 {:tx-data [asset]}))))
