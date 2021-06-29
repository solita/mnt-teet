(ns teet.browser-test
  (:require [teet.environment :as environment]
            [teet.thk.thk-import-test :as tit]
            [teet.main :as main]
            [clojure.java.io :as io]))

(defn -main [& args]
  (let [config-file (first args)]
    (environment/load-local-config! (io/file config-file))
    (user/make-mock-users!)
    (user/give-admin-permission [:user/id user/manager-uid])
    ;; fixme: following fails on a test fixture call
    (tit/import-contracts-csv! (.getBytes (slurp "test/teet/thk/thk-test-data.csv")) (environment/datomic-connection))
    (main/restart (io/file config-file))))
