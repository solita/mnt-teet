(ns migrate
  (:require [clojure.string :as str])
  (:import (org.flywaydb.core Flyway)
           (java.sql DriverManager Statement ResultSet Connection))
  (:gen-class))

(Class/forName "org.postgresql.Driver")

(defn ensure-db [uri user pass]
  (try
    (with-open [conn (DriverManager/getConnection (str/replace uri "/teet" "/postgres") user pass)
                stmt (.createStatement conn)]
      (.execute stmt "CREATE DATABASE teet"))
    (catch Throwable e
      (let [msg (.getMessage e)]
        (when (not= msg "ERROR: database \"teet\" already exists")
          (println "Unable to create teet database: " e)
          (System/exit 1))))))

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
    (ensure-db uri user pass)
    (migrate uri user pass)
    (System/exit 0)))
