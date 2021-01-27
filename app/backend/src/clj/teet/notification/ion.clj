(ns teet.notification.ion
  (:require [teet.cooperation.cooperation-db :as cooperation-db]
            [teet.cooperation.cooperation-notifications :as cooperation-notifications]
            [teet.activity.activity-db :as activity-db]
            [teet.environment :as environment]
            [datomic.client.api :as d]
            [teet.notification.notification-db :as notification-db]))

(defn notify-tx-data
  "Transaction data for notification"
  [db application-id]
  (let [ project-id (cooperation-db/application-project-id db application-id)
         activity-id (cooperation-db/application-activity-id db application-id)
         user-to (activity-db/activity-manager db activity-id)]
     (notification-db/system-notification-tx db
       {:to user-to
        :project project-id
        :target application-id
        :type cooperation-notifications/application-response-expired-soon})) )

(defn notify
  "Given number of days before Application expiration
  new notification is sent to active Activity PM and
  consultant who entered the application into system
  providing the Activity is not finished"
  ([days]
   (let [conn (environment/datomic-connection) db (d/db conn)]
     (map #(let [app-id (first %)
                 current-tx-data ['(teet.notification.ion/notify-tx-data app-id)]]
             (d/transact conn {:tx-data current-tx-data}))
       (cooperation-db/applications-to-be-expired db days))))
  (;; default 45 days
   []
   (notify 45)))
