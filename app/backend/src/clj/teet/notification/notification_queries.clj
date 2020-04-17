(ns teet.notification.notification-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [teet.notification.notification-db :as notification-db]
            [datomic.client.api :as d]
            [teet.comment.comment-model :as comment-model]))

(defquery :notification/unread-notifications
  {:doc "Fetch unread notifications for user, sorted by most recent first."
   :context {:keys [db user]}
   :args _
   :project-id nil
   :authorization {}}
  (->> (notification-db/unread-notifications db user)
       (sort-by :meta/created-at)))

(defquery :notification/user-notifications
  {:doc "Fetch notifications for user, sorted by most recent first."
   :context {:keys [db user]}
   :args _
   :project-id nil
   :authorization {}}
  (notification-db/user-notifications db user 20))

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

(defn- file-navigation-info [db file-id]
  (let [[project activity task]
        (first (d/q '[:find ?project-id ?activity ?task
                      :where
                      [?task :task/files ?file]
                      [?activity :activity/tasks ?task]
                      [?lifecycle :thk.lifecycle/activities ?activity]
                      [?project :thk.project/lifecycles ?lifecycle]
                      [?project :thk.project/id ?project-id]
                      :in $ ?file]
                    db file-id))]
    {:page :file
     :params {:project (str project)
              :activity (str activity)
              :task (str task)
              :file (str file-id)}}))

(defn- comment-parent
  "Returns [entity-type entity-id] for the parent of the given comment.
  If parent is not found, returns nil."
  [db comment-id]
  (some (fn [[entity-type attr]]
          (when-let [entity-id (ffirst
                                (d/q [:find '?e
                                      :where ['?e attr '?c]
                                      :in '$ '?c]
                                     db comment-id))]
            [entity-type entity-id]))
        comment-model/entity-comment-attribute))

(defn comment-navigation-info [db comment-id]
  (if-let [[entity-type entity-id] (comment-parent db comment-id)]
    (merge
     (case entity-type
       :task (task-navigation-info db entity-id)
       :file (file-navigation-info db entity-id))
     {:query {:tab "comments"
              :focus-on (str comment-id)}})
    (db-api/bad-request! "No such comment")))

(defquery :notification/navigate
  {:doc "Fetch navigation info for notification."
   :context {:keys [db user]}
   :args {:keys [notification-id]}
   :project-id nil
   :authorization {}}
  (if-let [{:notification/keys [type target]} (notification-db/navigation-info db user notification-id)]
    ;; FIXME: something more elegant? a multimethod?
    (case (:db/ident type)
      (:notification.type/task-waiting-for-review :notification.type/task-assigned)
      (task-navigation-info db (:db/id target))

      :notification.type/comment-created
      (comment-navigation-info db (:db/id target)))
    (db-api/bad-request! "No such notification")))
