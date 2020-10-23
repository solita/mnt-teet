(ns teet.file.file-tx
  "File transactions"
  (:require [datomic.client.api :as d]
            [teet.file.file-db :as file-db]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]
            [datomic.ion :as ion]
            [teet.meta.meta-model :as meta-model]))

(defn upload-file-to-task
  "Upload file to task. Takes tx data and resolves the part.
  Creates new file part if needed."
  [db task-file-tx]
  (if-let [part (get-in task-file-tx [:task/files 0 :file/part])]
    ;; Contains a part reference, get or create the part
    (let [task-id (:db/id task-file-tx)
          part-number (:file.part/number part)
          part-id (ffirst
                   (d/q '[:find ?part
                          :where
                          [?part :file.part/task ?task]
                          [?part :file.part/number ?n]
                          :in $ ?task ?n]
                        db task-id part-number))]
      [(assoc-in task-file-tx [:task/files 0 :file/part]
                 (if part-id
                   ;; Existing part, just refer to it
                   {:db/id part-id}
                   ;; New part, create it
                   (merge (select-keys part [:file.part/name :file.part/number])
                          {:db/id "new-part"
                           :file.part/task task-id})))])

    ;; No part, return tx data as is
    [task-file-tx]))

(defn create-task-file-part
  "Create a new file part for the given task."
  [db task-id part-name]
  [{:db/id "new-part"
    :file.part/task task-id
    :file.part/name (or part-name "")
    :file.part/number (file-db/next-task-part-number db task-id)}])

(defn remove-task-file-part
  "Mark a task file part as deleted if it doesn't have any files"
  [db part-id user]
  (let [files (->> (d/q '[:find ?file
                          :in $ ?part
                          :where
                          [?file :file/part ?part]
                          [(missing? $ ?file :meta/deleted?)]]
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
  "Modify file."
  [db {id :db/id :as file}]
  (let [old-file (d/pull db modify-file-keys id)
        new-file (-> file
                     (select-keys modify-file-keys)
                     (update :file/part :db/id)
                     cu/without-nils)]
    (du/modify-entity-tx old-file new-file)))
