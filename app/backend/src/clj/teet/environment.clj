(ns teet.environment
  "Configuration from environment"
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]
            [datomic.client.api :as d]))

(def config (atom {:datomic {:db-name "teet"
                             :client {:server-type :ion}}}))

(defn load-local-config!
  "Load local development configuration from outside repository"
  []
  (let [file (io/file ".." ".." ".." "teet-local" "config.edn")]
    (when (.exists file)
      (log/info "Loading local config file: " file)
      (swap! config (fn [c]
                      (merge-with merge c
                                  (read-string (slurp file))))))))
(defn db-name []
  (-> @config
      (get-in [:datomic :db-name])
      (str/replace "$USER" (System/getProperty "user.name"))))

(defn config-value [& path]
  (get-in @config (vec path)))

(def datomic-client
  (memoize #(d/client (config-value :datomic :client))))

(defn datomic-connection []
  (d/connect (datomic-client) {:db-name (db-name)}))
