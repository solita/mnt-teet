(ns teet.file.file-tx
  "File transactions"
  (:require [datomic.client.api :as d]
            [teet.file.file-db :as file-db]))

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
    :file.part/name part-name
    :file.part/number (file-db/next-task-part-number db task-id)}])
