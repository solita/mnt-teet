(ns teet.migration.file-part-statuses
  (:require [datomic.client.api :as d]
            [clojure.string :as string]
            [teet.admin.admin-queries :as admin-queries]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]))

(defn task-status-meta->file-part
  "Find task parts that are missing status values and use their tasks' status instead. Also copies the meta information from task."
  [conn]
  (let [db (d/db conn)           ;; For testing use: (d/as-of (d/db conn) #inst "2021-05-04T12:00:00.152-00:00")
   file-parts (d/q '[:find
                     (pull ?part [:db/id :file.part/name :file.part/status])
                     (pull ?task [:db/id :task/status :meta/creator])
                     :where
                     [(missing? $ ?part :file.part/status)]
                     [?part :file.part/task ?task]
                     [?part :file.part/name _]
                     :in $] db)]
    (d/transact conn
                {:tx-data
                 (vec
                   (for [f file-parts]
                     (let [part-info (first f)
                           task-info (second f)
                           task-meta (admin-queries/entity-tx-log db db (:db/id part-info))]
                       (merge {:db/id (:db/id part-info)
                               :file.part/status (-> task-info
                                                     :task/status
                                                     :db/ident
                                                     (string/replace ":task" "file.part")
                                                     keyword)}
                              (cu/without-nils {:meta/creator (->
                                                                (first task-meta)
                                                                (into {})
                                                                :tx
                                                                :tx/author
                                                                :db/id)
                                                :meta/created-at (->
                                                                   (first task-meta)
                                                                   (into {})
                                                                   :tx
                                                                   :db/txInstant)})
                              (if-not
                                (= (first task-meta) (last task-meta))
                                (cu/without-nils {:meta/modifier (->
                                                                   (last task-meta)
                                                                   (into {})
                                                                   :tx
                                                                   :tx/author
                                                                   :db/id)
                                                  :meta/modified-at (->
                                                                      (last task-meta)
                                                                      (into {})
                                                                      :tx
                                                                      :db/txInstant)}))))))})))
