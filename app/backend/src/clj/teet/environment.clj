(ns teet.environment
  "Configuration from environment"
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]
            [datomic.client.api :as d]
            [clojure.java.io :as io]
            [amazonica.aws.simplesystemsmanagement :as ssm]))

(defn- ssm-param [name]
  (->> name (ssm/get-parameter :name) :parameter :value))

(def config (atom {:datomic {:db-name "teet"
                             :client {:server-type :ion}}}))

(defn init-ion-config! [config]
  (swap! config
         (fn [base-config]
           (let [config (merge base-config config)
                 env (:env config)]
             (assoc-in config [:auth :jwt-secret]
                       (ssm-param (str "/teet-" (name env) "/api/jwt-secret")))))))

(defn load-local-config!
  "Load local development configuration from outside repository"
  []
  (let [file (io/file ".." ".." ".." "mnt-teet-private" "config.edn")]
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

(def schema (delay (-> "schema.edn" io/resource slurp read-string)))

(defn- migrate [conn]
  (log/info "Migrate, db: " (:db-name conn))
  (doseq [{ident :db/ident txes :txes} @schema
          :let [db (d/db conn)
                already-applied? (ffirst
                                  (d/q '[:find ?m :where [?m :db/ident ?ident]
                                         :in $ ?ident]
                                       db ident))]]
    (if already-applied?
      (log/info "Migration " ident " is already applied.")
      (do
        (log/info "Applying migration " ident)
        (doseq [tx txes]
          (d/transact conn {:tx-data tx}))
        (d/transact conn {:tx-data [{:db/ident ident}]}))))
  (log/info "Migrations finished."))

(def ^:private db-migrated? (atom false))

(defn datomic-connection []
  (let [conn (d/connect (datomic-client) {:db-name (db-name)})]
    (when-not @db-migrated?
      (migrate conn)
      (reset! db-migrated? true))
    conn))
