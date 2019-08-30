(ns teet.db-api.db-api-handlers
  "TEET Database API: query and command handlers"
  (:require [datomic.client.api :as d]
            [teet.environment :as environment]
            [teet.transit :as transit]
            [teet.db-api.core :as db-api]
            [clojure.spec.alpha :as s]

            ;; Require all namespaces that provide queries/commands
            [teet.workflow.workflow-queries]
            [teet.workflow.workflow-commands]
            [taoensso.timbre :as log]))


(defn- request [handler-fn]
  (fn [req]
    (let [conn (environment/datomic-connection)
          ctx {:conn conn :db (d/db conn)}
          request-payload (transit/transit-request req)]
      (transit/transit-response (handler-fn ctx request-payload)))))

(defn- check-spec [spec data]
  (if (nil? (s/get-spec spec))
    (do (log/warn "No spec for " spec ", every query and command should have a spec!")
        nil)
    (when-let [problems (s/explain-data spec data)]
      (merge {:error "Spec validation failed"} problems))))

(def query-handler
  "Ring handler to invoke a named Datomic query.
  Takes input as transit, invokes the query and returns
  the result as transit."
  (request
   (fn [ctx query-def]
     (let [query-result (or (check-spec (:query/name query-def) query-def)
                            (db-api/query ctx query-def))]
       (if (and (map? query-result)
                (contains? query-result :query)
                (contains? query-result :args))
         (d/q query-result)
         query-result)))))

(def command-handler
  "Ring handler to invoke a named Datomic query.
  Takes input as transit, invokes the query and returns
  the result as transit."
  (request
   (fn [ctx command-def]
     (or (check-spec (:command/name command-def) command-def)
         (db-api/command! ctx command-def)))))
