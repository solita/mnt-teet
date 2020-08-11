(ns teet.integration.postgrest
  "Helpers for PostgREST calls"
  (:require [cheshire.core :as cheshire]
            [org.httpkit.client :as client]
            [teet.auth.jwt-token :as jwt-token]
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

(defn select
  "Select data from a table endpoint in PostgREST."
  ([ctx table] (select ctx table nil nil))
  ([ctx table columns where]
   {:pre [(valid-api-context? ctx)]}
   (let [resp @(client/get (str (:api-url ctx) "/" (name table))
                           {:query-params (merge {}
                                                 (when (seq columns)
                                                   {"select" (str/join "," (map name columns))})
                                                 (where-params where))
                            :headers (auth-header ctx)})]
     (cheshire/decode (:body resp) keyword))))

(defn delete!
  "Delete rows from table with the given where clause."
  [ctx table where]
  {:pre [(valid-api-context? ctx)
         (seq where)]}
  (let [resp @(client/delete (str (:api-url ctx) "/" (name table))
                             {:query-params (where-params where)
                              :headers (auth-header ctx)})]
    (cheshire/decode (:body resp) keyword)))

(defn upsert!
  "Upsert data to a table endpoint in PostgREST."
  [ctx table rows]
  {:pre [(valid-api-context? ctx)]}
  (let [resp @(client/post (str (:api-url ctx) "/" (name table))
                           {:headers (merge
                                      {"Content-Type" "application/json"
                                       "Prefer" "resolution=merge-duplicates"}
                                      (auth-header ctx))
                            :body (cheshire/encode rows)})]
    (= (:status resp) 201)))

(defn rpc
  "POST request an RPC by name, expects JSON response."
  [ctx rpc-name params]
  (let [resp @(client/post (str (:api-url ctx) "/rpc/" (name rpc-name))
                           {:headers (merge {"Content-Type" "application/json"}
                                            (auth-header ctx))
                            :body (cheshire/encode params)})]
    (cheshire/decode (:body resp) keyword)))
