(ns teet.notification.notification-ion
  (:require [teet.cooperation.cooperation-db :as cooperation-db]
            [teet.cooperation.cooperation-notifications :as cooperation-notifications]
            [teet.activity.activity-db :as activity-db]
            [teet.environment :as environment]
            [datomic.client.api :as d]
            [teet.notification.notification-db :as notification-db]
            [teet.log :as log]
            [teet.user.user-db :as user-db]))


(defn notify-tx-data
  "Transaction data for notification"
  [db application-id]
  (let [ project-id (cooperation-db/application-project-id db application-id)
        activity-id (cooperation-db/application-activity-id db application-id)
        user-to (activity-db/activity-manager db activity-id)]
    (if (some? user-to)
      (if (user-db/is-user-deactivated? db user-to)
        (log/info "Notification skipped for application-id " application-id " because of deactivated user " user-to)
        (notification-db/system-notification-tx db
             {:to user-to
              :project project-id
              :target application-id
              :type cooperation-notifications/application-response-expired-soon}))
      (log/info "Notification skipped for application-id " application-id
        " as Activity Manager not found for activity-id " activity-id))))

(defn notify
  "Given number of days before Application expiration
  new notification is sent to active Activity PM and
  consultant who entered the application into system
  providing the Activity is not finished"
  ([event days]
   (log/info "Call notify by event: " event " with days param: " days)
   (let [conn (environment/datomic-connection) db (d/db conn)]
     (d/transact conn
       {:tx-data
        (map (comp (partial notify-tx-data db) :application-id)
          (cooperation-db/applications-to-be-expired db days))})))
  (;; read days from env config
   [event]
   (notify event (environment/config-value :notify :application-expire-days))))
