(ns teet.notification.notification-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [teet.notification.notification-db :as notification-db]
            [teet.project.project-db :as project-db]
            [datomic.client.api :as d]
            [teet.util.datomic :as du]
            [teet.comment.comment-db :as comment-db]))

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

(defn activity-navigation-info [db activity-id]
  (let [proj-id (project-db/activity-project-id db activity-id)
        proj (project-db/project-by-id db proj-id)]
    {:page :activity
     :params {
              :project (str (:thk.project/id proj))
              :activity (str activity-id)}}))

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


(defn- comment-navigation-info [db comment-id]
  (if-let [[entity-type entity-id] (comment-db/comment-parent db comment-id)]
    (merge
     (case entity-type
       :task (task-navigation-info db entity-id)
       :file (file-navigation-info db entity-id))
     {:query {:tab "comments"
              :focus-on (str comment-id)}})
    (db-api/bad-request! "No such comment")))

(defn- project-navigation-info [db project-id]
  {:page :project
   :params {:project (str (:thk.project/id (du/entity db project-id)))}})

(defquery :notification/navigate
  {:doc "Fetch navigation info for notification."
   :context {:keys [db user]}
   :args {:keys [notification-id]}
   :project-id nil
   :authorization {}}
  (if-let [{:notification/keys [type target]}
           (notification-db/navigation-info db user notification-id)]
    ;; FIXME: something more elegant? a multimethod?
    (case (:db/ident type)
      (:notification.type/task-waiting-for-review
       :notification.type/task-assigned)
      (task-navigation-info db (:db/id target))

      (:notification.type/activity-waiting-for-review
       :notification.type/activity-accepted
       :notification.type/activity-rejected)
      (activity-navigation-info db (:db/id target))

      (:notification.type/comment-created
       :notification.type/comment-resolved
       :notification.type/comment-unresolved)
      (comment-navigation-info db (:db/id target))

      :notification.type/project-manager-assigned
      (project-navigation-info db (:db/id target)))
    (db-api/bad-request! "No such notification")))
