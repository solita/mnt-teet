(ns teet.notification.notification-controller
  (:require [tuck.core :as t]))

(defrecord Acknowledge [notification-id])
(defrecord AcknowledgeResult [result])

(extend-protocol t/Event
  Acknowledge
  (process-event [{id :notification-id} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :notification/acknowledge
           :payload {:notification-id id}
           :result-event ->AcknowledgeResult}))

  AcknowledgeResult
  (process-event [_ app]
    ;; FIXME: tell notification query to rerun
    app))
