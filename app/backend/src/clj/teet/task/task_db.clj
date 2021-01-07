(ns teet.task.task-db
  "Task related queries"
  (:require [datomic.client.api :as d]
            [teet.util.datomic :as du]
            [teet.file.file-db :as file-db]
            [teet.activity.activity-db :as activity-db]
            [teet.meta.meta-query :as meta-query]))

(defn activity-for-task-id
  [db task-id]
  (let [task (du/entity db task-id)]
    (get-in task [:activity/_tasks 0 :db/id])))

(defn task-file-parts
  [db task-id]
  (update
    (meta-query/without-deleted db (d/pull db '[{:file.part/_task [:file.part/name :db/id :file.part/number :meta/deleted?]}] task-id))
    :file.part/_task
    #(sort-by :file.part/number %)))

(defn task-file-listing
  "Returns files for a given task. Returns latest versions of files as vector
  and adds all previous versions of a files as :versions key."
  [db user task-eid]
  (file-db/file-listing db user (mapv first
                                      (d/q '[:find ?f
                                             :where [?t :task/files ?f]
                                             :in $ ?t]
                                           db task-eid))
    false))



(defn valid-task-for-activity? [db activity-id {task-type :task/type :as _task}]
  (let [task-groups (activity-db/allowed-task-groups db activity-id)]
    (boolean (task-groups
              (:enum/valid-for (du/entity db [:db/ident task-type]))))))

(defn task-type-can-be-sent-to-thk? [db task-type]
  (ffirst (d/q '[:find ?thk-type
                 :where [?t :thk/task-type ?thk-type]
                 :in $ ?t]
               db task-type)))

(defn task-types-can-be-sent-to-thk? [db task-types]
  (= (count (d/q '[:find ?thk-type
                   :where [?t :thk/task-type ?thk-type]
                   :in $ [?t ...]]
                 db task-types))
     (count task-types)))

(defn send-to-thk? [db task-id]
  (:task/send-to-thk? (d/pull db [:task/send-to-thk?] task-id)))

(defn task-has-files?
  "Check if task currently has files. Doesn't include deleted files."
  [db task-id]
  (boolean
   (seq
    (d/q '[:find ?f
           :where
           [?t :task/files ?f]
           [(missing? $ ?f :meta/deleted?)]
           (not-join [?f]
                     [?replacement :file/previous-version ?f])
           :in $ ?t]
         db task-id))))
