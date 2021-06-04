(ns generate-schema
  (:require [clojure.string :as s]
            [datomic.client.api :as d]))

(def datomic-client-config (read-string (slurp "config.edn")))
(when-let [p (get datomic-client-config :aws-profile)]
  (System/setProperty "aws.profile" p))
(def client (d/client datomic-client-config))
(def db (d/connect client (select-keys datomic-client-config [:db-name])))

(defn -main [& args]  
  (println "foo"))
