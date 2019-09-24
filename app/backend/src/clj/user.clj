(ns user
  (:require [datomic.client.api :as d]
            [teet.main :as main]
            [teet.environment :as environment]))

(defn go []
  (main/restart)
  (environment/datomic-connection))

(def restart go)

(def db-connection environment/datomic-connection)

(defn db []
  (d/db (db-connection)))

(def q d/q)

(defn force-migrations! []
  "Forces all migrations to rerun."
  (environment/load-local-config!)
  (environment/migrate (db-connection) true))
