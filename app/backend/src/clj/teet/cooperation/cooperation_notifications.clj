(ns teet.cooperation.cooperation-notifications
  "Notifications functions for Cooperations"
  (:require [teet.notification.notification-db :as notification-db]))

(defn application-response-notification-tx
  "Add notification about new Application response,
  return a notification transaction map or empty map if receiver or sender is empty"
  [db user activity-user project application-id]
  (if
    (and
      (some? user)
      (some? activity-user))
    (notification-db/notification-tx
      db {:from user
          :to activity-user
          :target application-id
          :type :notification.type/cooperation-response-to-application-added
          :project project}) {}))

(defn send-notification-application-expired-in-45-days
  "Sending notification about third party cooperation application expiration to Project Manager of Activity"
  [db activity-id application-id]
  ;; TODO: implement
  (println
    (str "send-notification-application-expired-in-45-days called with "
      activity-id
      application-id)))