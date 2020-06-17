(ns teet.task.task-db
  "Task related queries"
  (:require [datomic.client.api :as d]
            [teet.activity.activity-model :as activity-model]
            [teet.util.datomic :as du]
            [teet.file.file-db :as file-db]))

(defn activity-for-task-id
  [db task-id]
  (let [task (du/entity db task-id)]
    (get-in task [:activity/_tasks 0 :db/id])))



(defn task-file-listing
  "Returns files for a given task. Returns latest versions of files as vector
  and adds all previous versions of a files as :versions key."
  [db task-eid]
  (file-db/file-listing db (mapv first
                                 (d/q '[:find ?f
                                        :where [?t :task/files ?f]
                                        :in $ ?t]
                                      db task-eid))))

(defn valid-task-for-activity? [db activity-id {task-type :task/type :as _task}]
  (boolean ((get activity-model/activity-name->task-groups
                 (-> (du/entity db activity-id)
                     :activity/name :db/ident)
                 #{})
            (:enum/valid-for (du/entity db [:db/ident task-type])))))

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
