(ns teet.notification.notification-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [teet.notification.notification-db :as notification-db]))

(defquery :notification/unread-notifications
  {:doc "Fetch unread notifications for user, sorted by most recent first."
   :context {:keys [db user]}
   :args _
   :project-id _
   :authorization {}}
  (->> (notification-db/unread-notifications db user)
       ;; TODO: Assoc a URL string to the notification target, or
       ;; necessary data to build the URL on the frontend side. We may
       ;; need target type as well as id for this.
       (sort-by :meta/created-at)))
