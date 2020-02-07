(ns teet.project.project-commands
  (:require [teet.db-api.core :as db-api]
            [datomic.client.api :as d]
            [teet.log :as log]
            [teet.project.project-model :as project-model]
            [teet.permission.permission-db :as permission-db]
            [clojure.string :as str]
            [teet.project.project-geometry :as project-geometry]
            [teet.environment :as environment]
            [teet.util.collection :as cu]
            [teet.meta.meta-model :refer [modification-meta creation-meta deletion-tx]]
            [teet.project.project-specs])
  (:import (java.util Date)))

(defmethod db-api/command! :thk.project/initialize!
  [{conn :conn
    user :user}
   {:thk.project/keys [id owner manager project-name custom-start-m custom-end-m
                       m-range-change-reason
                       related-restrictions
                       related-cadastral-units]}]
  (let [project-in-datomic (d/pull (d/db conn)
                                   [:thk.project/owner :thk.project/estimated-start-date :thk.project/estimated-end-date]
                                   [:thk.project/id id])]
    (if (project-model/initialized? project-in-datomic)
      (db-api/fail! {:error  :project-already-initialized
                     :msg    (str "Project " id " is already initialized")
                     :status 409})
      (let [{db :db-after}
            (d/transact
              conn
              {:tx-data [(merge {:thk.project/id    id
                                 :thk.project/owner [:user/id (:user/id owner)]}
                                (when-not (str/blank? project-name)
                                  {:thk.project/project-name project-name})
                                (when manager
                                  {:thk.project/manager [:user/id (:user/id manager)]})
                                (when custom-start-m
                                  {:thk.project/custom-start-m custom-start-m})
                                (when custom-end-m
                                  {:thk.project/custom-end-m custom-end-m})
                                (when m-range-change-reason
                                  {:thk.project/m-range-change-reason m-range-change-reason})
                                (when related-restrictions
                                  {:thk.project/related-restrictions related-restrictions})
                                (when related-cadastral-units
                                  {:thk.project/related-cadastral-units related-cadastral-units})
                                (modification-meta user))]})]
        (project-geometry/update-project-geometries!
          (environment/config-map {:api-url           [:api-url]
                                   :api-shared-secret [:auth :jwt-secret]})
          [(d/pull db '[:db/id :thk.project/name
                        :thk.project/road-nr :thk.project/carriageway
                        :thk.project/start-m :thk.project/end-m
                        :thk.project/custom-start-m :thk.project/custom-end-m]
                   [:thk.project/id id])]))))
  :ok)

(defmethod db-api/command! :project/skip-project-setup
  [{conn :conn
    user :user}
   {project-id :thk.project/id}]
  (d/transact
    conn
    {:tx-data [(merge {:thk.project/id             project-id
                       :thk.project/setup-skipped? true}
                      (modification-meta user))]})
  :ok)


(defmethod db-api/command! :thk.project/edit-project
  [{conn :conn
    user :user}
   project-form]
  (d/transact
    conn
    {:tx-data [(merge (cu/without-nils project-form)
                      (modification-meta user))]})
  :ok)


(defmethod db-api/command! :project/continue-project-setup
  [{conn :conn
    user :user}
   {project-id :thk.project/id}]
  (d/transact
    conn
    {:tx-data [(merge {:thk.project/id             project-id
                       :thk.project/setup-skipped? false}
                      (modification-meta user))]})
  :ok)

(defmethod db-api/command! :project/delete-task
  [{conn :conn
    user :user}
   {task-id :db/id}]
  (d/transact
    conn
    {:tx-data [(deletion-tx user task-id)]})
  :ok)

(defmethod db-api/command! :project/delete-activity
  [{conn :conn
    user :user}
   {activity-id :db/id}]
  (d/transact
    conn
    {:tx-data [(deletion-tx user activity-id)]})
  :ok)


(defmethod db-api/command! :project/revoke-permission
  [{conn :conn
    user :user}
   {permission-id :permission-id}]
  (d/transact conn
              {:tx-data [(merge {:db/id                  permission-id
                                 :permission/valid-until (Date.)}
                                (modification-meta user))]})
  :ok)

