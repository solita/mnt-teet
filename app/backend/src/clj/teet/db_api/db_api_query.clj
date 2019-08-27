(ns teet.db-api.db-api-query
  "TEET Database API: query"
  (:require [datomic.client.api :as d]
            [teet.environment :as environment]
            [teet.transit :as transit]
            [teet.db-api.core :as db-api]

            ;; Require all namespaces that provide queries
            [teet.workflow.workflow-queries]))


(defn query-handler
  "Ring handler to invoke a named Datomic query.
  Takes input as transit, invokes the query and returns
  the result as transit."
  [req]
  (let [db (d/db (environment/datomic-connection))
        query-def (transit/transit-request req)
        query-result (db-api/query db query-def)
        result (if (and (map? query-result)
                        (contains? query-result :query)
                        (contains? query-result :args))
                 (d/q query-result)
                 query-result)]
    (transit/transit-response result)))
