(ns teet.gis.entity-features
  "Functions to interface with postgrest api for entity feature table"
  (:require [teet.auth.jwt-token :as jwt-token]
            [cheshire.core :as cheshire]
            [teet.log :as log]
            [org.httpkit.client :as client]))

(defn- api-request [{:keys [api-url api-secret]} request-fn url-path opts]
  (let [default-opts {:headers
                      {"Authorization" (str "Bearer " (jwt-token/create-backend-token api-secret))
                       "Content-Type" "application/json"}}
        url (str api-url url-path)]
    (log/info "PostgREST API call: " url)
    @(request-fn url (merge-with merge default-opts opts))))

(defn upsert-entity-features!
  [config entity-id features]
  (log/info "Upserting" (count features) "features for entity:" entity-id)
  (let [response (api-request
                  config client/post "/rpc/upsert_entity_feature"
                  {:body (cheshire/encode
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
    :ok))

(defn delete-entity-feature!
  "Uses postgrest endpoint to delete an entity feature."
  [config entity-id feature-id]
  (let [response (api-request
                  config client/delete "/entity_feature"
                  {:headers {"count" "exact"}
                   :query-params {"entity_id" (str "eq." entity-id)
                                  "id" (str "eq." feature-id)}})]
    (when (not= (:status response) 204)                     ;; Postgrest returns 204 even if it doesn't delete anything.
      (throw (ex-info "Entity deletion failed" {:response response
                                                :entity-id entity-id
                                                :feature-id feature-id})))
    :ok))

(defn entity-search-area-gml
  "Fetch GML representation of entity search area."
  [config entity-id distance]
  (let [{:keys [status body] :as response}
        (api-request config client/get "/rpc/gml_entity_search_area"
                     {:headers {"Accept" "text/plain"}
                      :query-params {"entity_id" entity-id
                                     "distance" distance}})]
    (when (not= status 200)
      (throw (ex-info "Failed to fetch entity search area"
                      {:response response
                       :entity-id entity-id
                       :distance distance})))
    body))
