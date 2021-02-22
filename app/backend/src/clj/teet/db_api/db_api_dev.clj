(ns teet.db-api.db-api-dev
  (:require [compojure.core :refer [routes GET POST]]
            [teet.db-api.db-api-handlers :as db-api-handlers]
            [teet.util.datomic :as du]
            [datomic.client.api :as d]))

(defmacro with-query-checking [& body]
  `(with-redefs [d/q (fn [& args#]
                       (apply du/q args#))]
     ~@body))

(defn db-api-routes []
  (routes
   (POST "/query/" req
     (with-query-checking
       (#'db-api-handlers/query-handler req)))

   ;; FIXME: URL params are not handled currently
   (GET "/query/" req
     (with-query-checking
       (#'db-api-handlers/query-handler req)))

   (POST "/command/" req
     (with-query-checking
       (#'db-api-handlers/command-handler req)))))
