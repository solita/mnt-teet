(ns teet.db-api.db-api-dev
  (:require [compojure.core :refer [routes GET POST]]
            [teet.db-api.db-api-handlers :as db-api-handlers]))

(defn db-api-routes []
  (routes
   (POST "/query" req
         (#'db-api-handlers/query-handler req))

   ;; FIXME: URL params are not handled currently
   (GET "/query" req
        (#'db-api-handlers/query-handler req))

   (POST "/command" req
         (#'db-api-handlers/command-handler req))))
