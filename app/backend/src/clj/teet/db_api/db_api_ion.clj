(ns teet.db-api.db-api-ion
  "DB API Datomic Ion"
  (:require [teet.db-api.db-api-handlers :as db-api-handlers]
            [datomic.ion.lambda.api-gateway :as apigw]
            [datomic.ion :as ion]
            [teet.environment :as environment]))

(def db-api-query (apigw/ionize db-api-handlers/query-handler))
(def db-api-command (apigw/ionize db-api-handlers/command-handler))

(environment/init-ion-config! (ion/get-env))
