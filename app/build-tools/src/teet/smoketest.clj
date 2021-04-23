(ns teet.smoketest
  (:require [datomic.client.api :as d]
    [clojure.pprint :as pp]
    [clojure.string :as str]))

(def today-date-formatted
  (let [date (java.util.Date.)]
    (.format (java.text.SimpleDateFormat. "yyyyMMdd") date)))

(def datomic-config
  (let [region "eu-central-1"
        system "teet-datomic"]
    {:server-type :cloud
     :region region
     :system system
     :query-group system
     :endpoint (str "http://entry." system "." region ".datomic.net:8182/")
     :proxy-port 8666}))

(def ^:dynamic *connection* nil)

(defn datomic-client []
  (d/client datomic-config))

(defn- ensure-database [client db-name]
  (let [existing-databases
        (into #{} (d/list-databases client {}))]
    (if (existing-databases db-name)
      :already-exists
      :not-found)))

(defn datomic-connection
  "Returns thread bound connection or creates a new one."
  [database-name]
  (or *connection*
    (let [db database-name
          client (datomic-client)
          db-status (ensure-database client db)
          conn (d/connect client {:db-name db})]
      (println "Using database: " db db-status)
      conn)))

(defn test-query
  "Query given DB to make sure it is working"
  [database-name]
  (d/q '[:find ?opinions
         :where [?opinions :land-owner-opinion/project _]]
    (d/db (datomic-connection database-name))))

(defn -main [& [database-name]]
  (println "Smoke test of restored db:" database-name)
  (try
    (let [results (test-query database-name)]
      (println "Query results:" results)
      (System/exit 0))
    (catch Exception e
      (println e)
      (System/exit 0))))
