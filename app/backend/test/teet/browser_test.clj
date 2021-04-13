(ns teet.browser-test
  (:require [clojure.java.io :as io]
            [datomic.client.api :as d]
            [teet.environment :as environment]
            [teet.main :as main]
            [teet.test.utils :as tu])
  (:import (java.util Date)))

(defn make-mock-users!
  [conn]
  (d/transact conn {:tx-data (into [] tu/mock-users)}))

(defn give-admin-permission
  [conn user-eid]
  (d/transact conn
              {:tx-data [{:db/id            user-eid
                          :user/permissions [{:db/id                 "new-permission"
                                              :permission/role       :admin
                                              :permission/valid-from (Date.)}]}]}))


(defn -main [& args]
  (let [config-file (first args)]
    (environment/load-local-config! (io/file config-file))
    (make-mock-users! (environment/datomic-connection))
    (give-admin-permission (environment/datomic-connection) [:user/id tu/manager-id])
    (main/restart (io/file config-file))))
