(ns teet.file.file-tx
  "File transactions"
  (:require [datomic.client.api :as d]
            [teet.file.file-db :as file-db]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]
            [datomic.ion :as ion]
            [teet.meta.meta-model :as meta-model]
            [teet.task.task-db :as task-db]
            [taoensso.timbre :as log]))

(defn ensure-unique-metadata
  "Check that metadata will remain unique after transacting the given
  speculative tx data. Cancels tx if uniqueness isn't maintained.

  Returns tx data."
  [db task-id speculative-tx-data]
  (let [{db :db-after} (d/with db {:tx-data speculative-tx-data})
        task-files (task-db/latest-task-files-with-incomplete db task-id)
        metadata (mapv (partial file-db/file-metadata-by-id db) task-files)]
    (when (not= (count metadata)
                (count (distinct metadata)))
      (ion/cancel {:cognitect.anomalies/category :cognitect.anomalies/conflict
                   :cognitect.anomalies/message "File metadata not unique"
                   :teet/error :file-metadata-not-unique}))
    speculative-tx-data))

(defn upload-file-to-task
  "Upload file to task. Takes tx data and resolves the part.
  Creates new file part if needed."
  [db user task-file-tx]
  (ensure-unique-metadata
   db (:db/id task-file-tx)
   (if-let [part (get-in task-file-tx [:task/files 0 :file/part])]
     ;; Contains a part reference, get or create the part
     (let [task-id (:db/id task-file-tx)
           part-number (:file.part/number part)
           part-status (:file.part/status part)
           part-id (when (and part-number (not= 0 part-number))
                     (ffirst
                      (d/q '[:find ?part
                             :where
                             [?part :file.part/task ?task]
                             [?part :file.part/number ?n]
                             [(missing? $ ?part :meta/deleted?)]
                             :in $ ?task ?n]
                           db task-id part-number)))]
       (if (zero? part-number)
         ;; Part is zero, that means general (no part)
         [(update-in task-file-tx [:task/files 0]
                     dissoc :file/part)]
         [(assoc-in task-file-tx [:task/files 0 :file/part]
                    (if part-id
                      ;; Existing part, just refer to it
                      (merge
                       {:db/id part-id}
                       (when (du/enum= part-status :file.part.status/not-started)
                         {:file.part/status :file.part.status/in-progress}))
                      ;; New part, create it
                      (merge {:file.part/name ""}
                             (cu/without-nils (select-keys part [:file.part/name :file.part/number]))
                             (meta-model/creation-meta user)
                             {:db/id "new-part"
                              :file.part/status :file.part.status/in-progress
                              :file.part/task task-id})))]))

     ;; No part, return tx data as is
     [task-file-tx])))

(defn create-task-file-part
  "Create a new file part for the given task."
  [db user task-id part-name]
  [(merge
     (meta-model/creation-meta user)
     {:db/id "new-part"
      :file.part/task task-id
      :file.part/name (or part-name "")
      :file.part/number (file-db/next-task-part-number db task-id)
      :file.part/status :file.part.status/not-started})])

(defn remove-task-file-part
  "Mark a task file part as deleted if it doesn't have any files"
  [db part-id user]
  (let [files (->> (d/q '[:find ?file
                          :in $ ?part
                          :where
                          [?file :file/part ?part]
                          ;; File is not deleted
                          [(missing? $ ?file :meta/deleted?)]

                          ;; File has not been replaced with newer version
                          (not-join [?file]
                                    [?replacement :file/previous-version ?file])]
                        db part-id)
                   (mapv first))]
    (if (seq files)
      (ion/cancel {:cognitect.anomalies/category :cognitect.anomalies/conflict
                   :cognitect.anomalies/message "The part being deleted contains files so it can't be removed"
                   :teet/error :part-has-files})
      [(meta-model/deletion-tx user part-id)])))

(def modify-file-keys [:db/id :file/name :file/sequence-number :file/document-group :file/part
                       :meta/modified-at :meta/modifier])

(defn modify-file
  "Modify file. Ensures that the same task doesn't have
  another file with the same metadata."
  [db {id :db/id :as file}]
  (let [task-id (get-in (du/entity db id) [:task/_files 0 :db/id])

        old-file (d/pull db modify-file-keys id)
        new-file (select-keys file modify-file-keys)]
    (ensure-unique-metadata
     db task-id
     (du/modify-entity-tx
      old-file
      (-> new-file
          ;; Take :db/id from map as the ref attribute value
          (update :file/part :db/id)
          cu/without-nils)))))
