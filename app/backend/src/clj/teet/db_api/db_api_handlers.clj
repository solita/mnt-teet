(ns teet.db-api.db-api-handlers
  "TEET Database API: query and command handlers"
  (:require [datomic.client.api :as d]
            [teet.environment :as environment]
            [teet.transit :as transit]
            [teet.db-api.core :as db-api]
            [clojure.spec.alpha :as s]

            ;; Require all namespaces that provide queries/commands
            teet.authorization.authorization-queries
            teet.contract.contracts-queries
            teet.contract.contract-queries
            teet.contract.contract-commands
            teet.company.company-queries
            teet.file.file-commands
            teet.file.file-queries
            teet.comment.comment-commands
            teet.comment.comment-queries
            teet.login.login-commands
            teet.enum.enum-queries
            teet.admin.admin-queries
            teet.admin.admin-commands
            teet.user.user-queries
            teet.account.account-commands
            teet.account.account-queries
            teet.logging.logging-commands
            teet.project.project-queries
            teet.project.project-commands
            teet.land.land-commands
            teet.land.land-queries
            teet.land.owner-opinion-commands
            teet.land.owner-opinion-queries
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
            teet.cooperation.cooperation-queries
            teet.cooperation.cooperation-commands
            teet.asset.asset-queries
            teet.asset.asset-commands
            teet.integration.vektorio.vektorio-queries

            [teet.log :as log]
            [teet.auth.jwt-token :as jwt-token]
            [clojure.string :as str]
            [teet.user.user-db :as user-db]
            [ring.middleware.not-modified :as not-modified]
            [teet.util.url :as url]))

(defn- jwt-token [req]
  (or
   (when-let [auth (get-in req [:headers "authorization"])]
     (when (str/starts-with? auth "Bearer ")
       (subs auth 7)))
   (get-in req [:params "t"])))

(defn- deref-delay [x]
  (if (delay? x) @x x))

;; Context map that can have "lazy" values that are only realized
;; if they are looked up
(deftype ContextMap [m]
  clojure.lang.ILookup
  (valAt [_ k]
    (deref-delay (get m k)))
  (valAt [_ k default-value]
    (deref-delay (get m k default-value))))

(defn- error-message-header [msg]
  (when msg
    {"X-TEET-Error-Message"
     (url/js-style-url-encode msg)}))

(defn- request [handler-fn]
  (not-modified/wrap-not-modified
   (fn [req]
     (let [cleanup (atom [])]
       (try
         (let [user (some->> req jwt-token (jwt-token/verify-token
                                            (environment/config-value :auth :jwt-secret)))]
           (log/with-context
             {:user (str (:user/id user))}
             (let [conn (environment/datomic-connection)
                   db (d/db conn)
                   aconn (delay (environment/asset-connection))
                   pg (delay
                        (let [c (environment/get-pg-connection)]
                          (swap! cleanup conj
                                 #(do
                                    (log/debug "Closing PostgreSQL connection" c)
                                    (.close c)))
                          {:connection c}))
                   ctx {:conn conn
                        :db db
                        :asset-conn aconn
                        :asset-db (delay (d/db @aconn))
                        :user (merge user
                                     (when-let [user-id (:user/id user)]
                                       (user-db/user-info db [:user/id user-id])))
                        :session (:session req)
                        :headers (:headers req)}
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
                           (:teet/error exd))
                 error-message (:teet/error-message exd)]
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
                          {:headers (merge {"X-TEET-Error" (name error)}
                                           (error-message-header error-message))}))))))
         (finally
           ;; Run any registered cleanup functions
           (doseq [cleanup @cleanup]
              (cleanup))))))))

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
         _ (log/info "Query " query
                     ", user: " (get-in ctx [:user :db/id])
                     ", args: " args)
         query-result (db-api/query (ContextMap. ctx) args)]
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
         _ (log/info "Command: " command
                     ", user: " (get-in ctx [:user :db/id])
                     ", payload: " payload)
         result (db-api/command! (ContextMap. ctx) payload)]
     (log/debug "  " command " result => " result)

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
