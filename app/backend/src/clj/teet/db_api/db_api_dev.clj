(ns teet.db-api.db-api-dev
  (:require [compojure.core :refer [routes GET POST]]
            [teet.db-api.db-api-query :as db-api-query]))

(defn db-api-routes []
  (routes
   (POST "/query" req
         (db-api-query/query-handler req))
   (GET "/query" req
        (db-api-query/query-handler req))))
