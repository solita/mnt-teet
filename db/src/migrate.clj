(ns migrate
  (:require [clojure.string :as str])
  (:import (org.flywaydb.core Flyway))
  (:gen-class))

(Class/forName "org.postgresql.Driver")

(defn migrate [uri user pass]
  (println "Migrate DB: " uri)
  (try
    (-> (Flyway/configure)
        (.dataSource uri user pass)
        .load
        .migrate)
    (catch Throwable t
      (println "ERROR in migration: " t)
      (System/exit 1))))

(defn -main [& args]
  (let [uri (System/getenv "DB_URI")
        user (System/getenv "DB_USER")
        pass (System/getenv "DB_PASSWORD")]
    (when (some str/blank? [uri user pass])
      (println "Specify DB_URI, DB_USER and DB_PASSWORD environment variables!")
      (System/exit 1))
    (migrate uri user pass)
    (System/exit 0)))
