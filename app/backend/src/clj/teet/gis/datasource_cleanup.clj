(ns teet.gis.datasource-cleanup
  "Cleanup datasource features that are marked deleted and are not referenced
  from any project."
  (:require [datomic.client.api :as d]
            [teet.environment :as environment]
            [teet.integration.postgrest :as postgrest]
            [clojure.set :as set]
            [teet.log :as log]))

(def ^:const feature-reference-attributes
  #{:thk.project/related-restrictions
    :thk.project/related-cadastral-units})

(defn- fetch-all-referenced-features
  "Returns all features referenced in datomic as mapping from
  datasource id to set of feature ids in that datasource."
  [db]
  (loop [ids-by-ds {}
         [^String feature & features] (mapcat
                                       #(map :v (d/datoms db {:index :avet :components [%]}))
                                       feature-reference-attributes)]
    (if-not feature
      ids-by-ds
      (let [split-pos (.indexOf feature ":")
            datasource-id (Long/parseLong (subs feature 0 split-pos))
            feature-id (subs feature (inc split-pos))]
        (recur (update ids-by-ds datasource-id (fnil conj #{}) feature-id)
               features)))))

(defn- fetch-deleted-features-for-ds
  "Returns set of deleted feature ids for the given datasource."
  [ctx datasource-id ]
  (into #{} (map :id)
        (postgrest/select ctx :feature #{:id} {:datasource_id datasource-id
                                               :deleted true})))

(defn- delete-unreferenced-features [ctx datasource-id referenced-features]
  (let [all-deleted-ids (fetch-deleted-features-for-ds ctx datasource-id)
        features-to-delete (set/difference all-deleted-ids referenced-features)]
    (log/info "Datasource" datasource-id "all deleted" (count all-deleted-ids) "referenced" (count referenced-features)
              "=>" (count features-to-delete) "to delete")
    (when (seq features-to-delete)
      (doseq [chunk (partition-all 50 features-to-delete)]
        (postgrest/delete! ctx :feature {:datasource_id datasource-id
                                         :id [:in chunk]})))))

(defn- cleanup-datasources [ctx db]
  (doseq [[datasource-id referenced-feature-ids]
          (fetch-all-referenced-features db)]
    (delete-unreferenced-features ctx datasource-id referenced-feature-ids)))

(defn cleanup-datasources-ion [_event]
  (try
    (cleanup-datasources (environment/api-context)
                         (d/db (environment/datomic-connection)))
    "{\"success\": true}"
    (catch Exception e
      (log/error e "Exception in datasource cleanup"))))
