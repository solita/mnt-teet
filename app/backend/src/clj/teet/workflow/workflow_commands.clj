(ns teet.workflow.workflow-commands
  "Commands for workflows, activities and tasks"
  (:require [teet.db-api.core :as db-api]
            [datomic.client.api :as d]
            [clojure.spec.alpha :as s]
            [teet.workflow.workflow-specs]
            [teet.log :as log])
  (:import (java.util Date)))


(defmethod db-api/command! :activity/create-activity [{conn :conn} activity]
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

(defmethod db-api/command! :activity/update-activity [{conn :conn} activity]
  (select-keys (d/transact conn {:tx-data [(assoc activity :activity/modified (Date.))]}) [:tempids]))

(defmethod db-api/command! :workflow/update-task [{conn :conn} task]
  (select-keys (d/transact conn {:tx-data [(assoc task :task/modified (Date.))]}) [:tempids]))

(defmethod db-api/command! :workflow/add-task-to-activity [{conn :conn} {activity-id :activity-id
                                                                      task :task :as payload}]
  (log/info "PAYLOAD: " payload)
  (select-keys (d/transact conn {:tx-data [{:db/id activity-id
                                            :activity/tasks [task]}]}) [:tempids]))

(defmethod db-api/command! :workflow/comment-task [{conn :conn
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
