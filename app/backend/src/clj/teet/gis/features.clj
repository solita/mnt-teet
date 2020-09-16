(ns teet.gis.features
  "Utilities to fetch from PostgREST datasource/feature tables."
  (:require [org.httpkit.client :as client]
            [cheshire.core :as cheshire]
            [teet.auth.jwt-token :as jwt-token]))


(defn check-error [resp]
  (if-let [err (:error resp)]
    (throw (ex-info "PostGIS API error" {:error err}))
    resp))

(defn geojson-features-by-id [{:keys [api-url api-secret]} ids]
  (-> (str api-url "/rpc/geojson_features_by_id")
      (client/post
       {:body (cheshire/encode {"ids" (vec ids)})
        :headers {"Accept" "text/plain"
                  "Authorization"
                  (str "Bearer "
                       (jwt-token/create-backend-token api-secret))}})
      deref
      check-error
      :body
      (cheshire/decode keyword)))
