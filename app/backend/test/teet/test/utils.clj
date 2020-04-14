(ns teet.test.utils
  (:require [datomic.client.api :as d]
            [teet.db-api.db-api-handlers :as db-api-handlers]
            [teet.environment :as environment]
            [teet.log :as log]
            [teet.user.user-db :as user-db]))

;;
;; Test fixtures
;;

(defn with-environment [f]
  (log/info "Loading local config.")
  (environment/load-local-config!)
  (f)
  (log/info "Reloading local config.")
  (environment/load-local-config!))

(defn with-db [f]
  (log/redirect-ion-casts! :stderr)
  (let [test-db-name (str "test-db-" (System/currentTimeMillis))]
    (log/info "Creating database " test-db-name)
    (swap! environment/config assoc-in [:datomic :db-name] test-db-name)
    (d/create-database (d/client (environment/config-value :datomic :client)) {:db-name test-db-name})
    (f)
    (log/info "Deleting database " test-db-name)
    (d/delete-database (d/client (environment/config-value :datomic :client)) {:db-name test-db-name})))

(defn with-test-data [f]
  ;; TODO: Initialize test db with some test data
  (f))

;;
;; Database
;;

(def db-connection environment/datomic-connection)

(defn db []
  (d/db (db-connection)))


;;
;; Local login, queries, commands
;;
(def ^:private logged-in-user-id (atom nil))

(defn logged-user []
  @logged-in-user-id)

(defn local-login
  [user-id]   
  (reset! logged-in-user-id user-id)
  (log/info "Locally logged in as " user-id))

(defn- action-ctx [user-id]
  (let [db (db)]
    {:conn (db-connection)
     :db db
     :user (when user-id
             (user-db/user-info db user-id))
     :session "foo"}))

(defn local-query
  ([query args]
   (if-let [user-id @logged-in-user-id]
     (local-query user-id query args)
     (log/error "Not logged in! Call user/local-login with an existing user id to log in.")))

  ([user-id query args]
   (db-api-handlers/raw-query-handler (action-ctx user-id)
                                      {:args args
                                       :query query})))

(defn local-command
  ([command args]
   (if-let [user-id @logged-in-user-id]
     (local-command user-id command args)
     (log/error "Not logged in! Call user/local-login with an existing user id to log in.")))

  ([user-id command args]
   (db-api-handlers/raw-command-handler (action-ctx user-id)
                                        {:payload args
                                         :command command})))
