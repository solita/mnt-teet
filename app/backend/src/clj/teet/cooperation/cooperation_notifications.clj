(ns teet.cooperation.cooperation-notifications
  "Notifications functions for Cooperations")

(defn send-notification-comment-added
  "Send email to Activity Project Manager if comment has added to Cooperation Application"
  [db activity-id application-id]
  ;; TODO: implement
  (println
    (str send-notification-comment-added "send-notification-comment-added called "
      activity-id
      application-id)))

(defn send-notification-application-expired-in-45-days
  "Sending email about third party cooperation application expiration to Project Manager of Activity"
  [db activity-id application-id]
  ;; TODO: implement
  (println
    (str "send-notification-application-expired-in-45-days called with "
      activity-id
      application-id)))