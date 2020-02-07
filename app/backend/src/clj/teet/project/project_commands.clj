(ns teet.project.project-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
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
      (db-api/fail! {:error :project-already-initialized
                     :msg (str "Project " id " is already initialized")
                     :status 409})
      (let [{db :db-after}
            (d/transact
             conn
             {:tx-data [(merge {:thk.project/id id
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
         (environment/config-map {:api-url [:api-url]
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

(defn permission-project-id [db permission-id]
  ;; PENDING: currently permissions have one project
  (ffirst
   (d/q '[:find ?project
          :in $ ?permission
          :where [?permission :permission/projects ?project]]
        db permission-id)))

(defcommand :project/revoke-permission
  ;; Options
  {:doc "Revoke a permission by setting its validity to end now."
   :context {:keys [user db]} ; bindings from ctx map
   :payload {:keys [permission-id]} ; bindings from payload
   :project-id (permission-project-id db permission-id)
   :authorization {:project/edit-permissions {:link :thk.project/owner}}
   :transact [(merge {:db/id permission-id
                      :permission/valid-until (Date.)}
                     (modification-meta user))]})

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
                             [(merge {:db/id               "new-permission"
                                      :permission/role     :internal-consultant
                                      :permission/projects project-id
                                      :permission/valid-from (Date.)}
                                     (creation-meta user))]}]})
        {:success "User added successfully"})
      (throw (ex-info "User is already added"
                      {:status 400
                       :error :permission-already-granted})))))

(defmethod db-api/command! :project/create-activity [{conn :conn
                                                      user :user} activity]
  (log/info "ACTIVITY: " activity)
  (select-keys
    (d/transact
      conn
      {:tx-data [(merge
                   {:db/id "new-activity"}
                   (select-keys activity [:activity/name :activity/status
                                          :activity/estimated-start-date
                                          :activity/estimated-end-date])
                   (creation-meta user))
                 {:db/id                    (:lifecycle-id activity)
                  :thk.lifecycle/activities ["new-activity"]}]})
    [:tempids]))

(defmethod db-api/command! :project/update-activity [{conn :conn
                                                      user :user} activity]

  (select-keys (d/transact conn {:tx-data [(merge activity (modification-meta user))]})
               [:tempids]))

(defn- task-project-id [db task-id]
  (ffirst
   (d/q '[:find ?project
          :in $ ?t
          :where
          [?activity :activity/tasks ?t]
          [?lifecycle :thk.lifecycle/activities ?activity]
          [?project :thk.project/lifecycles ?lifecycle]]
        db task-id)))

(defcommand :project/update-task
  {:doc "Update basic task information for existing task."
   :context {:keys [user db]} ; bindings from context
   :payload {id :db/id :as task} ; bindings from payload
   :project-id (task-project-id db id)
   :authorization {:task/edit-task {:db/id id
                                    :link :task/assignee}}  ; auth checks
   :transact [(merge (select-keys task
                                  [:db/id :task/name :task/description :task/status :task/assignee])
                     (modification-meta user))]})  ; tx data


(defmethod db-api/command! :project/add-task-to-activity [{conn :conn} {activity-id :activity-id
                                                                        task        :task :as payload}]
  (log/info "PAYLOAD: " payload)
  (select-keys (d/transact conn {:tx-data [{:db/id          activity-id
                                            :activity/tasks [task]}]}) [:tempids]))

(defmethod db-api/command! :project/comment-task [{conn :conn
                                                   user :user}
                                                  {task-id :task-id
                                                   comment :comment}]
  (log/info "USER: " user)
  (select-keys
    (d/transact conn {:tx-data [{:db/id         task-id
                                 :task/comments [{:db/id             "comment"
                                                  :comment/comment   comment
                                                  :comment/timestamp (Date.)
                                                  :comment/author    [:user/id (:user/id user)]}]}]})
    [:tempids]))
