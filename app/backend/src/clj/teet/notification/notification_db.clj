(ns teet.notification.notification-db
  "Utilities for notifications"
  (:require [datomic.client.api :as d]
            [teet.meta.meta-model :as meta-model]
            [teet.user.user-model :as user-model]
            [teet.util.datomic :as du]
            [teet.comment.comment-db :as comment-db]
            [teet.project.project-model :as project-model]
            [teet.comment.comment-model :as comment-model]
            [teet.user.user-db :as user-db])
  (:import (java.util UUID)))

(defn notification-tx
  "Return a notification transaction map when the target of notification is not the cause of the notification"
  [db
   {:keys [from       ; user whose action generated the notification
           to         ; user who receives the notification
           target     ; id of the entity targeted by the notification
           type       ; notification type
           project]}] ; project this notification is in context of
  {:pre [(user-model/user-ref from)
         (user-model/user-ref to)
         (keyword? type)
         (some? target)
         (or (nil? project) (project-model/project-ref project))]}
  (let [from-id (user-db/resolve-user db from)
        to-id (user-db/resolve-user db to)]
    (if-not (= from-id to-id)
      (merge {:db/id (str "new-notification-" (str (UUID/randomUUID)))
              :notification/receiver to-id
              :notification/status :notification.status/unread
              :notification/target target
              :notification/type type}
             (when project
               {:notification/project (project-model/project-ref project)})
             (meta-model/creation-meta from))
      {})))

(def notification-keys [:db/id :notification/status
                        :notification/target :notification/type
                        :meta/created-at :meta/creator
                        {:notification/project [:db/id
                                                :thk.project/id :thk.project/name
                                                :thk.project/project-name]}])

(defn unread-notifications
  "Return unread notifications for user"
  [db user]
  ;; TODO
  (mapv first
       (d/q '[:find (pull ?notification notification-keys)
              :in $ ?user notification-keys
              :where
              [?notification :notification/receiver ?user]
              [?notification :notification/status :notification.status/unread]]
            db (user-model/user-ref user) notification-keys)))


(defn user-notifications
  "Return notifications for user. Returns all unread notifications
  even if there are more than `limit`.
  If there are less than `limit` of unread notifications, adds
  newest acknowledged notifications upto `limit`."
  [db user limit]
  (let [unreads (unread-notifications db user)
        notification-ids
        (when (< (count unreads) limit)
          (->> (d/q '[:find ?n ?t
                      :where
                      [?n :notification/receiver ?user]
                      [?n :notification/status :notification.status/acknowledged]
                      [?n :meta/created-at ?t]
                      :in $ ?user]
                    db (user-model/user-ref user))
               (sort-by second)
               (take (- limit (count unreads)))
               (mapv first)
               reverse))]
    (into unreads
          (->>
           (d/q '[:find (pull ?n notification-keys)
                  :in $ notification-keys [?n ...]]
                db notification-keys (or notification-ids []))
           (mapv first)
           (sort-by :meta/created-at)
           reverse))))

(defn navigation-info
  "Fetch notification type and target for user's notification."
  [db user notification-id]
  (ffirst
   (d/q '[:find (pull ?notification [:notification/target :notification/type])
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

(defn similar-notifications
  "Fetch notifications that should be acknowledged at the same time
  as the given notification. Returns set of notification ids including
  the given notification.

  If the notification type is a comment, returns all unread notifications
  for the user and the same target. Otherwise just returns the notification id."
  [db notification-id]
  (let [{:notification/keys [type target receiver]}
        (d/pull db '[:notification/type
                     :notification/target
                     :notification/receiver] notification-id)]
    (if (du/enum= type :notification.type/comment-created)
      (let [[parent-type parent-id] (comment-db/comment-parent db (:db/id target))]
        (into [notification-id]
              (map first)
              ;; Query all user's unread notifications that are for notifications
              ;; of comments for the same parent
              (d/q [:find '?id
                    :where
                    '[?id :notification/target ?target]
                    '[?id :notification/receiver ?user]
                    '[?id :notification/status :notification.status/unread]
                    ['?parent-id (comment-model/type->comments-attribute parent-type) '?target]
                    :in '$ '?user '?parent-id]
                   db (:db/id receiver) parent-id)))

      ;; Return just the given notification id
      [notification-id])))

(defn notifications-to-send
  "Fetch all notifications that need sending.

  Returns all notifications that have not been sent by email and are unread.
  Filters out notifications that are older than 1 day as a safety precaution."
  [db]
  (let [one-day-ago (java.util.Date.
                     (- (System/currentTimeMillis)
                        (* 1000 60 60 24)))]
    (mapv first
          (d/q '[:find (pull ?n [:db/id
                                 :notification/type
                                 :meta/created-at
                                 {:notification/receiver [:db/id :user/email]}])
                 :where
                 [(missing? $ ?n :notification/email-sent-at)]
                 [?n :notification/status :notification.status/unread]
                 [?n :meta/created-at ?created]
                 [(> ?created ?one-day-ago)]
                 :in $ ?one-day-ago]
               db one-day-ago))))
