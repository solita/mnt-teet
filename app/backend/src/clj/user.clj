(ns user
  (:require [datomic.client.api :as d]
            [teet.main :as main]
            [teet.environment :as environment]
            [teet.thk.thk-integration-ion :as thk-integration]))

(defn go []
  (main/restart)
  (environment/datomic-connection))

(def restart go)

(def db-connection environment/datomic-connection)

(defn db []
  (d/db (db-connection)))

(def q d/q)
(def pull d/pull)

(defn force-migrations!
  "Forces all migrations to rerun." ;; TODO: reload schema from environment to reload schema.edn
  []
  (environment/load-local-config!)
  (environment/migrate (db-connection) true))

;; TODO: Add function for importing projects to Datomic
;; See teet.thk.thk-import

(defn import-thk-from-local-file
  [filepath]
  (thk-integration/import-thk-local-file filepath))
