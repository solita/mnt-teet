(ns teet.main
  (:require [ring.adapter.jetty :as jetty]
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

(defn wrap-nil [handler]
   (fn [request]
    (let [rv (handler request)]
       (or rv {:status 404 :headers {"Content-Type" "text/plain"} :body "Not found\n"}))))

(defn start [{:keys [http-port https-port tara mode] :as config}]
  (environment/log-timezone-config!)
  (alter-var-root
   #'server
   (fn [_]
     ;; Redirecting to :stdout results in StackOverflowError
     (when (= mode :dev)
       (log/redirect-ion-casts! :stderr))
     (log/info "Starting TEET service in ports " http-port https-port)
     (-> (routes
          (if tara
            (tara-routes/tara-routes #(tara-endpoint/discover (:endpoint-url tara))
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
         wrap-nil
         params/wrap-params
         cookies/wrap-cookies
         (session/wrap-session {:store (session-cookie/cookie-store {:key (.getBytes "FIXME:USE PARAMS")})})
         (jetty/run-jetty (merge
                           {:http? true :port http-port :join? false
                            :host (:listen-address config)}
                           (if (-> "../../../teet.keystore" io/file .exists)
                             (do
                               (log/info "found keystore, starting tls sever too")
                               {:ssl? true :ssl-port https-port 
                                :keystore "../../../teet.keystore" :key-password "dummypass"})
                             (do
                               (log/info "keystore not found, not listening on tls port")
                               {}))))))))

(defn stop []
  (.stop server))

(defn restart
  ([]
   (restart (io/file ".." ".." ".." "mnt-teet-private" "config.edn")))
  ([config-file]
   (environment/load-local-config! config-file)
   (when server
     (stop))
   ;; Dummy config for local testing use
   (start {:mode :dev
           :http-port 4000
           :https-port 4443
           :listen-address (or (environment/config-value :listen-address)
                               "127.0.0.1")
           :api {:shared-secret (or (environment/config-value :auth :jwt-secret)
                                    "secret1234567890secret1234567890")
                 :role "teet_user"
                 :url "http://localhost:3000"}})))
