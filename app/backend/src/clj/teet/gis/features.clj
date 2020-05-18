(ns teet.gis.features
  "Utilities to fetch from PostgREST datasource/feature tables."
  (:require [org.httpkit.client :as client]
            [cheshire.core :as cheshire]
            [clojure.string :as str]
            [teet.auth.jwt-token :as jwt-token]))


(defn check-error [resp]
  (if-let [err (:error resp)]
    (throw (ex-info "PostGIS API error" {:error err}))
    resp))

(defn geojson-features-by-id [{:keys [api-url api-secret]} ids]
  (-> (str api-url "/rpc/geojson_features_by_id")
      (client/get
       {:query-params {"ids" (str "{" (str/join "," ids) "}")}
        :headers {"Accept" "text/plain"
                  "Authorization"
                  (str "Bearer "
                       (jwt-token/create-backend-token api-secret))}})
      deref
      check-error
      :body
      (cheshire/decode keyword)))
