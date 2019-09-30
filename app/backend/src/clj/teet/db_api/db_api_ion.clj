(ns teet.db-api.db-api-ion
  "DB API Datomic Ion"
  (:require [teet.db-api.db-api-handlers :as db-api-handlers]
            [datomic.ion.lambda.api-gateway :as apigw]
            [datomic.ion :as ion]
            [teet.environment :as environment]
            [taoensso.timbre :as log]
            [taoensso.timbre :as timbre]
            [datomic.ion.cast :as cast]))

(def db-api-query (apigw/ionize db-api-handlers/query-handler))
(def db-api-command (apigw/ionize db-api-handlers/command-handler))

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
