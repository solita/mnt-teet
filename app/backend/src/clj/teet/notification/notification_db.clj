(ns teet.notification.notification-db
  "Utilities for notifications"
  (:require [datomic.client.api :as d]
            [teet.db-api.core :as db-api]
            [teet.meta.meta-model :as meta-model]
            teet.user.user-spec
            [clojure.spec.alpha :as s]))

(defn user-eid? [user]
  (s/valid? :user/eid user))

(defn notification-tx
  "Return a notification transaction map."
  [{:keys [from    ;; user whose action generated the notification
           to      ;; user who receives the notification
           target  ;; id of the entity targeted by the notification
           type]}] ;; notification type
  {:pre [(user-eid? from)
         (user-eid? to)
         (keyword? type)
         (some? target)]}
  (merge {:db/id (str "new-notification-" (str (java.util.UUID/randomUUID)))
          :notification/receiver (:db/id to)
          :notification/status :notification.status/unread
          :notification/target target
          :notification/type type}
         (meta-model/creation-meta from)))

(defn unread-notifications
  "Return unread notifications for user"
  [db user]
  ;; TODO
  (map first
       (d/q '[:find (pull ?notification [:notification/status :notification/target :notification/type
                                         :meta/created-at :meta/creator])
              :in $ ?user
              :where
              [?notification :notification/receiver ?user]
              [?notification :notification/status :notification.status/unread]]
            db
            (:db/id user))))
