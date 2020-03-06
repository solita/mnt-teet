(ns teet.gis.entity-features
  "Functions to interface with postgrest api for entity feature table"
  (:require [teet.auth.jwt-token :as jwt-token]
            [cheshire.core :as cheshire]
            [teet.log :as log]
            [org.httpkit.client :as client]))

(defn upsert-entity-features!
  [{:keys [api-url api-secret]} entity-id features]
  (let [url (str api-url "/rpc/upsert_entity_feature")]
    (log/info "Upserting" (count features) "features for entity" entity-id ". URL: " api-url)
    (let [response @(client/post
                      url
                      {:headers {"Authorization" (str "Bearer " (jwt-token/create-backend-token api-secret))
                                 "Content-Type" "application/json"}
                       :body (cheshire/encode
                               (for [{:keys [geometry properties label type id]} features]
                                 {:entity (str entity-id)
                                  :id id

                                  ;; PENDING: what should we take from AGS for these?
                                  :label label
                                  :type type

                                  :geometry geometry
                                  :properties properties}))})]
      (when (not= (:status response) 200)
        (throw (ex-info "entity info upsert failed" {:response response
                                                     :entity-id entity-id
                                                     :features features})))
      :ok)))

(defn delete-entity-feature!
  "Uses postgrest endpoint to delete an entity feature."
  [{:keys [api-url api-secret]} entity-id feature-id]
  (let [url (str api-url "/entity_feature")
        response @(client/delete
                    url
                    {:headers {"Authorization" (str "Bearer " (jwt-token/create-backend-token api-secret))
                               "Content-Type" "application/json"
                               "count" "exact"}
                     :query-params {"entity_id" (str "eq." entity-id)
                                    "id" (str "eq." feature-id)}})]
    (when (not= (:status response) 204)                     ;; Postgrest returns 204 even if it doesn't delete anything.
      (throw (ex-info "Entity deletion failed" {:response response
                                                :entity-id entity-id
                                                :feature-id feature-id})))
    :ok))

