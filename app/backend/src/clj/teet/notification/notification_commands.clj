(ns teet.notification.notification-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [teet.notification.notification-db :as notification-db]
            [datomic.client.api :as d]))

(defcommand :notification/acknowledge
  {:doc "Acknowledge a notification"
   :context {:keys [db user]}
   :payload {id :notification-id}
   :project-id nil
   :authorization {}
   :pre [(notification-db/user-notification? db user id)]
   :transact [{:db/id id
               :notification/status :notification.status/acknowledged}]})
