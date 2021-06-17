(ns teet.contract-authorization)

(defn- write-authorization-edn-from-sheet! [sheet-path]
  (->> sheet-path
       (spit "../backend/resources/contract-authorization.edn")))

(defn -main [& [sheet-path]]
  (if sheet-path
    (do (write-authorization-edn-from-sheet! sheet-path)
        (println "Contract-authorization edn file successfully written!"))
    (println "Please provide contract-authorization sheet path as argument.")))
