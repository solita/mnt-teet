(ns teet.project.project-commands
  (:require [teet.db-api.core :as db-api]
            [datomic.client.api :as d]
            [teet.log :as log]
            [clojure.string :as str])
  (:import (java.util Date)))


(defmethod db-api/command! :thk.project/initialize!
  [{conn :conn}
   {:thk.project/keys [id owner project-name]}]
  (let [{:thk.project/keys [estimated-start-date estimated-end-date]}
        (d/pull (d/db conn) [:thk.project/estimated-start-date :thk.project/estimated-end-date]
                [:thk.project/id id])]
    (d/transact
     conn
     {:tx-data [(merge {:thk.project/id id
                        :thk.project/owner [:user/id (:user/id owner)]

                        ;; FIXME: these should be received from THK, now create a design/construction
                        ;; lifecycle that covers the project date range
                        :thk.project/lifecycles
                        (let [start-ms (.getTime estimated-start-date)
                              end-ms (.getTime estimated-end-date)
                              halfway-date (java.util.Date. (+ start-ms (/ (- end-ms start-ms) 2)))]
                          [{:db/id "design-lifecycle"
                            :thk.lifecycle/type [:db/ident :thk.lifecycle-type/design]
                            :thk.lifecycle/estimated-start-date estimated-start-date
                            :thk.lifecycle/estimated-end-date halfway-date}
                           {:db/id "construction-lifecycle"
                            :thk.lifecycle/type [:db/ident :thk.lifecycle-type/construction]
                            :thk.lifecycle/estimated-start-date halfway-date
                            :thk.lifecycle/estimated-end-date estimated-end-date}])}
                       (when-not (str/blank? project-name)
                         {:thk.project/project-name project-name}))]}))
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
