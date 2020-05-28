(ns teet.migration.activity-procurement-info
  (:require [datomic.client.api :as d]))

(defn extract-procurement-info-from-integration-info [conn]
  (let [activities (d/q '[:find ?a ?ii
                          :where [?a :activity/integration-info ?ii]]
                        (d/db conn))
        parse #(binding [*read-eval* false]
                 (read-string %))]

    (d/transact
     conn
     {:tx-data (for [[activity-id integration-info] activities
                     :let [info (parse integration-info)
                           procurement-id (:activity/procurementid info)
                           procurement-nr (:activity/procurementno info)]
                     :when procurement-id]
                 (merge {:db/id activity-id
                         :activity/procurement-id procurement-id}
                        (when procurement-nr
                          {:activity/procurement-nr procurement-nr})))})))
