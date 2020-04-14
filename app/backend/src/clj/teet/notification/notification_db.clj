(ns teet.notification.notification-db
  "Utilities for notifications"
  (:require [datomic.client.api :as d]
            [teet.meta.meta-model :as meta-model]
            [teet.user.user-model :as user-model]))



(defn notification-tx
  "Return a notification transaction map."
  [{:keys [from    ;; user whose action generated the notification
           to      ;; user who receives the notification
           target  ;; id of the entity targeted by the notification
           type]}] ;; notification type
  {:pre [(user-model/user-ref from)
         (user-model/user-ref to)
         (keyword? type)
         (some? target)]}
  (merge {:db/id (str "new-notification-" (str (java.util.UUID/randomUUID)))
          :notification/receiver (user-model/user-ref to)
          :notification/status :notification.status/unread
          :notification/target target
          :notification/type type}
         (meta-model/creation-meta from)))

(defn unread-notifications
  "Return unread notifications for user"
  [db user]
  ;; TODO
  (map first
       (d/q '[:find (pull ?notification [:db/id :notification/status
                                         :notification/target :notification/type
                                         :meta/created-at :meta/creator])
              :in $ ?user
              :where
              [?notification :notification/receiver ?user]
              [?notification :notification/status :notification.status/unread]]
            db
            (:db/id user))))

(defn navigation-info
  "Fetch notification type and target for user's notification."
  [db user notification-id]
  (ffirst (d/q '[:find (pull ?notification [:notification/target :notification/type])
                 :where [?notification :notification/receiver ?user]
                 :in $ ?notification ?user]
               db
               notification-id
               (user-model/user-ref user))))

(defn user-notification? [db user notification-id]
  (boolean
   (seq
    (d/q '[:find ?notification
           :where [?notification :notification/receiver ?user]
           :in $ ?user ?notification]
         db
         (user-model/user-ref user)
         notification-id))))
