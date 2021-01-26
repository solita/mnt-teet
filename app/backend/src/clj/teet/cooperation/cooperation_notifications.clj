(ns teet.cooperation.cooperation-notifications
  "Notifications functions for Cooperations"
  (:require [teet.notification.notification-db :as notification-db]))

(def new-response-type
  "Type of notification when new Response added to Application"
  :notification.type/cooperation-response-to-application-added)

(def application-response-expired-soon
  "Type of notification when the Application response expired in number of days"
  :notification.type/cooperation-response-expired-soon)

(defn application-response-notification-tx
  "Add notification of given type,
  return a notification transaction map or empty map if receiver or sender is empty"
  [db user activity-user project application-id notification-type]
  (if
    (and
      (some? user)
      (some? activity-user))
    (notification-db/notification-tx
      db {:from user
          :to activity-user
          :target application-id
          :type notification-type
          :project project})
    {}))
