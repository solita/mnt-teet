(ns teet.project.project-commands
  (:require [teet.db-api.core :as db-api]
            [datomic.client.api :as d]
            [teet.log :as log]
            [teet.project.project-model :as project-model]
            [clojure.string :as str])
  (:import (java.util Date)))

(defmethod db-api/command! :thk.project/initialize!
  [{conn :conn}
   {:thk.project/keys [id owner manager project-name]}]
  (let [project-in-datomic (d/pull (d/db conn)
                                   [:thk.project/owner :thk.project/estimated-start-date :thk.project/estimated-end-date]
                                   [:thk.project/id id])]
    (if (project-model/initialized? project-in-datomic)
      (db-api/fail! {:error :project-already-initialized
                     :msg (str "Project " id " is already initialized")
                     :status 409})
      (d/transact
       conn
       {:tx-data [(merge {:thk.project/id id
                          :thk.project/owner [:user/id (:user/id owner)]}
                         (when-not (str/blank? project-name)
                           {:thk.project/project-name project-name})
                         (when manager
                           {:thk.project/manager [:user/id (:user/id manager)]}))]})))
  :ok)

(defmethod db-api/command! :project/delete-task
  [{conn :conn}
   {task-id :db/id}]
  (d/transact
    conn
    {:tx-data [[:db/retractEntity task-id]
               {:db/id "datomic.tx"
                :deletion/eid [task-id]}]})
  :ok)


(defmethod db-api/command! :project/create-activity [{conn :conn} activity]
  (log/info "ACTIVITY: " activity)
  (select-keys
    (d/transact
      conn
      {:tx-data [(merge
                   {:db/id "new-activity"}
                   (select-keys activity [:activity/name :activity/status
                                          :activity/estimated-start-date
                                          :activity/estimated-end-date]))
                 {:db/id (:lifecycle-id activity)
                  :thk.lifecycle/activities ["new-activity"]}]})
    [:tempids]))

(defmethod db-api/command! :project/update-activity [{conn :conn} activity]
  (select-keys (d/transact conn {:tx-data [(assoc activity :activity/modified (Date.))]}) [:tempids]))

(defmethod db-api/command! :project/update-task [{conn :conn} task]
  (select-keys (d/transact conn {:tx-data [(assoc task :task/modified (Date.))]}) [:tempids]))

(defmethod db-api/command! :project/add-task-to-activity [{conn :conn} {activity-id :activity-id
                                                                         task :task :as payload}]
  (log/info "PAYLOAD: " payload)
  (select-keys (d/transact conn {:tx-data [{:db/id activity-id
                                            :activity/tasks [task]}]}) [:tempids]))

(defmethod db-api/command! :project/comment-task [{conn  :conn
                                                    user :user}
                                                   {task-id :task-id
                                                    comment :comment}]
  (log/info "USER: " user)
  (select-keys
    (d/transact conn {:tx-data [{:db/id task-id
                                 :task/comments [{:db/id "comment"
                                                  :comment/comment comment
                                                  :comment/timestamp (Date.)
                                                  :comment/author [:user/id (:user/id user)]}]}]})
    [:tempids]))
