(ns teet.db-api.db-api-ion
  "DB API Datomic Ion"
  (:require [teet.db-api.db-api-handlers :as db-api-handlers]
            [datomic.ion.lambda.api-gateway :as apigw]
            [datomic.ion :as ion]
            [teet.environment :as environment]
            [ring.middleware.params :as params]
            [ring.middleware.cookies :as cookies]
            [ring.middleware.session :as session]
            [ring.middleware.session.cookie :as session-cookie]
            [teet.log :as log]
            [tara.routes :as tara-routes]
            [tara.endpoint :as tara-endpoint]))

(some-> (ion/get-env) environment/init-ion-config!)

(def cookie-store (session-cookie/cookie-store
                   {:key (environment/config-value :session-cookie-key)}))

(defn- wrap-middleware [handler]
  (-> handler
      params/wrap-params
      cookies/wrap-cookies
      (session/wrap-session {:cookie-name "teet-session"
                             :store cookie-store})))

(defn ring->ion [handler]
  (-> handler wrap-middleware apigw/ionize))

(def db-api-query (ring->ion db-api-handlers/query-handler))
(def db-api-command (ring->ion db-api-handlers/command-handler))
(def tara-login (ring->ion (tara-routes/tara-routes
                            (tara-endpoint/discover (environment/config-value :tara :endpoint-url))
                            (merge
                             (environment/config-value :tara)
                             {:on-success login-commands/on-tara-login}))))

(log/enable-ion-cast-appender!)
