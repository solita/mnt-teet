(ns teet.environment
  "Configuration from environment"
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [teet.log :as log]
            [datomic.client.api :as d]
            [amazonica.aws.simplesystemsmanagement :as ssm]))

(defn- ssm-param
  [env & param-path]
  (->> (str "/teet-" (name env) "/" (str/join "/" (map name param-path)))
       (ssm/get-parameter :name)
       :parameter :value))

(def init-config {:datomic {:db-name "teet"
                            :client {:server-type :ion}}

                  ;; Replaced by parameter store value in actual env, value used for local env only
                  :session-cookie-key "ABCDEFGHIJKLMNOP"})

(def config (atom init-config))

(defn tara-config [env]
  (let [p (partial ssm-param env :auth :tara)]
    {:endpoint-url (p :endpoint)
     :base-url (p :baseurl)
     :client-id (p :clientid)
     :client-secret (p :secret)}))

(defn init-ion-config! [ion-config]
  (swap! config
         (fn [base-config]
           (let [config (merge base-config ion-config)
                 env (:env config)
                 config (assoc-in config [:auth :jwt-secret]
                                  (ssm-param env :api :jwt-secret))
                 bap (ssm-param env :api :basic-auth-password)
                 tara (tara-config env)
                 config (-> config
                            (assoc :tara tara)
                            (assoc :session-cookie-key
                                   (ssm-param env :auth :session-key))
                            (assoc-in [:auth :basic-auth-password] bap)
                            (assoc :base-url (ssm-param env :base-url))
                            (assoc :api-url (ssm-param env :api :url)))]
             config))))

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

(defn check-site-password [given-password]
  (let [actual-pw (config-value :auth :basic-auth-password)]
    (or (nil? actual-pw)
        (= given-password actual-pw))))
