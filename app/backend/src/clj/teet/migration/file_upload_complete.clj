(ns teet.migration.file-upload-complete
  (:require [datomic.client.api :as d]))

(defn existing-files-upload-complete
  [conn]
  (let [db (d/db conn)
        file-ids (map first
                      (d/q '[:find ?f
                             :where [?f :file/name _]]
                           db))]
    (d/transact
     conn
     {:tx-data (vec (for [f file-ids]
                      {:db/id f
                       :file/upload-complete? true}))})))
