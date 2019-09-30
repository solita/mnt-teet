(ns teet.db-api.db-api-ion
  "DB API Datomic Ion"
  (:require [teet.db-api.db-api-handlers :as db-api-handlers]
            [datomic.ion.lambda.api-gateway :as apigw]
            [datomic.ion :as ion]
            [teet.environment :as environment]
            [taoensso.timbre :as log]
            [taoensso.timbre :as timbre]
            [datomic.ion.cast :as cast]
            [ring.middleware.params :as params]
            [ring.middleware.cookies :as cookies]
            [ring.middleware.session :as session]))

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

(defn- ion-appender [{:keys [level _output]}]
  ((case level
     (:error :warn :fatal) cast/alert
     cast/dev)
   {:msg (force _output)}))

(defn enable-timbre-appender! []
  (log/merge-config!
   {:appenders {:println {:enabled? false}
                :ion {:enabled? true
                      :fn ion-appender}}}))

(enable-timbre-appender!)
