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
            [teet.document.document-commands]
            [teet.document.document-queries]

            [taoensso.timbre :as log]))


(defn- request [handler-fn]
  (fn [req]
    (let [conn (environment/datomic-connection)
          ctx {:conn conn :db (d/db conn)}
          request-payload (transit/transit-request req)
          response (handler-fn ctx request-payload)]
      (case (:format (meta response))
        ;; If :format meta key is :raw, send output as ring response
        :raw response

        ;; Default to sending out transit response
        (transit/transit-response response)))))

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
   (fn [ctx {:keys [query args]}]
     (let [query-result (or (check-spec query args)
                            (db-api/query (assoc ctx :query/name query) args))]
       (if (and (map? query-result)
                (contains? query-result :query)
                (contains? query-result :args))
         (let [result-fn (or (:result-fn query-result) identity)]
           (result-fn (d/q (select-keys query-result [:query :args]))))
         query-result)))))

(def command-handler
  "Ring handler to invoke a named Datomic query.
  Takes input as transit, invokes the query and returns
  the result as transit."
  (request
   (fn [ctx {:keys [command payload]}]
     (let [result
           (or (check-spec command payload)
               (db-api/command! (assoc ctx :command/name command) payload))]
       (log/debug "command: " command ", payload: " payload ", result => " result)
       result))))
