(ns teet.migration.file-part-statuses
  (:require [datomic.client.api :as d]
            [clojure.string :as string]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]))

(def user-info [:db/id :user/person-id :user/given-name :user/family-name])

(defn- get-task-meta
  "Get the transaction history for the item"
  [db entity-id]
  (->>
    ;; Pull all datoms affecting entity
    (d/q '[:find ?tx ?e ?a ?v ?add
           :where
           [?e ?a ?v ?tx ?add]
           :in $ ?e]
         (d/history db) entity-id)

    ;; Group by transaction
    (group-by first)

    (map
      (fn [[_ tx-changes]]
        (let [tx-id (ffirst tx-changes)]
          ;; Fetch all information added to tx and the user information
          ;; of the :tx/author (if any)
          {:tx (update (d/pull db '[*] tx-id)
                       :tx/author (fn [author]
                                    (when author
                                          (d/pull db user-info [:user/id author]))))

           ;; group all changes by attribute
           :changes (reduce (fn [m [_tx _e a v add]]
                              (let [attr (:db/ident (d/pull db [:db/ident] a))]
                                (update m attr (fnil conj [])
                                        [add v])))
                            {}
                            tx-changes)})))

    ;; Sort transactions by time
    (sort-by (comp :db/txInstant :tx)))
  )

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
                             task-meta (get-task-meta db (:db/id part-info))]
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
