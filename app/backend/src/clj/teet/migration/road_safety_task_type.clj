(ns teet.migration.road-safety-task-type
  (:require [datomic.client.api :as d]))



(defn use-design-road-safety-audit [conn]
  
  (let [r (d/q '[:find (pull ?t [:db/id :thk/task-type]) :where [?t :thk/task-type :thk.task-type/study]] (db))
        result (mapv first r)
        tx-data (vec (for [task (map first r)
                           :when (parent-is-design? task)]
                       {:db/id (:db/id task)
                        :thk.task-type :thk.task-type/design-road-safety-audit}))]
    (clojure.pprint/pprint tx-dta)))
