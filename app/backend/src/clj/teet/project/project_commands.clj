(ns teet.project.project-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand tx]]
            [datomic.client.api :as d]
            [teet.permission.permission-db :as permission-db]
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
            [teet.user.user-db :as user-db]
            [teet.user.user-model :as user-model]
            [teet.user.user-spec :as user-spec]
            [teet.integration.x-road.property-registry :as property-registry]
            [teet.integration.postgrest :as postgrest]
            [teet.integration.vektorio.vektorio-core :as vektorio])
  (:import (java.util Date UUID)))


(defn- geometry-update-attrs [db project-eid]
  (d/pull db [:thk.project/project-name
              :thk.project/custom-start-m
              :thk.project/custom-end-m]
          project-eid))

(defn maybe-update-vektorio-project-name? [db project-id project-name]
  (let [vektorio-enabled? (environment/feature-enabled? :vektorio)
        vektorio-config (environment/config-value :vektorio)]
      (if vektorio-enabled?
            (vektorio/update-project-in-vektorio! db vektorio-config project-id project-name)
            true)))

(defcommand :thk.project/update
  {:doc "Edit project basic info"
   :context {:keys [conn db user]}
   :payload {id :thk.project/id :as project-form}
   :project-id [:thk.project/id id]
   :authorization {:project/update-info {:eid [:thk.project/id id]
                                         :link :thk.project/owner}}}
  (try
    (if (some? (maybe-update-vektorio-project-name? db [:thk.project/id id] (:thk.project/project-name project-form)))
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
      (when (not= (geometry-update-attrs db-before [:thk.project/id id])
                  (geometry-update-attrs db [:thk.project/id id]))
        (project-geometry/update-project-geometries!
         (environment/config-map {:wfs-url [:road-registry :wfs-url]})
         [(d/pull db '[:db/id :integration/id
                       :thk.project/project-name :thk.project/name
                       :thk.project/road-nr :thk.project/carriageway
                       :thk.project/start-m :thk.project/end-m
                       :thk.project/custom-start-m :thk.project/custom-end-m]
                  [:thk.project/id id])]))
      :ok)
      (db-api/fail!
        {:status 400
         :msg "Vektor.io error"
         :error :vektorio-request-failed}))
       (catch Exception e
         (if
           (some? (get-in (ex-data e) [:vektorio-response :reason-phrase]))
           (db-api/fail!
             {:status 400
              :msg (str "Vektor.io error:" (get-in (ex-data e) [:vektorio-response :reason-phrase]))
              :error :vektorio-request-failed})
           (throw e)))))

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
   :pre [(:user/person-id user)
         (user-spec/estonian-person-id? (:user/person-id user))]
   :authorization {:project/edit-permissions {:link :thk.project/owner}}
   :audit? true}
  (assert (authorization-check/role-can-be-granted? role) "Can't grant role")
  (let [user-person-id (-> user
                           :user/person-id
                           user-model/normalize-person-id)
        user-info (user-db/user-info-by-person-id db user-person-id)
        user-already-added-to-project?
        (and user-info
             (boolean
               (seq
                (permission-db/user-permission-for-project db
                                                           user-info
                                                           project-id))))]
    (if-not user-already-added-to-project?
      (let [tx [(merge
                 (when-not user-info
                   (user-model/new-user user-person-id))
                 {:db/id (if user-info
                           (user-model/user-ref user-info)
                           "new-user")
                  :user/permissions
                  [(merge {:db/id "new-permission"
                           :permission/role role
                           :permission/projects project-id
                           :permission/valid-from (Date.)}
                          (creation-meta granting-user))]})]]
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
   :spec (s/keys :req [:thk.project/id]
                 :req-un [::geometry])
   :project-id [:thk.project/id id]
   :authorization {:project/update-info {:eid [:thk.project/id id]
                                          :link :thk.project/owner}}
   :pre [(string? id)]}
  (when-let [entity-id (project-db/thk-id->integration-id-number db id)]
    (let [config (environment/config-map {:api-url [:api-url]
                                         :api-secret [:auth :jwt-secret]})
          features [{:label geometry-label
                     :id (str (UUID/randomUUID))
                     :geometry geometry
                     :type "search-area"}]]
      (entity-features/upsert-entity-features! config entity-id features))))


(defcommand :thk.project/delete-search-geometry
  {:doc "Delete a single search geometry used in a project"
   :context {:keys [user db]}
   :payload {id :thk.project/id
             geometry-id :geometry-id}
   :spec (s/keys :req [:thk.project/id]
                 :req-un [::geometry-id])
   :project-id [:thk.project/id id]
   :authorization {:project/update-info {:eid [:thk.project/id id]
                                          :link :thk.project/owner}}
   :pre [(string? id)
         (string? geometry-id)]}
  (when-let [entity-id (project-db/thk-id->integration-id-number db id)]
    (let [config (environment/config-map {:api-url [:api-url]
                                         :api-secret [:auth :jwt-secret]})]
     (entity-features/delete-entity-feature! config entity-id geometry-id))))
