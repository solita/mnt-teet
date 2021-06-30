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

(defn -main [& args]
  (let [config-file (first args)]
    (environment/load-local-config! (io/file config-file))
    (user/make-mock-users!)
    (user/give-admin-permission [:user/id user/manager-uid])
    ;; fixme: following fails on a test fixture call
    (import-contracts-test-data! (environment/datomic-connection))
    (main/restart (io/file config-file))))
