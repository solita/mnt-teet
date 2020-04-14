(ns teet.comment.comment-commands
  (:require [clojure.set :as set]
            [teet.db-api.core :as db-api :refer [defcommand]]
            [datomic.client.api :as d]
            teet.file.file-spec
            [teet.meta.meta-model :refer [creation-meta modification-meta deletion-tx]]
            [teet.meta.meta-query :as meta-query]
            [teet.comment.comment-model :as comment-model]
            [teet.project.project-db :as project-db])
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

(defcommand :comment/create
  {:doc "Create a new comment and add it to an entity"
   :context {:keys [db user]}
   :payload {:keys [entity-id entity-type comment files visibility]}
   :project-id (project-db/entity-project-id db entity-type entity-id)
   :authorization {:project/write-comments {}}
   :transact [(merge {:db/id entity-id
                      (comment-model/comments-attribute-for-entity-type entity-type)
                      [(merge {:db/id "new-comment"
                               :comment/author [:user/id (:user/id user)]
                               :comment/comment comment
                               ;; TODO: Can external partners set visibility?
                               :comment/visibility visibility
                               :comment/timestamp (Date.)}
                              (creation-meta user)
                              (when (seq files)
                                {:comment/files (validate-files db user files)}))]})]})

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
