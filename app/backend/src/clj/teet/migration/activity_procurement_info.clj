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
                     :when (or procurement-id
                               procurement-nr)]
                 (merge {:db/id activity-id}
                        (when procurement-id
                          {:activity/procurement-id procurement-id})
                        (when procurement-nr
                          {:activity/procurement-nr procurement-nr})))})))

(defn retract-procurement-info-from-activity [conn]
  (let [activities (->> (d/q '[:find ?a
                               :where (or
                                        [?a :activity/procurement-id _]
                                        [?a :activity/procurement-nr _])]
                          (d/db conn))
                     (mapv first))
        procurement-ids (for [activity-id activities]
                          [:db/retract activity-id :activity/procurement-id])
        procurement-nrs (for [activity-id activities]
                          [:db/retract activity-id :activity/procurement-nr])]
    (d/transact conn
      {:tx-data (into [] (concat procurement-ids procurement-nrs))})))
