(ns teet.project.project-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand tx]]
            [datomic.client.api :as d]
            [teet.permission.permission-db :as permission-db]
            [clojure.string :as str]
            [teet.project.project-geometry :as project-geometry]
            [teet.gis.entity-features :as entity-features]
            [teet.environment :as environment]
            [teet.util.collection :as cu]
            [teet.meta.meta-model :refer [modification-meta creation-meta] :as meta-model]
            [teet.project.project-specs]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [teet.project.project-db :as project-db]
            [teet.authorization.authorization-check :as authorization-check]
            [teet.util.datomic :as du]
            [teet.integration.x-road.property-registry :as property-registry]
            [teet.integration.postgrest :as postgrest])
  (:import (java.util Date UUID)))


(defn- project-custom-m-range [db project-eid]
  (d/pull db '[:thk.project/custom-start-m :thk.project/custom-end-m] project-eid))

(defcommand :thk.project/update
  {:doc "Edit project basic info"
   :context {:keys [conn db user]}
   :payload {id :thk.project/id :as project-form}
   :project-id [:thk.project/id id]
   :authorization {:project/update-info {:eid [:thk.project/id id]
                                          :link :thk.project/owner}}}
  (let [{db-before :db-before
         db :db-after} (tx [(merge (cu/without-nils
                                    (select-keys project-form
                                                 [:thk.project/id
                                                  :thk.project/owner
                                                  :thk.project/m-range-change-reason
                                                  :thk.project/project-name
                                                  :thk.project/custom-start-m
                                                  :thk.project/custom-end-m]))
                                   (modification-meta user))])]
    (when (not= (project-custom-m-range db-before [:thk.project/id id])
                (project-custom-m-range db [:thk.project/id id]))
      (project-geometry/update-project-geometries!
       (environment/config-map {:api-url [:api-url]
                                :api-secret [:auth :jwt-secret]
                                :wfs-url [:road-registry :wfs-url]})
        [(d/pull db '[:db/id :thk.project/name
                      :thk.project/road-nr :thk.project/carriageway
                      :thk.project/start-m :thk.project/end-m
                      :thk.project/custom-start-m :thk.project/custom-end-m]
                 [:thk.project/id id])]))
    :ok))

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
  [db project-eid datasource-feature-ids link-attribute-kw]
  (let [current-entities-in-db (set
                                 (link-attribute-kw
                                   (d/pull db
                                           [link-attribute-kw]
                                           project-eid)))
        to-be-removed (set/difference current-entities-in-db datasource-feature-ids)
        to-be-added (set/difference datasource-feature-ids current-entities-in-db)]
    (into []
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
   :authorization {:project/update-info {:eid [:thk.project/id project-id]
                                          :link :thk.project/owner}}
   :transact (update-related-entities-tx db [:thk.project/id project-id] restrictions :thk.project/related-restrictions)})

(defcommand :thk.project/update-cadastral-units
  {:doc "Update project related cadastral-units"
   :context {:keys [user db]}
   :payload {:keys [cadastral-units project-id]}
   :project-id [:thk.project/id project-id]
   :authorization {:project/update-info {:eid [:thk.project/id project-id]
                                          :link :thk.project/owner}}
   :config {xroad-instance [:xroad :instance-id]
            xroad-url [:xroad :query-url]
            xroad-subsystem [:xroad :kr-subsystem-id]
            api-url [:api-url]
            api-secret [:auth :jwt-secret]}}
  (let [{db :db-after} (db-api/tx
                        (update-related-entities-tx db [:thk.project/id project-id]
                                                    cadastral-units
                                                    :thk.project/related-cadastral-units))]
    (future
      (let [current-cadastral-unit-ids (mapv first
                                             (d/q '[:find ?id :where [?project :thk.project/related-cadastral-units ?id]
                                                    :in $ ?project]
                                                  db [:thk.project/id project-id]))
            estate-ids (into #{}
                             (map (comp :KINNISTU val))
                         (postgrest/rpc {:api-url api-url :api-secret api-secret}
                                        :select_feature_properties
                                        {:ids current-cadastral-unit-ids
                                         :properties ["KINNISTU"]}))]
        (property-registry/ensure-cached-estate-info {:xroad-url xroad-url
                                                      :xroad-kr-subsystem-id xroad-subsystem
                                                      :instance-id xroad-instance
                                                      :requesting-eid (:user/person-id user)
                                                      :api-url api-url
                                                      :api-secret api-secret}
                                                     estate-ids)))
    :ok))

(defcommand :thk.project/add-permission
  {:doc "Add permission to project"
   :context {granting-user :user
             :keys [conn db]}
   :payload {:keys [project-id user role] :as payload}
   :spec (s/keys :req-un [::project-id])
   :project-id project-id
   :authorization {:project/edit-permissions {:link :thk.project/owner}}}
  (assert (authorization-check/role-can-be-granted? role) "Can't grant role")
  (let [user-exists? (:user/id user)
        user-already-added?
        (and user-exists?
             (boolean
               (seq
                 (permission-db/user-permission-for-project db [:user/id (:user/id user)] project-id))))]
    (if-not user-already-added?
      (let [tx [(merge
                  {:db/id (if user-exists?
                            [:user/id (:user/id user)]
                            "new-user")
                   :user/permissions
                   [(merge {:db/id "new-permission"
                            :permission/role role
                            :permission/projects project-id
                            :permission/valid-from (Date.)}
                           (creation-meta granting-user))]}
                  (when-not user-exists?
                    {:user/person-id (let [pid (:user/person-id user)]
                                       ;; Normalize estonian ids to start with "EE"
                                       (if (str/starts-with? pid "EE")
                                         pid
                                         (str "EE" pid)))}))]]
        (d/transact conn {:tx-data tx})
        {:success "User added successfully"})
      (db-api/fail!
        {:status 400
         :msg "User is already added"
         :error :permission-already-granted}))))

(defcommand :thk.project/add-search-geometry
  {:doc "Add a new geometry to use in the related restriction search"
   :context {:keys [user db]}
   :payload {geometry :geometry
             geometry-label :geometry-label
             id :thk.project/id}
   :spec (s/keys :req-un [::geometry])
   :project-id [:thk.project/id id]
   :authorization {:project/update-info {:eid [:thk.project/id id]
                                          :link :thk.project/owner}}
   :pre [(string? id)]}
  (let [config (environment/config-map {:api-url [:api-url]
                                        :api-secret [:auth :jwt-secret]})
        entity-id (:db/id (du/entity db [:thk.project/id id]))
        features [{:label geometry-label
                   :id (str (UUID/randomUUID))
                   :geometry geometry
                   :type "search-area"}]]
    (entity-features/upsert-entity-features! config entity-id features)))


(defcommand :thk.project/delete-search-geometry
  {:doc "Delete a single search geometry used in a project"
   :context {:keys [user]}
   :payload {entity-id :entity-id
             geometry-id :geometry-id}
   :spec (s/keys :req-un [::entity-id ::geometry-id])
   :project-id [:thk.project/id entity-id]
   :authorization {:project/update-info {:eid [:thk.project/id entity-id]
                                          :link :thk.project/owner}}
   :pre [(number? entity-id)
         (string? geometry-id)]}
  (let [config (environment/config-map {:api-url [:api-url]
                                        :api-secret [:auth :jwt-secret]})]
    (entity-features/delete-entity-feature! config entity-id geometry-id)))
