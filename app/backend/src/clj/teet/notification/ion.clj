(ns teet.notification.ion
  "Notifications lambdas")

(defn notify [_Event]
  "AWS Lambda entry point for scheduled notification"
  ;;TODO: add required notifications
  (println (str "notify called with " _Event)))



