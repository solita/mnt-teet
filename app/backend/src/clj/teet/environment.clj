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

(def init-config {:datomic {:db-name "teet"
                            :client {:server-type :ion}}})

(def config (atom init-config))

(defn init-ion-config! [ion-config]
  (swap! config
         (fn [base-config]
           (let [config (merge base-config ion-config)
                 env (:env config)]
             (assoc-in config [:auth :jwt-secret]
                       (ssm-param (str "/teet-" (name env) "/api/jwt-secret")))))))

(defn load-local-config!
  "Load local development configuration from outside repository"
  []
  (let [file (io/file ".." ".." ".." "mnt-teet-private" "config.edn")]
    (when (.exists file)
      (log/info "Loading local config file: " file)
      (reset! config (merge-with (fn [a b]
                                   (if (and (map? a) (map? b))
                                     (merge a b)
                                     b))
                                 init-config
                                 (read-string (slurp file)))))))
(defn db-name []
  (-> @config
      (get-in [:datomic :db-name])
      (str/replace "$USER" (System/getProperty "user.name"))))

(defn config-value [& path]
  (get-in @config (vec path)))

(def datomic-client
  (memoize #(d/client (config-value :datomic :client))))

(def schema (delay (-> "schema.edn" io/resource slurp read-string)))

(defn migrate
  ([conn]
   (migrate conn false))
  ([conn force?]
   (log/info "Migrate, db: " (:db-name conn))
   (doseq [{ident :db/ident txes :txes} @schema
           :let [db (d/db conn)
                 already-applied? (if force?
                                    false
                                    (ffirst
                                     (d/q '[:find ?m :where [?m :db/ident ?ident]
                                            :in $ ?ident]
                                          db ident)))]]
     (if already-applied?
       (log/info "Migration " ident " is already applied.")
       (do
         (log/info "Applying migration " ident)
         (doseq [tx txes]
           (d/transact conn {:tx-data tx}))
         (d/transact conn {:tx-data [{:db/ident ident}]}))))
   (log/info "Migrations finished.")))

(def ^:private db-migrated? (atom false))

(defn datomic-connection []
  (let [conn (d/connect (datomic-client) {:db-name (db-name)})]
    (when-not @db-migrated?
      (migrate conn)
      (reset! db-migrated? true))
    conn))
