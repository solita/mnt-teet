(ns teet.integration.postgrest
  "Helpers for PostgREST calls"
  (:require [cheshire.core :as cheshire]
            [org.httpkit.client :as http]
            [teet.auth.jwt-token :as jwt-token]
            [teet.db-api.core :refer [fail!]]
            [teet.log :as log]
            [clojure.string :as str]))

(defn valid-api-context? [ctx]
  (and (map? ctx)
       (contains? ctx :api-url)
       (contains? ctx :api-secret)))

(defn- auth-header [ctx]
  {:pre [(valid-api-context? ctx)]}
  {"Authorization" (str "Bearer " (jwt-token/create-backend-token (:api-secret ctx)))})

(def where-op {:= #(str "eq." %)
               :< #(str "lt." %)
               :<= #(str "lte." %)
               :> #(str "gt." %)
               :>= #(str "gte." %)
               :in #(str "in.(" (str/join ","
                                          (map (fn [x]
                                                 (str "\"" x "\""))
                                               %)) ")")})

(defn- where-params [where]
  (into {}
        (map (fn [[key val]]
               (if (vector? val)
                 [(name key) ((where-op (first val))
                              (second val))]
                 [(name key) (str "eq." val)])))
        where))

(defn- decode-response-body [resp]
  (try (cheshire/decode (:body resp) keyword)
       (catch Exception _
         (log/error "Failed to decode PostgREST response: " (:body resp))
         (fail! {:msg "Failed to decode PostgREST response"
                 :error :postgrest-response-error}))))

(defn select
  "Select data from a table endpoint in PostgREST."
  ([ctx table] (select ctx table nil nil))
  ([ctx table columns where]
   {:pre [(valid-api-context? ctx)]}
   (let [resp @(http/get (str (:api-url ctx) "/" (name table))
                           {:query-params (merge {}
                                                 (when (seq columns)
                                                   {"select" (str/join "," (map name columns))})
                                                 (where-params where))
                            :headers (auth-header ctx)})]
     (decode-response-body resp))))

(defn delete!
  "Delete rows from table with the given where clause."
  [ctx table where]
  {:pre [(valid-api-context? ctx)
         (seq where)]}
  (let [resp @(http/delete (str (:api-url ctx) "/" (name table))
                             {:query-params (where-params where)
                              :headers (auth-header ctx)})]
    (decode-response-body resp)))

(defn upsert!
  "Upsert data to a table endpoint in PostgREST."
  [ctx table rows]
  {:pre [(valid-api-context? ctx)]}
  (let [resp @(http/post (str (:api-url ctx) "/" (name table))
                           {:headers (merge
                                      {"Content-Type" "application/json"
                                       "Prefer" "resolution=merge-duplicates"}
                                      (auth-header ctx))
                            :body (cheshire/encode rows)})]
    (= (:status resp) 201)))

(defn rpc
  "POST request an RPC by name, expects JSON response."
  [ctx rpc-name params]
  (let [resp @(http/post (str (:api-url ctx) "/rpc/" (name rpc-name))
                           {:headers (merge {"Content-Type" "application/json"}
                                            (auth-header ctx))
                            :body (cheshire/encode params)})]
    (if
      (or
        (= (:status resp) 401)
        (= (:status resp) 503))
      (throw (ex-info "Error posting a PostgREST request"
                      {:rpc-name rpc-name
                       :request-params params
                       :error-response (cheshire/decode (:body resp))
                       }))
      (decode-response-body resp))))
