(ns teet.migration.project-region
  (:require [datomic.client.api :as d]))

(defn extract-region-from-integration-info [conn]
  (let [projects (d/q '[:find ?p ?ii
                        :where [?p :thk.project/integration-info ?ii]]
                      (d/db conn))
        parse #(binding [*read-eval* false]
                 (read-string %))]

    (d/transact
     conn
     {:tx-data (for [[project-id integration-info] projects
                     :let [info (parse integration-info)
                           region-name (:object/regionname info)]
                     :when region-name]
                 {:db/id project-id
                  :thk.project/region-name region-name})})))
