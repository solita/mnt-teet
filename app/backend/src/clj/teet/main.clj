(ns teet.main
  (:require [org.httpkit.server :as server]
            [teet.routes :as routes]
            [tara.routes :as tara-routes]
            [tara.endpoint :as tara-endpoint]
            [compojure.core :refer [routes]]
            [ring.middleware.params :as params]
            [ring.middleware.cookies :as cookies]
            [ring.middleware.session :as session]
            [ring.middleware.session.cookie :as session-cookie]
            [teet.login.login-tara-token :as login-tara-token]
            [teet.login.login-fake-routes :as login-fake-routes]
            [teet.db-api.db-api-dev :as db-api-dev]
            [teet.log :as log]
            [teet.environment :as environment]
            [clojure.java.io :as io])
  (:gen-class))

(def server nil)

(defn start [{:keys [port tara mode] :as config}]
  (environment/log-timezone-config!)
  (alter-var-root
   #'server
   (fn [_]
     ;; Redirecting to :stdout results in StackOverflowError
     (when (= mode :dev)
       (log/redirect-ion-casts! :stderr))
     (log/info "Starting TEET service in port " port)
     (-> (routes
          (if tara
            (tara-routes/tara-routes (tara-endpoint/discover (:endpoint-url tara))
                                     (merge tara
                                            {:scopes ["openid" "email"]
                                             :on-error login-tara-token/tara-error-handler
                                             :on-success (partial login-tara-token/tara-success-handler
                                                                  (:base-url tara))}))
            (do
              (log/info "No TARA configuration present, using fake login.")
              (login-fake-routes/fake-login-routes)))
          (db-api-dev/db-api-routes)
          (routes/teet-routes config))
         params/wrap-params
         cookies/wrap-cookies
         (session/wrap-session {:store (session-cookie/cookie-store {:key (.getBytes "FIXME:USE PARAMS")})})
         (server/run-server {:ip "0.0.0.0"
                             :port port})))))

(defn stop []
  (server))

(defn restart
  ([]
   (restart (io/file ".." ".." ".." "mnt-teet-private" "config.edn")))
  ([config-file]
   (environment/load-local-config! config-file)
   (when server
     (stop))
   ;; Dummy config for local testing use
   (start {:mode :dev
           :port 4000
           :api {:shared-secret "secret1234567890secret1234567890"
                 :role "teet_user"
                 :url "http://localhost:3000"}})))
