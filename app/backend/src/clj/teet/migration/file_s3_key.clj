(ns teet.migration.file-s3-key
  (:require [datomic.client.api :as d]))

(defn set-file-keys [conn]
  (let [db (d/db conn)
        files-without-key (d/q '[:find ?id ?name
                                 :where
                                 [?id :file/name ?name]
                                 [(missing? $ ?id :file/s3-key)]]
                               db)]
    (d/transact
     conn
     {:tx-data (vec
                (for [[id name] files-without-key]
                  {:db/id id
                   :file/s3-key (str id "-" name)}))})))
