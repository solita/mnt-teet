(ns teet.comment.comment-commands
  (:require [clojure.set :as set]
            [teet.db-api.core :as db-api :refer [defcommand]]
            [datomic.client.api :as d]
            teet.file.file-spec
            [teet.meta.meta-model :refer [creation-meta modification-meta deletion-tx]]
            [teet.meta.meta-query :as meta-query]
            [teet.comment.comment-db :as comment-db]
            [teet.comment.comment-model :as comment-model]
            [teet.project.project-db :as project-db]
            [teet.authorization.authorization-check :as authorization-check]
            [teet.permission.permission-db :as permission-db]
            [teet.notification.notification-db :as notification-db]
            [teet.user.user-model :as user-model]
            [teet.util.datomic :as du])
  (:import (java.util Date)))

(defn- validate-files
  "Validate comment attachments. They must be created
  by the same user and they must be images."
  [db user files]
  (let [user-created-files
        (into #{}
              (map first)
              (d/q '[:find ?c
                     :where
                     [?c :meta/creator ?user]
                     [?c :file/type ?type]
                     [(.startsWith ^String ?type "image/")]
                     :in $ $user [?c ...]]
                   db
                   [:user/id (:user/id user)]
                   files))]
    (if-not (every? user-created-files files)
      (db-api/bad-request! "No such file")
      files)))

(defn- participants
  "Returns all participants (excluding `except-user`) in the comment thread.

  The project manager is always considered to be a participant.
  For comments on files, the file creator is always considered to be a participant.

  If internal? is true, returns only participants allowed to
  read internal comments."
  [db entity-type entity-id internal? except-user]
  (let [project-id (project-db/entity-project-id db entity-type entity-id)
        project-manager-uid (get-in (du/entity db project-id)
                                    [:thk.project/manager :db/id])
        attr (comment-model/comments-attribute-for-entity-type entity-type)
        query {:find '[(distinct ?author)]
               :where [['?entity attr '?comment]
                       '[?comment :comment/author ?author]]
               :in '[$ ?entity]}
        participants (disj (into (if project-manager-uid
                                 #{project-manager-uid}
                                 #{})
                               (ffirst (d/q query
                                            db entity-id)))
                           (:db/id (du/entity db (user-model/user-ref except-user))))
        participants (if (= entity-type :file)
                       (conj participants
                             (get-in (du/entity db entity-id)
                                     [:meta/creator :db/id]))
                       participants)]
    (if internal?
      ;; Filter to participants who can view internal comments
      (into #{}
            (filter (fn [participant]
                      (authorization-check/authorized?
                       {:user/permissions (permission-db/user-permissions db participant)}
                       :project/view-internal-comments
                       {:project-id project-id}))
                    participants))
      ;; Return all participants
      participants)))

(defn- comment-status
  [user project-id track?]
  (if (authorization-check/authorized? user
                                       :project/track-comment-status
                                       {:project-id project-id})
    (if track?
      :comment.status/unresolved
      :comment.status/untracked)
    :comment.status/untracked))

(defcommand :comment/create
  {:doc "Create a new comment and add it to an entity"
   :context {:keys [db user]}
   :payload {:keys [entity-id entity-type comment files visibility track?]}
   :project-id (project-db/entity-project-id db entity-type entity-id)
   :authorization {:project/write-comments {:db/id entity-id}}
   :transact
   (into [(merge {:db/id entity-id
                  (comment-model/comments-attribute-for-entity-type entity-type)
                  [(merge {:db/id "new-comment"
                           :comment/author [:user/id (:user/id user)]
                           :comment/comment comment
                           ;; TODO: Can external partners set visibility?
                           :comment/visibility visibility
                           :comment/timestamp (Date.)
                           :comment/status (comment-status user
                                                           (project-db/entity-project-id db entity-type entity-id)
                                                           track?)}
                          (creation-meta user)
                          (when (seq files)
                            {:comment/files (validate-files db user files)}))]})]
         (map #(notification-db/notification-tx
                {:from user
                 :to %
                 :type :notification.type/comment-created
                 :target "new-comment"
                 :project (project-db/entity-project-id db entity-type entity-id)}))
         (participants db entity-type entity-id
                       (= visibility :comment.visibility/internal)
                       user))})

(defn- comment-parent-entity [db comment-id]
  (if-let [file-id (ffirst
                    (d/q '[:find ?file
                           :in $ ?comment
                           :where [?file :file/comments ?comment]]
                         db comment-id))]
    [:file file-id]
    (if-let [task-id (ffirst
                      (d/q '[:find ?task
                             :in $ ?comment
                             :where [?task :task/comments ?comment]]
                           db comment-id))]
      [:task task-id]
      nil)))

