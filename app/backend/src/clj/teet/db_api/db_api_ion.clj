(ns teet.db-api.db-api-ion
  "DB API Datomic Ion"
  (:require [teet.db-api.db-api-handlers :as db-api-handlers]
            [datomic.ion.lambda.api-gateway :as apigw]
            [datomic.ion :as ion]
            [teet.environment :as environment]
            [teet.log :as log]
            [ring.middleware.params :as params]
            [ring.middleware.cookies :as cookies]))

(defn- wrap-middleware [handler]
  (-> handler
      params/wrap-params
      cookies/wrap-cookies
      ;; FIXME: we don't need session yet (with TARA login, add it)
      ))

(def db-api-query (-> db-api-handlers/query-handler
                      wrap-middleware
                      apigw/ionize))
(def db-api-command (-> db-api-handlers/command-handler
                        wrap-middleware
                        apigw/ionize))

(environment/init-ion-config! (ion/get-env))

(log/enable-timbre-appender!)
