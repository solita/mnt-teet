(ns teet.notification.notification-queries
  (:require [teet.db-api.core :as db-api :refer [defquery]]
            [teet.notification.notification-db :as notification-db]
            [teet.project.project-db :as project-db]
            [datomic.client.api :as d]
            [teet.util.datomic :as du]
            [teet.log :as log]
            [teet.comment.comment-db :as comment-db]
            [teet.cooperation.cooperation-db :as cooperation-db]
            [teet.file.file-db :as file-db]))

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
   :spec any?
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

(defn task-part-navigation-info [db task-part-id]
  (let [[project activity task-id]
        (first
          (d/q '[:find ?project-id ?activity ?task
                 :where
                 [?project :thk.project/id ?project-id]
                 [?project :thk.project/lifecycles ?lifecycle]
                 [?lifecycle :thk.lifecycle/activities ?activity]
                 [?activity :activity/tasks ?task]
                 [?task-part :file.part/task ?task]
                 :in $ ?task-part]
               db task-part-id))]
    {:page :activity-task
     :params {:project (str project)
              :activity (str activity)
              :task (str task-id)}}))

(defn activity-navigation-info [db activity-id]
  (let [proj-id (project-db/activity-project-id db activity-id)
        proj (project-db/project-by-id db proj-id)]
    {:page :activity
     :params {:project (str (:thk.project/id proj))
              :activity (str activity-id)}}))

(defn- file-navigation-info [db file-id]
  (let [[project id]
        (first (d/q '[:find ?project-id ?id
                      :where
                      [?task :task/files ?file]
                      [?file :file/id ?id]
                      [?activity :activity/tasks ?task]
                      [?lifecycle :thk.lifecycle/activities ?activity]
                      [?project :thk.project/lifecycles ?lifecycle]
                      [?project :thk.project/id ?project-id]
                      :in $ ?file]
                    db (file-db/latest-version db file-id)))]
    {:page :file
     :params {:project (str project)
              :file (str id)}}))



(defn- project-navigation-info [db project-id]
  {:page :project
   :params {:project (str (:thk.project/id (du/entity db project-id)))}})

(defn- meeting-navigation-info [db meeting-eid]
  (let [{:keys [project-thk-id activity-eid]}
        (project-db/meeting-parents db {:db/id meeting-eid} (project-db/meeting-project-id db meeting-eid))]
    (if (and project-thk-id activity-eid meeting-eid)
      {:page :meeting
       :params {:project project-thk-id
                :activity (str activity-eid)
                :meeting (str meeting-eid)}}
      ;; else
      (do
        (log/info "meeting-navigation-info couldn't answer for meeting eid" meeting-eid)
        (db-api/bad-request! "No such meeting")))))

(defn- comment-navigation-info [db comment-id]
  (if-let [[entity-type entity-id] (comment-db/comment-parent db comment-id)]
    (merge
     (case entity-type
       :task (task-navigation-info db entity-id)
       :file (file-navigation-info db entity-id)
       :meeting (meeting-navigation-info db entity-id))
     {:query {:tab "comments"
              :focus-on (str comment-id)}})
    (db-api/bad-request! "No such comment")))

(defn- cooperation-application-navigation-info [db application-id]
  (let [project-id (cooperation-db/application-project-id db application-id)
        thk-project-id (:thk.project/id (project-db/project-by-id db project-id))
        application-uuid (cooperation-db/application-uuid db application-id)
        third-party-uuid (cooperation-db/application-3rd-party-uuid db application-id)]
    {:page :cooperation-application
     :params {:project (str thk-project-id)
              :third-party (:uuid third-party-uuid)
              :application (:uuid application-uuid)}}))

(defn notification-navigation-info [db user notification-id]
  (when-let [{:notification/keys [type target]}
             (notification-db/navigation-info db user notification-id)]
    (case (:db/ident type)
      (:notification.type/task-waiting-for-review
       :notification.type/task-assigned)
      (task-navigation-info db (:db/id target))

      (:notification.type/task-part-waiting-for-review
       :notification.type/task-part-review-rejected
       :notification.type/task-part-review-accepted)
      (task-part-navigation-info db (:db/id target))

      (:notification.type/activity-waiting-for-review
       :notification.type/activity-accepted
       :notification.type/activity-rejected)
      (activity-navigation-info db (:db/id target))

      (:notification.type/comment-created
       :notification.type/comment-resolved
       :notification.type/comment-unresolved
       :notification.type/comment-mention)
      (comment-navigation-info db (:db/id target))

      :notification.type/project-manager-assigned
      (project-navigation-info db (:db/id target))

      :notification.type/meeting-updated
      (meeting-navigation-info db (:db/id target))

      :notification.type/cooperation-response-to-application-added
      (cooperation-application-navigation-info db (:db/id target))

      :notification.type/cooperation-application-expired-soon
      (cooperation-application-navigation-info db (:db/id target)))))

(defquery :notification/navigate
  {:doc "Fetch navigation info for notification."
   :context {:keys [db user]}
   :args {:keys [notification-id]}
   :project-id nil
   :authorization {}}
  (or (notification-navigation-info db user notification-id)
      (db-api/bad-request! "No such notification")))