(defn- get-project-id-of-comment [db comment-id]
  (let [[parent-type parent-id] (comment-parent-entity db comment-id)]
    (case parent-type
      :file (project-db/file-project-id db parent-id)
      :task (project-db/task-project-id db parent-id)
      (db-api/bad-request! "No such comment"))))

(defn- files-in-db [db comment-id]
  (->> (d/pull db
               [:comment/files]
               comment-id)
       :comment/files
       (meta-query/without-deleted db)
       (map :db/id)
       set))

(defn- update-files [user comment-id files-in-command files-in-db]
  (let [to-be-removed (set/difference files-in-db files-in-command)
        to-be-added (set/difference files-in-command files-in-db)]
    (into []
          (concat (for [id-to-remove to-be-removed]
                    (deletion-tx user id-to-remove))
                  (for [id-to-add to-be-added]
                    [:db/add
                     comment-id :comment/files id-to-add])))))

(defcommand :comment/update
  {:doc "Update existing comment"
   :context {:keys [db user]}
   :payload {comment-id :db/id comment :comment/comment files :comment/files
             visibility :comment/visibility}
   :project-id (get-project-id-of-comment db comment-id)
   :authorization {:project/edit-comments {:db/id comment-id}}
   :transact (into [(merge {:db/id comment-id
                            :comment/comment comment
                            :comment/visibility visibility}
                           (modification-meta user))]
                   (update-files user
                                 comment-id
                                 (set files)
                                 (files-in-db db comment-id)))})

(defcommand :comment/delete-comment
  {:doc "Delete existing comment"
   :context {:keys [db user]}
   :payload {:keys [comment-id]}
   :project-id (get-project-id-of-comment db comment-id)
   :authorization {:project/delete-comments {:db/id comment-id}}
   :transact [(deletion-tx user comment-id)]})

(defn- comment-status-tx [user id status]
  (merge {:db/id id
          :comment/status status}
         (modification-meta user)))

(def ^:private status->notification
  {:comment.status/unresolved :notification.type/comment-unresolved
   :comment.status/resolved   :notification.type/comment-resolved})

(defn- get-comment-status-update-txs
  "Return the comment status notification itself along with
  notifications of the update"
  [db user id status]
  (let [[entity-type entity-id] (comment-parent-entity db id)
        visibility (get-in (du/entity db id)
                           [:comment/visibility :db/ident])]
           ;; The comment status update itself
    (into [(comment-status-tx user id status)]

          ;; Notifications of the status update
          (when (comment-model/tracked-statuses status)
            (map #(notification-db/notification-tx
                   {:from user
                    :to %
                    :type (status->notification status)
                    :target id
                    :project (project-db/entity-project-id db entity-type entity-id)})
                 (participants db entity-type entity-id
                               (= visibility :comment.visibility/internal)
                               user))))))

(defcommand :comment/set-status
  {:doc "Toggle the tracking status of the comment"
   :context {:keys [db user]}
   :payload {comment-id :db/id status :comment/status}
   :project-id (get-project-id-of-comment db comment-id)
   :authorization {:project/track-comment-status {:db/id comment-id}}
   :transact (get-comment-status-update-txs db user comment-id status)})

(defcommand :comment/resolve-comments-of-entity
  {:doc "Resolve multiple comments"
   :context {:keys [db user]}
   :payload {:keys [entity-id entity-type]}
   :project-id (project-db/entity-project-id db entity-type entity-id)
   :authorization {:project/track-comment-status {}}
   :transact (into []
                   (->> (comment-db/comments-of-entity db
                                                       entity-id
                                                       entity-type
                                                       nil
                                                       '[:db/id :comment/status])
                        (filter comment-model/tracked?)
                        (map :db/id)
                        (mapcat #(get-comment-status-update-txs db user % :comment.status/resolved))))})
