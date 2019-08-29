(ns teet.db-api.db-api-handlers
  "TEET Database API: query and command handlers"
  (:require [datomic.client.api :as d]
            [teet.environment :as environment]
            [teet.transit :as transit]
            [teet.db-api.core :as db-api]

            ;; Require all namespaces that provide queries/commands
            [teet.workflow.workflow-queries]
            [teet.workflow.workflow-commands]))


(defn- request [handler-fn]
  (fn [req]
    (let [conn (environment/datomic-connection)
          ctx {:conn conn :db (d/db conn)}
          request-payload (transit/transit-request req)]
      (transit/transit-response (handler-fn ctx request-payload)))))

(def query-handler
  "Ring handler to invoke a named Datomic query.
  Takes input as transit, invokes the query and returns
  the result as transit."
  (request
   (fn [ctx query-def]
     (let [query-result (db-api/query ctx query-def)]
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
     (db-api/command! ctx command-def))))
