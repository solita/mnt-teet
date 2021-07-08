(ns teet.browser-test
  (:require [teet.environment :as environment]
            [teet.thk.thk-import :as thk-import]
            [teet.thk.thk-mapping :as thk-mapping]
            [teet.main :as main]
            [clojure.java.io :as io]))

(defn import-contracts-test-data!
  [conn]
  (let [csv-data (.getBytes (slurp "test/teet/thk/thk-test-data.csv"))
        contracts
        (thk-import/parse-thk-export-csv
         {:input (java.io.ByteArrayInputStream.
                  csv-data)
          :column-mapping thk-mapping/thk->teet-contract
          :group-by-fn (fn [val]
                         (select-keys val [:thk.contract/procurement-part-id
                                           :thk.contract/procurement-id]))})
        import-contract-result (thk-import/import-thk-contracts! conn "test://test-csv" contracts)]
    import-contract-result))

(defn import-tasks-test-data!
  [conn]
  (let [csv-data  (.getBytes (slurp "test/teet/thk/thk-test-data.csv"))
        projects
        (thk-import/parse-thk-export-csv
         {:input (java.io.ByteArrayInputStream.
                  csv-data)
          :column-mapping thk-mapping/thk->teet-project
          :group-by-fn #(get % :thk.project/id)})
        import-tasks-result (thk-import/import-thk-tasks! conn "test://test-csv" projects)]
    ;; (println "projects was" projects)
    import-tasks-result))

(defn import-projects-test-data! [conn]
  (let [csv-data  (.getBytes (slurp "test/teet/thk/thk-test-data.csv"))
        projects
        (thk-import/parse-thk-export-csv
         {:input (java.io.ByteArrayInputStream.
                  csv-data)
          :column-mapping thk-mapping/thk->teet-project
          :group-by-fn #(get % :thk.project/id)})
        import-projects-result (thk-import/import-thk-projects! conn "test://test-csv" projects)]
    import-projects-result))

(defn import-test-data! []
  (import-projects-test-data! (environment/datomic-connection))

  ;; tasks import doesn't seem to yield tasks,
  ;; but not needed either for contracts to appear.
  ;; should investigate if we want to get tasks in browser tests too
  (import-tasks-test-data! (environment/datomic-connection))

  (import-contracts-test-data! (environment/datomic-connection)))

(defn -main [& args]
  (let [config-file (first args)]
    (environment/load-local-config! (io/file config-file))
    (user/make-mock-users!)
    (user/give-admin-permission [:user/id user/boss-uid])

    (import-test-data!)

    (main/restart (io/file config-file))))
