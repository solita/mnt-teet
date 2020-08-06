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
                   (user-model/user-ref user)
                   files))]
    (if-not (every? user-created-files files)
      (db-api/bad-request! "No such file")
      files)))

(def ^:private manager-user-id-path
  {:file [:task/_files 0 :activity/_tasks 0 :activity/manager :user/id]
   :task [:activity/_tasks 0 :activity/manager :user/id]})

(defn- manager-user-id
  "Get manager for comment about entity. For file or task comments
  it is the manager assigned to the activity."
  [db entity-type entity-id]
  (when-let [p (manager-user-id-path entity-type)]
    (get-in (du/entity db entity-id) p)))

(defn- participants
  "Returns all participants (excluding `except-user`) in the comment thread.

  When comment is about task or file in the task, the manager is always considered
  to be a participant. For comments on files, the file creator is always considered
  to be a participant.

  If internal? is true, returns only participants allowed to
  read internal comments."
  [db entity-type entity-id internal?]
  (let [project-id (project-db/entity-project-id db entity-type entity-id)
        manager-uid (manager-user-id db entity-type entity-id)
        attr (comment-model/comments-attribute-for-entity-type entity-type)
        query {:find '[(distinct ?author)]
               :where [['?entity attr '?comment]
                       '[?comment :comment/author ?author]]
               :in '[$ ?entity]}
        participants (into (if manager-uid
                             #{[:user/id manager-uid]}
                             #{})
                           ;; if entity id is a tuple for the first creation of estate-comments or owner-comments
                           ;; This is because the tuple entity can't be resolved and thus will fail
                           ;; There also cannot be any pre-existing comments because the whole entity is created
                           (when-let [id (if (number? entity-id)
                                           entity-id
                                           (:db/id (du/entity db entity-id)))]
                             (ffirst (d/q query
                                          db id))))
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

(defn mentioned-user-ids
  [db mentions]
  (into #{}
        (map (comp :db/id first))

        (d/q '[:find (pull ?e [:db/id])
           :in $ [?e ...]]
         db
         (map user-model/user-ref mentions))))

(defn extract-mentions [text]
  (into #{}
        (keep (fn [[mention _]]
                (when-let [[_ id] (re-matches comment-model/user-mention-id-pattern mention)]
                  (Long/parseLong id))))
        (re-seq comment-model/user-mention-pattern text)))

(defn comment-entity-tx
  [entity-tuple transaction-id]
  (let [project (-> entity-tuple
                    second
                    first)
        target (-> entity-tuple
                   second
                   second)]
    [(case (first entity-tuple)
       :estate-comments/project+estate-id
       {:db/id transaction-id
        :estate-comments/project project
        :estate-comments/estate-id target}
       :owner-comments/project+owner-id
       {:db/id transaction-id
        :owner-comments/project project
        :owner-comments/owner-id target}
       :unit-comments/project+unit-id
       {:db/id transaction-id
        :unit-comments/project project
        :unit-comments/unit-id target}
       (throw (ex-info "Given entity tuple doesn't match known cases"
                       {:entity-tuple entity-tuple})))]))

(defn- valid-visibility-for-user? [user project-id visibility]
  (or (du/enum= visibility :comment.visibility/all)
      (authorization-check/authorized? user
                                       :projects/set-comment-visibility
                                       {:project-id project-id})))


(defcommand :comment/create
  {:doc "Create a new comment and add it to an entity"
   :context {:keys [db user]}
   :payload {:keys [entity-id entity-type comment files visibility track?] :as payload}
   :project-id (project-db/entity-project-id db entity-type entity-id)
   :authorization {:project/write-comments {:db/id entity-id}}
   :pre [(valid-visibility-for-user? user
                                     (project-db/entity-project-id db entity-type entity-id)
                                     visibility)]
   :transact
   (let [mentioned-ids (extract-mentions comment)
         project-id (project-db/entity-project-id db entity-type entity-id)
         resolved-id (if (number? entity-id)
                       entity-id
                       (:db/id (du/entity db entity-id)))
         transact-id (if resolved-id
                       resolved-id
                       "new-entity")]
     (into [{:db/id transact-id
             (comment-model/comments-attribute-for-entity-type entity-type)
             [(merge {:db/id "new-comment"
                      :comment/author (user-model/user-ref user)
                      :comment/comment comment
                      ;; TODO: Can external partners set visibility?
                      :comment/visibility visibility
                      :comment/mentions (extract-mentions comment)
                      :comment/timestamp (Date.)
                      :comment/status (comment-status user
                                                      (project-db/entity-project-id db entity-type entity-id)
                                                      track?)}
                     (creation-meta user)
                     (when (seq files)
                       {:comment/files (validate-files db user files)}))]}]
           (concat
             (when (not resolved-id)
               (comment-entity-tx entity-id transact-id))
             ;; Comment mention notification for mentioned users
             (map #(notification-db/notification-tx
                     db
                     {:from user
                      :to %
                      :type :notification.type/comment-mention
                      :target "new-comment"
                      :project project-id})
                  mentioned-ids)

             ;; Comment creation notification for participants except mentioned ones
             (map #(notification-db/notification-tx
                     db
                     {:from user
                      :to %
                      :type :notification.type/comment-created
                      :target "new-comment"
                      :project project-id})
                  (set/difference (participants db entity-type entity-id
                                                (= visibility :comment.visibility/internal))
                                  mentioned-ids)))))})

(defn- get-project-id-of-comment [db comment-id]
  (let [[parent-type parent-id] (comment-db/comment-parent db comment-id)]
    (case parent-type
      :file (project-db/file-project-id db parent-id)
      :task (project-db/task-project-id db parent-id)
      :estate-comments (project-db/estate-comments-project-id db parent-id)
      :owner-comments (project-db/owner-comments-project-id db parent-id)
      :unit-comments (project-db/unit-comments-project-id db parent-id)
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
             visibility :comment/visibility mentions :comment/mentions}
   :project-id (get-project-id-of-comment db comment-id)
   :authorization {:project/edit-comments {:db/id comment-id}}
   :pre [(valid-visibility-for-user? user               ;; TODO fix for owner estate and unit comments
                                     (project-db/comment-project-id db comment-id)
                                     visibility)]
   :transact
   (let [incoming-mentions (extract-mentions comment)
         old-mentions (into #{}
                            (map :db/id (:comment/mentions (d/pull db '[:comment/mentions] comment-id))))
         un-mentioned (set/difference old-mentions incoming-mentions)
         new-mentions (set/difference incoming-mentions old-mentions)]
     (into [(merge {:db/id comment-id
                    :comment/comment comment
                    :comment/visibility visibility
                    :comment/mentions (vec new-mentions)}
                   (modification-meta user))]
           (concat (update-files user
                                 comment-id
                                 (set files)
                                 (files-in-db db comment-id))
                   (map #(notification-db/notification-tx
                           db
                           {:from user
                            :to %
                            :type :notification.type/comment-mention
                            :target comment-id
                            :project (get-project-id-of-comment db comment-id)})
                        new-mentions)
                   (for [removed-mention un-mentioned]
                     [:db/retract comment-id :comment/mentions removed-mention]))))})

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
  (let [[entity-type entity-id] (comment-db/comment-parent db id)
        visibility (get-in (du/entity db id)
                           [:comment/visibility :db/ident])]
           ;; The comment status update itself
    (into [(comment-status-tx user id status)]

          ;; Notifications of the status update
          (when (comment-model/tracked-statuses status)
            (map #(notification-db/notification-tx
                    db
                    {:from user
                     :to %
                     :type (status->notification status)
                     :target id
                     :project (project-db/entity-project-id db entity-type entity-id)})
                 (participants db entity-type entity-id
                               (= visibility :comment.visibility/internal)))))))

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
