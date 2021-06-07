(ns generate-schema
  (:require [clojure.string :as s]
            [datomic.client.api :as d]))

(def datomic-client-config (read-string (slurp "config.edn")))
(when-let [p (get datomic-client-config :aws-profile)]
  (System/setProperty "aws.profile" p))
(def conn (d/connect (d/client datomic-client-config)
                     (select-keys datomic-client-config [:db-name])))

(defn walk-entity [db eid]
  (let [entity-map (d/pull db '[*] eid)]
    {:name name
     :fields (keys entity-map)
     :children 
     (for [[key val] entity-map
           :when (and (vector? val) (-> val seq first :db/id))]
       [key (mapv (partial walk-entity db) (mapcat vals val))])}))

(defn walk-db [db root-attr]
  (let [root-eid-set
        (mapv first (d/q [:find '?e :where ['?e root-attr]] db))]
    (map (partial walk-entity db)
            root-eid-set)))

(defn -main [& args]  
  (walk-db (d/db conn) :thk.project/id))
