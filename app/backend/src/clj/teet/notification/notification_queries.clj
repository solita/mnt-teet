(ns teet.notification.notification-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [teet.notification.notification-db :as notification-db]
            [datomic.client.api :as d]))

(defquery :notification/unread-notifications
  {:doc "Fetch unread notifications for user, sorted by most recent first."
   :context {:keys [db user]}
   :args _
   :project-id nil
   :authorization {}}
  (->> (notification-db/unread-notifications db user)
       ;; TODO: Assoc a URL string to the notification target, or
       ;; necessary data to build the URL on the frontend side. We may
       ;; need target type as well as id for this.
       (sort-by :meta/created-at)))

(defn task-navigation-info [db task-id]
  (let [[project activity]
        (first
         (d/q '[:find ?project-id ?activity
                :where
                [?project :thk.project/id ?project-id]
                [?project :thk.project/lifecycles ?lifecycle]
                [?lifecycle :thk.lifecycle/activities ?activity]
                [?activity :activity/tasks ?task]
                :in $ ?task]
              db task-id))]
    {:page :activity-task
     :params {:project (str project)
              :activity (str activity)
              :task (str task-id)}}))

(defquery :notification/navigate
  {:doc "Fetch navigation info for notification."
   :context {:keys [db user]}
   :args {:keys [notification-id]}
   :project-id nil
   :authorization {}}
  (if-let [notification (notification-db/navigation-info db user notification-id)]
    ;; FIXME: something more elegant? a multimethod?
    (case (:db/ident (:notification/type notification))
      (:notification.type/task-waiting-for-review :notification.type/task-assigned)
      (task-navigation-info db (:db/id (:notification/target notification))))
    (db-api/bad-request! "No such notification")))
