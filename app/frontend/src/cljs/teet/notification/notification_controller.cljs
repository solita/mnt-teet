(ns teet.notification.notification-controller
  (:require [tuck.core :as t]))

(defrecord Acknowledge [notification-id on-acknowledge])
(defrecord AcknowledgeResult [on-acknowledge result])
(defrecord NavigateTo [notification-id])
(defrecord NavigateToResult [result])

(extend-protocol t/Event
  Acknowledge
  (process-event [{id :notification-id
                   on-acknowledge :on-acknowledge} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :notification/acknowledge
           :payload {:notification-id id}
           :result-event (partial ->AcknowledgeResult on-acknowledge)}))

  AcknowledgeResult
  (process-event [{:keys [on-acknowledge]} app]
    (on-acknowledge)
    app)

  NavigateTo
  (process-event [{:keys [notification-id]} app]
    (t/fx app
          {:tuck.effect/type :query
           :query :notification/navigate
           :args {:notification-id notification-id}
           :result-event ->NavigateToResult}))

  NavigateToResult
  (process-event [{:keys [result]} app]
    (t/fx app
          (merge {:tuck.effect/type :navigate
                  :params {}
                  :query {}}
                 result))))
