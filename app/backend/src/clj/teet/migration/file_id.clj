(ns teet.migration.file-id
  "Generate UUIDs for all latest versions of task files."
  (:require [datomic.client.api :as d]
            [teet.file.file-db :as file-db]
            [teet.log :as log]))

(defn migrate [conn]
  (let [db (d/db conn)
        task-files (d/q '[:find ?f
                          :where
                          [?t :task/files ?f]]
                        db)
        latest-versions
        (into #{}
              (map (comp (partial file-db/latest-version db) first))
              task-files)]
    (log/info "Creating new :file/id UUID for " (count latest-versions) "latest file versions")
    (d/transact
     conn
     {:tx-data (vec
                (for [id latest-versions]
                  {:db/id id
                   :file/id (java.util.UUID/randomUUID)}))})))
