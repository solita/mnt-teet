(ns teet.migration.file-sequence-number
    (:require [datomic.client.api :as d]))

(defn pos-number->sequence-number [conn]
  (let [db (d/db conn)
        files-with-pos
        (d/q '[:find (pull ?f [:db/id :file/pos-number])
               :where
               [?act :activity/name :activity.name/land-acquisition]
               [?act :activity/tasks ?task]
               [?task :task/files ?f]
               [?f :file/pos-number _]] db)]
    (d/transact
     conn
     {:tx-data (vec (for [f files-with-pos]
                      {:db/id (:db/id f)
                       :file/sequence-number (:file/pos-number f)}))})))
