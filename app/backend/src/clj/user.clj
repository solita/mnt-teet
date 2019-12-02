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

(defn tx
  "Transact given maps to db"
  [& maps]
  (d/transact (environment/datomic-connection)
              {:tx-data (into [] maps)}))

(def mock-users [{:user/id #uuid "4c8ec140-4bd8-403b-866f-d2d5db9bdf74"
                  :user/person-id "1234567890"
                  :user/given-name "Danny D."
                  :user/family-name "Manager"
                  :user/email "danny.d.manager@example.com"
                  ; :user/organization "Maanteeamet"
                  }

                 {:user/id #uuid "ccbedb7b-ab30-405c-b389-292cdfe85271"
                  :user/person-id "3344556677"
                  :user/given-name "Carla"
                  :user/family-name "Consultant"
                  :user/email "carla.consultant@example.com"
                  ; :user/organization "ACME Road Consulting, Ltd."
                  }

                 {:user/id #uuid "fa8af5b7-df45-41ba-93d0-603c543c880d"
                  :user/person-id "9483726473"
                  :user/given-name "Benjamin"
                  :user/family-name "Boss"
                  :user/email "benjamin.boss@example.com"
                  ; :user/organization "Maanteeamet"
                  }])

(defn make-mock-users!
  []
  (apply tx mock-users))

(defn delete-db
  [db-name]
  (d/delete-database (environment/datomic-client) {:db-name db-name}))

(defn create-db
  [db-name]
  (d/create-database (environment/datomic-client) {:db-name db-name}))

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
