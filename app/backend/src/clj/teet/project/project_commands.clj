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
            [teet.project.project-specs]
            [clojure.spec.alpha :as s]
            [teet.project.project-db :as project-db])
  (:import (java.util Date)))

(defcommand :thk.project/initialize!
  {:doc "Initialize project state. Sets project basic information and linked restrictions
and cadastral units"
   :context {conn :conn
             user :user}
   :payload {:thk.project/keys [id owner manager project-name custom-start-m custom-end-m
                                m-range-change-reason
                                related-restrictions
                                related-cadastral-units]}
   :project-id [:thk.project/id id]
   :authorization {:project/project-setup {:link :thk.project/owner}}}
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

(defcommand :thk.project/skip-setup
  {:doc "Mark project setup as skipped"
   :context {conn :conn
             user :user}
   :payload {project-id :thk.project/id}
   :project-id [:thk.project/id project-id]
   :authorization {:project/project-setup {:link :thk.project/owner}}
   :transact [(merge {:thk.project/id project-id
                      :thk.project/setup-skipped? true}
                     (modification-meta user))]})

(defcommand :thk.project/edit-project
  {:doc "Edit project basic info"
   :context {conn :conn
             user :user}
   :payload {id :thk.project/id :as project-form}
   :project-id [:thk.project/id id]
   :authorization {:project/edit-project-info {:link :thk.project/owner}}
   :transact [(merge (cu/without-nils (select-keys project-form
                                                   [:thk.project/id
                                                    :thk.project/owner
                                                    :thk.project/manager
                                                    :thk.project/project-name]))
                     (modification-meta user))]})

(defcommand :thk.project/continue-setup
  {:doc "Undo project setup skip"
   :context {conn :conn
             user :user}
   :payload {project-id :thk.project/id}
   :project-id [:thk.project/id project-id]
   :authorization {:project/project-setup {:link :thk.project/owner}}
   :transact [(merge {:thk.project/id             project-id
                      :thk.project/setup-skipped? false}
                     (modification-meta user))]})


(defcommand :project/delete-activity ;; FIXME: :activity/delete
  {:doc "Mark an activity as deleted"
   :context {db :db
             user :user}
   :payload {activity-id :db/id}
   :project-id (project-db/activity-project-id db activity-id)
   :authorization {:activity/delete-activity {}}
   :transact [(deletion-tx user activity-id)]})

(defcommand :project/revoke-permission
  ;; Options
  {:doc "Revoke a permission by setting its validity to end now."
   :context {:keys [user db]} ; bindings from ctx map
   :payload {:keys [permission-id]} ; bindings from payload
   :project-id (project-db/permission-project-id db permission-id)
   :authorization {:project/edit-permissions {:link :thk.project/owner}}
   :transact [(merge {:db/id permission-id
                      :permission/valid-until (Date.)}
                     (modification-meta user))]})

(defcommand :project/add-permission
  {:doc "Add permission to project"
   :context {:keys [conn user db]}
   :payload {project-id :project-id
             {user-id :user/id} :user}
   :spec (s/keys :req-un [::project-id ])
   :project-id project-id
   :authorization {:project/edit-permissions {:link :thk.project/owner}}}
  (let [user-already-added?
        (boolean
         (seq
          (permission-db/user-permission-for-project db [:user/id user-id] project-id)))]
    (if-not user-already-added?
      (do
        (d/transact
         conn
         {:tx-data [{:db/id [:user/id user-id]
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


(defcommand :project/create-activity ;; FIXME: :activity/create
  {:doc "Create new activity to lifecycle"
   :context {:keys [db user conn]}
   :payload {activity :activity
             lifecycle-id :lifecycle-id}
   :project-id (project-db/lifecycle-project-id db lifecycle-id)
   :authorization {:activity/create-activity {}}
   ;;:pre [(valid-activity-name? db activity lifecycle-id)]
   }
  (log/info "ACTIVITY: " activity)
  (if (valid-activity-name? db activity lifecycle-id)
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
    (db-api/bad-request! "Not a valid activity")))

(defcommand :project/update-activity ;; FIXME: :activity/update
  {:doc "Update activity basic info"
   :context {:keys [conn user db]}
   :payload activity
   :project-id (project-db/activity-project-id db (:db/id activity))
   :authorization {:activity/edit-activity {:db/id (:db/id activity)}}}
  (let [lifecycle-id (ffirst (d/q '[:find ?lc
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
      (db-api/bad-request! "Not a valid activity"))))


(defcommand :project/add-task-to-activity ;; :task/create
  {:doc "Add task to activity"
   :context {:keys [db conn user]}
   :payload {activity-id :activity-id
             task        :task :as payload}
   :project-id (project-db/activity-project-id db activity-id)
   :authorization {:task/create-task {}
                   :activity/edit-activity {:db/id activity-id}}
   :transact [(merge {:db/id          activity-id
                      :activity/tasks [task]}
                     (creation-meta user))]})
