(ns teet.db-api.db-api-handlers
  "TEET Database API: query and command handlers"
  (:require [datomic.client.api :as d]
            [teet.environment :as environment]
            [teet.transit :as transit]
            [teet.db-api.core :as db-api]
            [clojure.spec.alpha :as s]

            ;; Require all namespaces that provide queries/commands
            teet.document.document-commands
            teet.document.document-queries
            teet.comment.comment-commands
            teet.login.login-commands
            teet.enum.enum-queries
            teet.admin.admin-queries
            teet.admin.admin-commands
            teet.user.user-queries
            teet.logging.logging-commands
            teet.project.project-queries
            teet.project.project-commands
            teet.road.road-query-queries

            [teet.log :as log]
            [teet.auth.jwt-token :as jwt-token]
            [clojure.string :as str]))

(defn- jwt-token [req]
  (or
   (when-let [auth (get-in req [:headers "authorization"])]
     (when (str/starts-with? auth "Bearer ")
       (subs auth 7)))
   (get-in req [:params "t"])))

(defn- request [handler-fn]
  (fn [req]
    (log/debug "REQUEST: " (pr-str req))
    (try
      (let [user (some->> req jwt-token (jwt-token/verify-token
                                         (environment/config-value :auth :jwt-secret)))]
        (log/with-context
          {:user (str (:user/id user))}
          (let [conn (environment/datomic-connection)
                ctx {:conn conn
                     :db (d/db conn)
                     :user user
                     :session (:session req)}
                request-payload (transit/transit-request req)
                response (handler-fn ctx request-payload)]
            (case (:format (meta response))
              ;; If :format meta key is :raw, send output as ring response
              :raw response

              ;; Default to sending out transit response
              (transit/transit-response response)))))
      (catch Exception e
        (let [{:keys [status error]} (ex-data e)]
          (case error
            ;; Return JWT verification failures (likely expired token) as 401 (same as PostgREST)
            :jwt-verification-failed
            (do
              (log/info "JWT verification failed: " (ex-data e))
              {:status 401
               :body "JWT verification failed"})

            ;; Log all other errors, but don't return exception info to client
            (do
              (log/error e "Exception in handler")
              (merge {:status (or status 500)
                      :body "Internal server error, see log for details"}
                     (when error
                       {:headers {"X-TEET-Error" (name error)}})))))))))

(defn- check-spec [spec data]
  (if (nil? (s/get-spec spec))
    (do (log/warn "No spec for " spec ", every query and command should have a spec!")
        nil)
    (when-let [problems (s/explain-data spec data)]
      (with-meta
        (merge
          {:status 422
           :body "Spec validation failed"} problems)
        {:format :raw}))))

(def query-handler
  "Ring handler to invoke a named Datomic query.
  Takes input as transit, invokes the query and returns
  the result as transit."
  (request
   (fn [ctx {:keys [query args]}]
     (log/event :query {:request/name query})
     (log/metric query 1 :count)
     (or
      (check-spec query args)
      (let [ctx (assoc ctx :query/name query)
            auth-ex (try
                      (db-api/query-authorization ctx args)
                      nil
                      (catch Exception e
                        (log/warn e "Query authorization failure")
                        e))]
        (if auth-ex
          ^{:format :raw}
          {:status 403
           :body "Query authorization failed"}
          (let [query-result (db-api/query ctx args)]
            (if (and (map? query-result)
                     (contains? query-result :query)
                     (contains? query-result :args))
              (let [result-fn (or (:result-fn query-result) identity)]
                (result-fn (d/q (select-keys query-result [:query :args]))))
              query-result))))))))

(def command-handler
  "Ring handler to invoke a named Datomic query.
  Takes input as transit, invokes the query and returns
  the result as transit."
  (request
   (fn [ctx {:keys [command payload]}]
     (log/event :command {:request/name command})
     (log/metric command 1 :count)
     (or
       (check-spec command payload)
       (let [ctx (assoc ctx :command/name command)
             auth-ex (try
                       (db-api/command-authorization ctx payload)
                       nil
                       (catch Exception e
                         (log/warn e "Command authorization failure")
                         e))]
         (if auth-ex
           ^{:format :raw}
           {:status 403
            :body "Command authorization failure"}
           (let [result (db-api/command! ctx payload)]
             (log/debug "command: " command ", payload: " payload ", result => " result)
             result)))))))
