(ns teet.util.thk-import-cli
  (:require [teet.thk.thk-integration-ion :as thk-integration]
            [teet.environment :as environment]))

(defn -main
  [thk-csv-file]
  (println "hello m")
  (environment/load-local-config!)
  (environment/datomic-connection)
  (thk-integration/import-thk-local-file thk-csv-file))
