(ns teet.task.task-db
  "Task related queries"
  (:require [datomic.client.api :as d]
            [teet.util.datomic :as du]
            [teet.file.file-db :as file-db]
            [teet.activity.activity-db :as activity-db]
            [teet.meta.meta-query :as meta-query]
            [teet.activity.activity-model :as activity-model]
            [taoensso.timbre :as log]
            [teet.user.user-model :as user-model]
            [teet.project.task-model :as task-model])
  (:import (java.util Date)))

(defn activity-for-task-id
  [db task-id]
  (let [task (du/entity db task-id)]
    (get-in task [:activity/_tasks 0 :db/id])))

(defn task-part-completion-attributes
  [user]
  {:file.part/completed-by (user-model/user-ref user)
   :file.part/completed-at (Date.)})

(defn task-completion-attributes
  [user]
  {:task/completed-by (user-model/user-ref user)
   :task/completed-at (Date.)})

(defn get-task-assignee-by-id
  [db task-id]
  (get-in (d/pull db [:task/assignee] task-id) [:task/assignee :db/id]))

(defn task-file-parts
  [db task-id]
  (update
    (meta-query/without-deleted db
                                (d/pull db '[{:file.part/_task [:file.part/name
                                                                :db/id
                                                                :file.part/number
                                                                :file.part/status
                                                                :file.part/completed-at
                                                                :meta/deleted?]}] task-id))
    :file.part/_task
    #(sort-by :file.part/number %)))

(defn not-reviewed-file-parts
  [db task-eid]
  (d/q '[:find ?p
         :where
         [?p :file.part/task ?t]
         [(missing? $ ?p :file.part/completed-at)]
         [(missing? $ ?p :meta/deleted?)]
         :in $ ?t]
       db task-eid))

(defn not-reviewed-task-files
  "Returns non-final version of files for a given task. Returns latest versions of files as vector
  and adds all previous versions of a files as :versions key."
  [db user task-eid]
  (file-db/file-listing db user (mapv first
                                      (d/q '[:find ?f
                                             :where
                                             [?t :task/files ?f]
                                             [?f :file/upload-complete? true]
                                             (or-join [?f]
                                                      [(missing? $ ?f :file/part)]
                                                      (and
                                                        [?f :file/part ?p]
                                                        [(missing? $ ?p :file.part/completed-at)]))
                                             :in $ ?t]
                                           db task-eid))))

(defn task-file-listing
  "Returns files for a given task. Returns latest versions of files as vector
  and adds all previous versions of a files as :versions key."
  [db user task-eid]
  (file-db/file-listing db user (mapv first
                                      (d/q '[:find ?f
                                             :where
                                             [?t :task/files ?f]
                                             [?f :file/upload-complete? true]
                                             :in $ ?t]
                                           db task-eid))))

(defn task-part-file-listing
  "Returns files for a given task part. Returns latest versions of files as vector
  and adds all previous versions of a files as :versions key."
  [db user part-eid]
  (file-db/file-listing db user (mapv first
                                      (d/q '[:find ?f
                                             :where
                                             [?f :file/part ?t]
                                             [?f :file/upload-complete? true]
                                             :in $ ?t]
                                           db part-eid))))


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
  (= (count (d/q '[:find ?t ?thk-type
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
           [?f :file/upload-complete? true]
           [(missing? $ ?f :meta/deleted?)]
           (not-join [?f]
                     [?replacement :file/previous-version ?f])
           :in $ ?t]
         db task-id))))

(defn latest-task-files-with-incomplete
  "Return latest versions of all files in task."
  [db task-id]
  (mapv first
        (d/q
          '[:find ?f
            :where
            [?t :task/files ?f]
            [(missing? $ ?f :meta/deleted?)]
            (not-join [?f]
                      [?replacement :file/previous-version ?f])
            :in $ ?t]
          db task-id)))

(defn latest-task-files
  "Return latest versions of all files in task that are marked as upload complete."
  [db task-id]
  (mapv first
        (d/q
          '[:find ?f
            :where
            [?t :task/files ?f]
            [?f :file/upload-complete? true]
            [(missing? $ ?f :meta/deleted?)]
            (not-join [?f]
                      [?replacement :file/previous-version ?f])
            :in $ ?t]
          db task-id)))

(defn valid-task-types? [db tasks]
  (let [task-keywords (into #{}
                            (map first)
                            (d/q '[:find ?id
                                   :where
                                   [?kw :enum/attribute :task/type]
                                   [?kw :db/ident ?id]]
                                 db))]
    (every? task-keywords
            (map second tasks))))


(defn valid-tasks-sent-to-thk? [db tasks]
  (->> tasks
       (filter (fn [[_ _ sent-to-thk?]]
                 sent-to-thk?))
       (map second)
       (task-types-can-be-sent-to-thk? db)))

(defn- valid-task-triple? [[t-group t-type send-to-thk? :as task-triple]]
  (and (= (count task-triple) 3)
       (keyword? t-group)
       (keyword? t-type)
       (boolean? send-to-thk?)))

(defn- only-tasks-creatable-in-teet?
  "List of tasks contains only tasks that can be created in teet"
  [tasks]
  (->> tasks
       (mapv second)
       (keep task-model/not-creatable-in-teet)
       empty?))

(defn valid-tasks? [db activity-name tasks]
  (or (empty? tasks)
      (and (every? valid-task-triple? tasks)
           (valid-task-types? db tasks)
           (only-tasks-creatable-in-teet? tasks)
           (activity-model/valid-task-groups-for-activity? activity-name tasks)
           (valid-tasks-sent-to-thk? db tasks))))
