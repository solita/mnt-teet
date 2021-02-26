(ns teet.environment
  "Configuration from environment"
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [teet.log :as log]
            [datomic.client.api :as d]
            [cognitect.aws.client.api :as aws]
            [teet.util.collection :as cu])
  (:import (java.time ZoneId)
           (java.util.concurrent TimeUnit Executors)))

(def ^:private ssm-client (delay (aws/client {:api :ssm})))

(def init-config {:datomic {:db-name "teet"
                            :client {:server-type :ion}}

                  ;; Replaced by parameter store value in actual env, value used for local env only
                  :session-cookie-key "ABCDEFGHIJKLMNOP"})

(def config (atom init-config))

(defn reset-config! []
  (reset! config init-config))

;; "road-information-view, component-view"
(defn- parse-enabled-features [ssm-param]
  (->> (str/split ssm-param #",")
       (remove str/blank?)
       (map str/trim)
       (map keyword)
       set))

(defn log-timezone-config! []
  (log/info "local timezone:" (ZoneId/systemDefault)))

(defrecord SSMParameter [path default-value parser])

(defn ->ssm
  "Construct an SSMParameter instance"
  ([path] (->ssm path ::no-default-value))
  ([path default-value]
   (->ssm path default-value identity))
  ([path default-value parser]
   (assert (vector? path) "Path must be a vector")
   (assert (fn? parser) "Parser must be a function")
   (->SSMParameter path default-value parser)))

(defn- ssm-parameters
  "Fetch a collection of SSM parameters in one go.
  Parameters is a collection of SSMParameter instances.
  Returns mapping from SSMParameter to the value.

  If a parameter is missing and it doesn't have a default value,
  an exception will be thrown."
  [parameters]
  (let [name->parameter (into {}
                              (map (fn [{path :path :as param}]
                                     [(str "/teet/" (str/join "/" (map name path)))
                                      param]))
                              parameters)

        ;; GetParameters supports at most 10 parameters in a single call
        ;; so we partition keys and invoke the operation for each batch
        responses (for [keys (partition-all 10 (keys name->parameter))]
                    (aws/invoke @ssm-client
                                {:op :GetParameters
                                 :request {:Names (vec keys)}}))]
    (reduce
     merge
     (for [response responses]
       (merge
        (into {}
              (map (fn [{:keys [Name Value]}]
                     (let [{parser :parser :as param} (name->parameter Name)]
                       [param (parser Value)])))
              (:Parameters response))
        (into {}
              (map (fn [missing-parameter-name]
                     (let [{:keys [path default-value] :as param}
                           (name->parameter missing-parameter-name)]
                       (if (= default-value ::no-default-value)
                         (throw (ex-info "Missing parameter that has no default value"
                                         {:parameter-name missing-parameter-name
                                          :parameter-path path}))
                         [param default-value]))))
              (:InvalidParameters response)))))))

(defn- resolve-ssm-parameters
  "Resolve multiple SSM parameters in a nested structure."
  [form]
  (cu/replace-deep
   (ssm-parameters
    (cu/collect (partial instance? SSMParameter) form))
   form))

(defn- suffix-list [string]
  (into #{}
        (map str/trim)
        (str/split string #",")))

(def teet-ssm-config
  {:datomic {:db-name (->ssm [:datomic :db-name] "teet")
             :asset-db-name (->ssm [:datomic :asset-db-name] "asset")
             :client {:server-type :ion}}
   :env (->ssm [:env])
   :backup {:bucket-name (->ssm [:s3 :backup-bucket])}
   :enabled-features (->ssm [:enabled-features] #{} parse-enabled-features)
   :tara {:endpoint-url (->ssm [:auth :tara :endpoint])
          :base-url (->ssm [:auth :tara :baseurl])
          :client-id (->ssm [:auth :tara :clientid])
          :client-secret (->ssm [:auth :tara :secret])}
   :session-cookie-key (->ssm [:auth :session-key])
   :auth {:basic-auth-password (->ssm [:api :basic-auth-password] nil)
          :jwt-secret (->ssm [:api :jwt-secret])}
   :base-url (->ssm [:base-url])
   :api-url (->ssm [:api :url])
   :document-storage {:bucket-name (->ssm [:s3 :document-bucket])
                      :export-bucket-name (->ssm [:s3 :export-bucket] nil)}
   :file {:allowed-suffixes (->ssm [:file :allowed-suffixes] #{} suffix-list)
          :image-suffixes (->ssm [:file :image-suffixes] #{} suffix-list)}
   :thk {:export-bucket-name (->ssm [:thk :teet-to-thk :bucket-name] nil)
         :export-dir (->ssm [:thk :teet-to-thk :unprocesseddir] nil)
         :url (->ssm [:thk :url] nil)}
   :road-registry {:wfs-url (->ssm [:road-registry :wfs-url] nil)
                   :wms-url (->ssm [:road-registry :wms-url] nil)}
   :xroad {:query-url (->ssm [:xroad-query-url] nil)
           :instance-id (->ssm [:xroad-instance-id] nil)
           :kr-subsystem-id (->ssm [:xroad-kr-subsystem-id] nil)}
   :eelis {:wms-url (->ssm [:eelis :wms-url] nil)}
   :email {:from (->ssm [:email :from] nil)
           :subject-prefix (->ssm [:email :subject-prefix] nil)
           :contact-address (->ssm [:email :contact-address] nil)
           :server (->ssm [:email :server] {} read-string)}
   :notify {:application-expire-days (->ssm [:notify :application-expire-days] 45 #(Integer/parseInt %))}
   :vektorio {:api-key (->ssm [:vektorio :api-key] nil)
              :config (->ssm [:vektorio :config] {} (comp #(update % :file-extensions suffix-list)
                                                          read-string))}})

(defn- load-ssm-config! [base-config]
  (let [old-config @config
        new-config (merge base-config
                          (resolve-ssm-parameters teet-ssm-config))]
    (when (not= old-config new-config)
      (log/info "Configuration changed")
      (reset! config new-config))))

(defn init-ion-config! [ion-config]
  (log-timezone-config!)
  (let [base-config (merge init-config ion-config)]
    (load-ssm-config! base-config)
    (.scheduleWithFixedDelay
     (Executors/newScheduledThreadPool 1)
     #(load-ssm-config! base-config)
     1 1 TimeUnit/MINUTES)))

(defn merge-config! [new-config]
  (swap! config (fn [old-config]
                  (cu/deep-merge old-config new-config))))

(defn load-local-config!
  "Load local development configuration from outside repository"
  ([] (load-local-config! (io/file ".." ".." ".." "mnt-teet-private" "config.edn")))
  ([file]
   (when (.exists file)
     (log/info "Loading local config file: " file)
     (reset! config (cu/deep-merge init-config
                                   (read-string (slurp file)))))))

(defn db-name []
  (-> @config
      (get-in [:datomic :db-name])
      (str/replace "$USER" (System/getProperty "user.name"))))


(defn config-value [& path]
  (get-in @config (vec path)))

(defn asset-db-name []
  (config-value :datomic :asset-db-name))

(defn config-map [key-path-map]
  (reduce-kv (fn [acc key path]
               (assoc acc key (apply config-value path)))
             {} key-path-map))

(defn feature-enabled? [feature]
  (config-value :enabled-features feature))

(defmacro when-feature [feature & body]
  `(when (feature-enabled? ~feature)
     ~@body))

(def datomic-client
  (memoize #(d/client (config-value :datomic :client))))

(def schema (delay (-> "schema.edn" io/resource slurp read-string)))

(defn migrate
  ([conn]
   (migrate conn false))
  ([conn force?]
   (try
     (let [schema @schema
           applied-migrations (into #{}
                                    (map first)
                                    (d/q '[:find ?ident
                                           :where [_ :db/ident ?ident]
                                           :in $ [?ident ...]]
                                         (d/db conn)
                                         (map :db/ident schema)))]
       (log/info (count applied-migrations) "/" (count schema) "migrations already applied.")
       (doseq [{ident :db/ident txes :txes} schema
               :when (or force?
                         (not (applied-migrations ident)))]
         (log/info "Applying migration " ident)
         (doseq [tx txes]
           (if (symbol? tx)
             (do
               (require (symbol (namespace tx)))
               ((resolve tx) conn))
             (d/transact conn {:tx-data tx})))
         (d/transact conn {:tx-data [{:db/ident ident}]})))
     (catch Exception e
       (log/error e "Uncaught exception in migration")
       (throw e)))
   (log/info "Migrations finished.")))

(defn- sha-256 [string]
  (str/join
   (map #(format "%x" %)
        (.digest (java.security.MessageDigest/getInstance "SHA-256")
                 (.getBytes string "UTF-8")))))

(defn- migrate-asset-base-schema
  "Base schema for asset-db, must be transacted before the loaded schema."
  [conn]
  (let [schema (-> "asset-base-schema.edn" io/resource slurp read-string)
        db (d/db conn)
        attrs (map :db/ident schema)
        exists? (= (count attrs)
                   (ffirst
                    (d/q '[:find (count ?e)
                           :where [?e :db/ident ?id]
                           :in $ [?id ...]]
                         db attrs)))]
    (if exists?
      db
      (:db-after
       (d/transact conn {:tx-data schema})))))

(defn migrate-asset-db
  "Migrate asset databse. The whole schema is transacted in one go
  if the hash of the schema file has changed."
  [conn]
  (try
    (let [schema-source (-> "asset-schema.edn" io/resource slurp)
          hash (sha-256 schema-source)
          db (migrate-asset-base-schema conn)
          last-hash (->>
                     (d/q '[:find ?hash ?txi
                            :where
                            [?tx :tx/schema-hash ?hash]
                            [?tx :db/txInstant ?txi]] db)
                     (sort-by second)
                     last first)]

      (when (not= hash last-hash)
        (d/transact
         conn
         {:tx-data (into [{:db/id "datomic.tx" :tx/schema-hash hash}]
                         (read-string schema-source))})
        (log/info "Asset database migrated, previous hash: " last-hash ", current hash: " hash)))
    (catch Exception e
      (log/error e "Uncaught exception in asset db migration")
      (throw e))))

(def ^:private db-migrated? (atom false))
(def ^:private asset-db-migrated? (atom false))

(defn- ensure-database [client db-name]
  (let [existing-databases
        (into #{} (d/list-databases client {}))]
    (if (existing-databases db-name)
      :already-exists
      (do
        (d/create-database client {:db-name db-name})
        :created))))

(def ^:dynamic *connection* nil)
(def ^:dynamic *asset-connection* nil)

(defn connection
  "Gets a Datomic connection to the named database, creating it if necessary.
  If migrate? is true, the migrations in schema will be transacted before
  returning the connection."
  [db-name migrate?]
  (let [db db-name
        client (datomic-client)
        db-status (ensure-database client db)
        conn (d/connect client {:db-name db})]
    (log/debug "Using database: " db db-status)
    (when migrate?
      (migrate conn))
    conn))

(defn datomic-connection
  "Returns thread bound connection or creates a new one."
  []
  (or *connection*
      (let [db (db-name)
            client (datomic-client)
            db-status (ensure-database client db)
            conn (d/connect client {:db-name db})]
        (log/info "Using database: " db db-status)
        (when-not @db-migrated?
          (migrate conn)
          (reset! db-migrated? true))
        conn)))

(defn asset-connection
  "Returns thread bound asset db connection or creates a new one."
  []
  (if-not (feature-enabled? :asset-db)
    (throw (ex-info "Asset database is not enabled" {:missing-feature-flag :asset-db}))
    (or *asset-connection*
        (let [db (asset-db-name)
              client (datomic-client)
              db-status (ensure-database client db)
              conn (d/connect client {:db-name db})]
          (log/debug "Using database: " db db-status)
          (when-not @asset-db-migrated?
            (migrate-asset-db conn)
            (reset! asset-db-migrated? true))
          conn))))

(defn asset-db
  "Return an asset db handled."
  []
  (d/db (asset-connection)))

(defn check-site-password [given-password]
  (let [actual-pw (config-value :auth :basic-auth-password)]
    (or (nil? actual-pw)
        (= given-password actual-pw))))

(defn api-context
  "Convenience for getting a PostgREST API context map."
  []
  {:api-url (config-value :api-url)
   :api-secret (config-value :auth :jwt-secret)})