(defmethod db-api/command! :project/add-permission
  [{conn :conn
    user :user}
   {project-id        :project-id
    {:user/keys [id]} :user}]
  (let [user-already-added? ((comp boolean seq)
                             (permission-db/user-permission-for-project (d/db conn) [:user/id id] project-id))]
    (if-not user-already-added?
      (do
        (d/transact
          conn
          {:tx-data [{:db/id [:user/id id]
                      :user/permissions
                             [(merge {:db/id                 "new-permission"
                                      :permission/role       :internal-consultant
                                      :permission/projects   project-id
                                      :permission/valid-from (Date.)}
                                     (creation-meta user))]}]})
        {:success "User added successfully"})
      (db-api/fail!
        {:status 400
         :msg "User is already added"
         :error  :permission-already-granted}))))

(defn valid-activity-name?
  "Check if the activity name is valid for the lifecycle it's being added to"
  [db {activity-name :activity/name} lifecycle-id]
  (boolean
    (seq
      (d/q '[:find ?lc
             :in $ ?lc ?act-enum
             :where
             [?act-enum :enum/valid-for ?v]
             [?lc :thk.lifecycle/type ?v]]
           db
           lifecycle-id
           activity-name))))

(defmethod db-api/command! :project/create-activity [{conn :conn
                                                      user :user}
                                                     {activity :activity
                                                      lifecycle-id :lifecycle-id}]
  (log/info "ACTIVITY: " activity)
  (if (valid-activity-name? (d/db conn) activity lifecycle-id)
    (select-keys
      (d/transact
        conn
        {:tx-data [(merge
                     {:db/id "new-activity"}
                     (select-keys activity [:activity/name :activity/status
                                            :activity/estimated-start-date
                                            :activity/estimated-end-date])
                     (creation-meta user))
                   {:db/id                    lifecycle-id
                    :thk.lifecycle/activities ["new-activity"]}]})
      [:tempids])
    (db-api/fail! {:status 400
                   :error  :bad-request})))

(defmethod db-api/command! :project/update-activity [{conn :conn
                                                      user :user}
                                                     activity]
  (let [db (d/db conn)
        lifecycle-id (ffirst (d/q '[:find ?lc
                                    :in $ ?act
                                    :where [?lc :thk.lifecycle/activities ?act]]
                                  db
                                  (:db/id activity)))]
    (if (valid-activity-name? db activity lifecycle-id)
      (select-keys (d/transact conn {:tx-data [(merge (select-keys activity
                                                                   [:activity/name :activity/status
                                                                    :activity/estimated-start-date
                                                                    :activity/estimated-end-date
                                                                    :db/id])
                                                      (modification-meta user))]})
                   [:tempids])
      (db-api/fail! {:status 400
                     :error  :bad-request}))))

(defmethod db-api/command! :project/update-task [{conn :conn
                                                  user :user} task]
  (select-keys (d/transact conn {:tx-data [(merge task (modification-meta user))]}) [:tempids]))

(defmethod db-api/command! :project/add-task-to-activity [{conn :conn
                                                           user :user} {activity-id :activity-id
                                                                        task        :task :as payload}]
  (log/info "PAYLOAD: " payload)
  (select-keys (d/transact conn {:tx-data [(merge {:db/id          activity-id
                                                   :activity/tasks [task]}
                                                  (creation-meta user))]}) [:tempids]))

(defmethod db-api/command! :project/comment-task [{conn :conn
                                                   user :user}
                                                  {task-id :task-id
                                                   comment :comment}]
  (log/info "USER: " user)
  (select-keys
    (d/transact conn {:tx-data [{:db/id         task-id
                                 :task/comments [(merge {:db/id             "comment"
                                                         :comment/comment   comment
                                                         :comment/timestamp (Date.)
                                                         :comment/author    [:user/id (:user/id user)]}
                                                        (creation-meta user))]}]})
    [:tempids]))
