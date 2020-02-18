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
            [clojure.set :as set]
            [teet.project.project-db :as project-db]
            [teet.meta.meta-model :as meta-model])
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
         (environment/config-map {:api-url           [:api-url]
                                  :api-shared-secret [:auth :jwt-secret]
                                  :wfs-url [:road-registry :wfs-url]})
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

(defcommand :thk.project/update
  {:doc "Edit project basic info"
   :context {conn :conn
             user :user}
   :payload {id :thk.project/id :as project-form}
   :project-id [:thk.project/id id]
   :authorization {:project/project-info {:eid [:thk.project/id id]
                                          :link :thk.project/owner}}
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
   :transact [(merge {:thk.project/id project-id
                      :thk.project/setup-skipped? false}
                     (modification-meta user))]})

(defcommand :thk.project/revoke-permission
  ;; Options
  {:doc "Revoke a permission by setting its validity to end now."
   :context {:keys [user db]}                               ; bindings from ctx map
   :payload {:keys [permission-id]}                         ; bindings from payload
   :project-id (project-db/permission-project-id db permission-id)
   :authorization {:project/edit-permissions {:link :thk.project/owner}}
   :transact [(merge {:db/id permission-id
                      :permission/valid-until (Date.)}
                     (modification-meta user))]})

(defn- update-related-entities-tx
  "Return transaction data to update related map features"
  [db user project-eid datasource-feature-ids link-attribute-kw]
  (let [current-entities-in-db (set
                                 (link-attribute-kw
                                   (d/pull db
                                           [link-attribute-kw]
                                           project-eid)))
        to-be-removed (set/difference current-entities-in-db datasource-feature-ids)
        to-be-added (set/difference datasource-feature-ids current-entities-in-db)]
    (into [(meta-model/tx-meta user)]
          (concat
            (for [id-to-remove to-be-removed]
              [:db/retract project-eid
               link-attribute-kw id-to-remove])
            (for [id-to-add to-be-added]
              [:db/add project-eid
               link-attribute-kw id-to-add])))))

(defcommand :thk.project/update-restrictions
  {:doc "Update project related restrictions"
   :context {:keys [user db]}
   :payload {:keys [restrictions project-id]}
   :project-id [:thk.project/id project-id]
   :authorization {:project/project-info {:eid [:thk.project/id project-id]
                                          :link :thk.project/owner}}
   :transact (update-related-entities-tx db user [:thk.project/id project-id] restrictions :thk.project/related-restrictions)})

(defcommand :thk.project/update-cadastral-units
  {:doc "Update project related cadastral-units"
   :context {:keys [user db]}
   :payload {:keys [cadastral-units project-id]}
   :project-id [:thk.project/id project-id]
   :authorization {:project/project-info {:eid [:thk.project/id project-id]
                                          :link :thk.project/owner}}
   :transact (update-related-entities-tx db user [:thk.project/id project-id] cadastral-units :thk.project/related-cadastral-units)})

(defcommand :thk.project/add-permission
  {:doc "Add permission to project"
   :context {:keys [conn user db]}
   :payload {project-id :project-id
             {user-id :user/id} :user}
   :spec (s/keys :req-un [::project-id])
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
                      [(merge {:db/id "new-permission"
                               :permission/role :internal-consultant
                               :permission/projects project-id
                               :permission/valid-from (Date.)}
                              (creation-meta user))]}]})
        {:success "User added successfully"})
      (db-api/fail!
        {:status 400
         :msg "User is already added"
         :error :permission-already-granted}))))
