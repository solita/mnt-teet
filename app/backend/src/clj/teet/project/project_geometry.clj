(ns teet.project.project-geometry
  "Code for working with project geometries stored in PostgreSQL.
  Provides functions for calling the PostgREST API."
  (:require [org.httpkit.client :as client]
            [teet.auth.jwt-token :as jwt-token]
            [clojure.string :as str]
            [cheshire.core :as cheshire]
            [teet.road.road-query :as road-query]
            [teet.util.geo :as geo]))

(defn- valid-api-info? [{:keys [api-url api-shared-secret] :as api}]
  (and (not (str/blank? api-url))
       (not (str/blank? api-shared-secret))))

(defn update-project-geometries!
  "Update project geometries in PostgreSQL.
  Calls store_entity_info in PostgREST API."
  [{:keys [api-url api-shared-secret wfs-url] :as api} projects]
  {:pre [(valid-api-info? api)]}
  (let [road-part-cache (atom {})
        request-body (for [{id :db/id
                            :thk.project/keys [name road-nr carriageway
                                               start-m end-m
                                               custom-start-m custom-end-m]}
                           projects
                           :when (and (integer? (or custom-start-m start-m))
                                      (integer? (or custom-end-m end-m))
                                      (integer? road-nr)
                                      (integer? carriageway))]
                       {:id (str id)
                        :type "project"
                        :tooltip name
                        :geometry_wkt (geo/line-string-to-wkt
                                       (road-query/fetch-road-geometry {:wfs-url wfs-url
                                                                        :cache-atom road-part-cache}
                                                                       road-nr carriageway
                                                                       (or custom-start-m start-m)
                                                                       (or custom-end-m end-m)))})]
    (when (not-empty request-body)
      (let [response @(client/post
                       (str api-url "/rpc/store_entity_info")
                       {:headers {"Content-Type" "application/json"
                                  "Authorization"
                                  (str "Bearer "
                                       (jwt-token/create-backend-token api-shared-secret))}
                        :body (cheshire/encode request-body)})]
        (def the-response response)
        (when-not (= 200 (:status response))
          (throw (ex-info "Update project geometries failed"
                          {:expected-response-status 200
                           :actual-response-status (:status response)
                           :response response})))))
    projects))
