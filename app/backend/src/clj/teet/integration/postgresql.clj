(ns teet.integration.postgresql
  "Helpers for PostgreSQL"
  (:require [clojure.java.jdbc :as jdbc]
            [teet.environment :as environment]))

(defmacro with-connection
  "Run `body` with `db-sym` bound to a connection. The connection
  will be closed (returned to pool) after body has returned."
  [db-sym & body]
  `(environment/call-with-pg-connection
    (fn [~db-sym]
      ~@body)))

(defmacro with-transaction
  "Start or join current db transaction. Runs `body` with `db-sym`
  bound to a connection that is in transaction."
  [db-sym & body]
  `(jdbc/with-db-transaction [~db-sym ~db-sym]
     ~@body))
