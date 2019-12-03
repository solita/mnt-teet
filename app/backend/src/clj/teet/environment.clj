(ns teet.environment
  "Configuration from environment"
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [teet.log :as log]
            [datomic.client.api :as d]
            [amazonica.aws.simplesystemsmanagement :as ssm])
  (:import (com.amazonaws.services.simplesystemsmanagement.model ParameterNotFoundException)))

(defn- ssm-param
  [& param-path]
  (let [value (->> (str "/teet/" (str/join "/" (map name param-path)))
                   (ssm/get-parameter :name)
                   :parameter :value)]
    (if (string? value)
      (str/trim value)
      value)))

(defn- ssm-param-default [param-path default-value]
  (try
    (apply ssm-param param-path)
    (catch ParameterNotFoundException _e
      default-value)))

(def init-config {:datomic {:db-name "teet"
                            :client {:server-type :ion}}

                  ;; Replaced by parameter store value in actual env, value used for local env only
                  :session-cookie-key "ABCDEFGHIJKLMNOP"})

(def config (atom init-config))

;; "road-information-view, component-view"
(defn parse-enabled-features [ssm-param]
  (->> (str/split ssm-param #",")
       (remove str/blank?)
       (map str/trim)
       (map keyword)
       set))

(defn enabled-features-config []
  (try (parse-enabled-features (ssm-param :enabled-features))
       (catch ParameterNotFoundException _e
         (log/warn "SSM parameter enabled-features not found, treating all as disabled")
         #{})))

(defn tara-config []
  (let [p (partial ssm-param :auth :tara)]
    {:endpoint-url (p :endpoint)
     :base-url (p :baseurl)
     :client-id (p :clientid)
     :client-secret (p :secret)}))

(defn init-ion-config! [ion-config]
  (swap! config
         (fn [base-config]
           (let [config (merge base-config ion-config)
                 config (assoc-in config [:auth :jwt-secret]
                                  (ssm-param :api :jwt-secret))
                 bap (ssm-param :api :basic-auth-password)
                 tara (tara-config)
                 ;; enabled-features (enabled-features-config)
                 config (-> config
                            (assoc :tara tara)
                            (assoc :session-cookie-key
                                   (ssm-param :auth :session-key))
                            (assoc-in [:auth :basic-auth-password] bap)
                            (assoc :base-url (ssm-param :base-url))
                            (assoc :api-url (ssm-param :api :url))
                            (assoc-in [:document-storage :bucket-name] (ssm-param :s3 :document-bucket))
                            (assoc-in [:thk :export-bucket-name] (ssm-param-default [:thk :teet-to-thk :bucket-name] nil))
                            (assoc-in [:thk :export-dir] (ssm-param-default [:thk :teet-to-thk :unprocesseddir] nil)))]
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

(defn- ensure-database [client db-name]
  (let [existing-databases
        (into #{} (d/list-databases client {}))]
    (if (existing-databases db-name)
      :already-exists
      (do
        (d/create-database client {:db-name db-name})
        :created))))

(defn datomic-connection []
  (let [db (db-name)
        client (datomic-client)
        db-status (ensure-database client db)
        conn (d/connect client {:db-name db})]
    (log/info "Using database: " db db-status)
    (when-not @db-migrated?
      (migrate conn)
      (reset! db-migrated? true))
    conn))

(defn check-site-password [given-password]
  (let [actual-pw (config-value :auth :basic-auth-password)]
    (or (nil? actual-pw)
        (= given-password actual-pw))))

(defn feature-enabled? [feature]
  (config-value :enabled-features feature))

(defmacro when-feature [feature & body]
  `(when (feature-enabled? ~feature)
     ~@body))
