(ns teet.db-api.db-api-ion
  "DB API Datomic Ion"
  (:require [teet.db-api.db-api-query :as db-api-query]
            [datomic.ion.lambda.api-gateway :as apigw]))

(def db-api-query (apigw/ionize db-api-query/query-handler))
