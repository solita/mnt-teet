(ns teet.db-api.db-api-handlers
  "TEET Database API: query and command handlers"
  (:require [datomic.client.api :as d]
            [teet.environment :as environment]
            [teet.transit :as transit]
            [teet.db-api.core :as db-api]
            [clojure.spec.alpha :as s]

            ;; Require all namespaces that provide queries/commands
            teet.authorization.authorization-queries
            teet.file.file-commands
            teet.file.file-queries
            teet.comment.comment-commands
            teet.comment.comment-queries
            teet.login.login-commands
            teet.enum.enum-queries
            teet.admin.admin-queries
            teet.admin.admin-commands
            teet.user.user-queries
            teet.logging.logging-commands
            teet.project.project-queries
            teet.project.project-commands
            teet.land.land-commands
            teet.land.land-queries
            teet.road.road-query-queries
            teet.system.system-queries
            teet.task.task-commands
            teet.activity.activity-commands
            teet.dashboard.dashboard-queries
            teet.notification.notification-queries
            teet.notification.notification-commands
            teet.meeting.meeting-queries
            teet.meeting.meeting-commands
            teet.meta.meta-commands
            teet.link.link-queries
            teet.link.link-commands

            [teet.log :as log]
            [teet.auth.jwt-token :as jwt-token]
            [clojure.string :as str]
            [teet.user.user-db :as user-db]))

(defn- jwt-token [req]
  (or
   (when-let [auth (get-in req [:headers "authorization"])]
     (when (str/starts-with? auth "Bearer ")
       (subs auth 7)))
   (get-in req [:params "t"])))

(defn- request [handler-fn]
  (fn [req]
    (try
      (let [user (some->> req jwt-token (jwt-token/verify-token
                                         (environment/config-value :auth :jwt-secret)))]
        (log/with-context
          {:user (str (:user/id user))}
          (let [conn (environment/datomic-connection)
                db (d/db conn)
                ctx {:conn conn
                     :db db
                     :user (merge user
                                  (when-let [user-id (:user/id user)]
                                    (user-db/user-info db [:user/id user-id])))
                     :session (:session req)}
                request-payload (transit/transit-request req)
                response (handler-fn ctx request-payload)]
            (case (:format (meta response))
              ;; If :format meta key is :raw, send output as ring response
              :raw response

              ;; Default to sending out transit response
              (transit/transit-response response)))))
      (catch Exception e
        (let [exd (ex-data e)
              status (:status exd)
              error (or (:error exd)
                        (:teet/error exd))]
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
                     (when (keyword? error)
                       {:headers {"X-TEET-Error" (name error)}})))))))))

(defn- check-spec [spec data]
  (if (nil? (s/get-spec spec))
    (do (log/warn "No spec for " spec ", every query and command should have a spec!")
        nil)
    (when-let [problems (s/explain-data spec data)]
      (log/debug "spec problems:" problems)
      (with-meta
        (merge
          {:status 422
           :body "Spec validation failed"} problems)
        {:format :raw}))))

(defn raw-query-handler [ctx {:keys [query args]}]
  (log/event :query {:request/name query})
  (log/metric query 1 :count)
  (or
   (check-spec query args)
   (let [ctx (assoc ctx :query/name query)
         query-result (db-api/query ctx args)]
     (if (and (map? query-result)
              (contains? query-result :query)
              (contains? query-result :args))
       (let [result-fn (or (:result-fn query-result) identity)]
         (result-fn (d/q (select-keys query-result [:query :args]))))
       query-result))))

(def query-handler
  "Ring handler to invoke a named Datomic query.
  Takes input as transit, invokes the query and returns
  the result as transit."
  (request raw-query-handler))

(defn raw-command-handler [ctx {:keys [command payload]}]
  (log/event :command {:request/name command})
  (log/metric command 1 :count)
  (or
   (check-spec command payload)
   (let [ctx (assoc ctx :command/name command)
         result (db-api/command! ctx payload)]
     (log/debug "command: " command ", payload: " payload ", result => " result)
     (if-let [error (:error result)]
       (with-meta
         error
         {:format :raw})
       result))))

(def command-handler
  "Ring handler to invoke a named Datomic query.
  Takes input as transit, invokes the query and returns
  the result as transit."
  (request raw-command-handler))
