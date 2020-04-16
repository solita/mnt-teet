(ns teet.test.utils
  (:require [datomic.client.api :as d]
            [teet.db-api.db-api-handlers :as db-api-handlers]
            [teet.environment :as environment]
            [teet.log :as log]
            [teet.user.user-model :as user-model]
            [teet.user.user-db :as user-db]
            [clojure.java.io :as io]))

;; Convenient shortcuts
(def manager-id #uuid "4c8ec140-4bd8-403b-866f-d2d5db9bdf74")
(def external-consultant-id #uuid "ccbedb7b-ab30-405c-b389-292cdfe85271")
(def internal-consultant-id #uuid "008af5b7-0f45-01ba-03d0-003c111c8f00")
(def boss-id #uuid "fa8af5b7-df45-41ba-93d0-603c543c880d")

(def mock-users
  [{:user/id manager-id
    :user/person-id "1234567890"
    :user/given-name "Danny D."
    :user/family-name "Manager"
    :user/email "danny.d.manager@example.com"
                                        ; :user/organization "Maanteeamet"
    }

   {:user/id external-consultant-id
    :user/person-id "3344556677"
    :user/given-name "Carla"
    :user/family-name "Consultant"
    :user/email "carla.consultant@example.com"
                                        ; :user/organization "ACME Road Consulting, Ltd."
    }

   {:user/id boss-id
    :user/person-id "9483726473"
    :user/given-name "Benjamin"
    :user/family-name "Boss"
    :user/email "benjamin.boss@example.com"
                                        ; :user/organization "Maanteeamet"
    }

   {:user/id internal-consultant-id
    :user/person-id "1233726123"
    :user/given-name "Edna E."
    :user/family-name "Consultant"
    :user/email "edna.e.consultant@example.com"
                                        ; :user/organization "Maanteeamet"
    }])

;;
;; Test fixtures
;;

(def ^:dynamic *connection* "Datomic connection during tests" nil)

(defn connection
  "Returns the current connection. Can only be called within tests using with-db fixture."
  []
  *connection*)

(defn db
  "Returns the current database. Can only be called within tests using with-db fixture."
  []
  (d/db (connection)))

(defn tx
  "Transact the given tx-data maps. Can only be called within tests using with-db fixture."
  [& tx-data]
  (d/transact (connection) {:tx-data (vec tx-data)}))

(defn with-environment [f]
  (log/info "Loading local config.")
  (environment/load-local-config!)
  (f)
  (log/info "Reloading local config.")
  (environment/load-local-config!))

(defn with-db
  ([] (with-db {}))
  ([{:keys [data-fixtures]
     :or {data-fixtures [:projects]} :as _opts}]
   (fn with-db-fixture [f]
     (log/redirect-ion-casts! :stderr)
     (let [test-db-name (str "test-db-" (System/currentTimeMillis))]
       (log/info "Creating database " test-db-name)
       (swap! environment/config assoc-in [:datomic :db-name] test-db-name)
       (let [client (d/client (environment/config-value :datomic :client))
             db-name {:db-name test-db-name}]
         (d/create-database client db-name)
         (binding [*connection* (d/connect client db-name)]
           (environment/migrate *connection*)
           (d/transact *connection* {:tx-data mock-users})
           (doseq [df data-fixtures
                   :let [resource (str "resources/" (name df) ".edn")]]
             (log/info "Transacting data fixture: " df)
             (d/transact *connection* {:tx-data (-> resource io/resource
                                                    slurp read-string)}))
           (f))
         (log/info "Deleting database " test-db-name)
         (d/delete-database client db-name))))))

;;
;; Database
;;

(def db-connection environment/datomic-connection)

(defn db []
  (d/db (db-connection)))


;;
;; Local login, queries, commands
;;
(def ^:private logged-in-user-ref (atom nil))

(defn logged-user []
  @logged-in-user-ref)

(defn local-login
  [user-ref]
  (reset! logged-in-user-ref user-ref)
  (log/info "Locally logged in as " user-ref))

(defn- action-ctx
  "A valid datomic reference must be obtainable from `user` with `user-model/user-ref`"
  [user]
  (let [db (db)
        user-ref (user-model/user-ref user)]
    {:conn (db-connection)
     :db db
     :user (when user-ref
             (user-db/user-info db user-ref))
     :session "foo"}))

(defn local-query
  ([query args]
   (if-let [user-id @logged-in-user-ref]
     (local-query user-id query args)
     (log/error "Not logged in! Call user/local-login with an existing user id to log in.")))

  ([user-ref query args]
   (db-api-handlers/raw-query-handler (action-ctx user-ref)
                                      {:args args
                                       :query query})))

(defn local-command
  ([command args]
   (if-let [user-id @logged-in-user-ref]
     (local-command user-id command args)
     (log/error "Not logged in! Call user/local-login with an existing user id to log in.")))

  ([user-id command args]
   (db-api-handlers/raw-command-handler (action-ctx user-id)
                                        {:payload args
                                         :command command})))
