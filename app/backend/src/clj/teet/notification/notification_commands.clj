(ns teet.notification.notification-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.notification.notification-db :as notification-db]))

(defcommand :notification/acknowledge
  {:doc "Acknowledge a notification"
   :context {:keys [db user]}
   :payload {notification-id :notification-id}
   :allowed-for-all-users? true
   :pre [(notification-db/user-notification? db user notification-id)]
   :transact
   (vec
    (for [id (notification-db/similar-notifications db notification-id)]
      {:db/id id
       :notification/status :notification.status/acknowledged}))})

(defcommand :notification/acknowledge-many
  {:doc "Acknowledge many notifications at once"
   :context {:keys [db user]}
   :payload {notification-ids :notification-ids}
   :allowed-for-all-users? true
   :pre [(every? #(notification-db/user-notification? db user %) notification-ids)]
   :transact
   (vec
    (for [id notification-ids]
      {:db/id id
       :notification/status :notification.status/acknowledged}))})
