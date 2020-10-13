(ns teet.environment
  "Configuration from environment"
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [teet.log :as log]
            [datomic.client.api :as d]
            [cognitect.aws.client.api :as aws])
  (:import (java.time ZoneId)))

(def ^:private ssm-client (delay (aws/client {:api :ssm})))

(defn ssm-param
  [& param-path]
  (let [name (str "/teet/" (str/join "/" (map name param-path)))
        response (aws/invoke @ssm-client {:op :GetParameter
                                          :request {:Name name}})]
    (if (:cognitect.anomalies/category response)
      (throw (ex-info "Anomaly in SSM invocation"
                      {:response response}))
      (let [value (get-in response [:Parameter :Value])]
        (if (string? value)
          (str/trim value)
          value)))))

(defn- ssm-param-default [param-path default-value]
  (try
    (apply ssm-param param-path)
    (catch Exception e
      (if (= (get-in (ex-data e) [:response :__type]) "ParameterNotFound")
        default-value
        (throw (ex-info "Exception in SSM invocation (not ParameterNotfound)"
                        {:param-path param-path :default-value default-value}
                        e))))))

(defn- ssm-parameters [parameter-paths default-values]
  (let [name->path (into {}
                         (map (fn [path]
                                [(str "/teet/" (str/join "/" (map name path)))
                                 path]))
                         parameter-paths)

        ;; GetParameters supports at most 10 parameters in a single call
        ;; so we partition keys and invoke the operation for each batch
        responses (for [keys (partition-all 10 (keys name->path))]
                    (aws/invoke @ssm-client
                                {:op :GetParameters
                                 :request {:Names (vec keys)}}))]
    (reduce
     merge
     (for [response responses]
       (merge
        (into {}
              (map (fn [{:keys [Name Value]}]
                     [(name->path Name) Value]))
              (:Parameters response))
        (into {}
              (map (fn [missing-parameter-name]
                     (let [path (name->path missing-parameter-name)
                           value (get default-values path)]
                       (if (nil? value)
                         (throw (ex-info "Missing parameter that has no default value"
                                         {:parameter-name missing-parameter-name
                                          :parameter-path path}))
                         [path value]))))
              (:InvalidParameters response)))))))

(def init-config {:datomic {:db-name "teet"
                            :client {:server-type :ion}}

                  ;; Replaced by parameter store value in actual env, value used for local env only
                  :session-cookie-key "ABCDEFGHIJKLMNOP"})

(def config (atom init-config))

(defn reset-config! []
  (reset! config init-config))

;; "road-information-view, component-view"
(defn parse-enabled-features [ssm-param]
  (->> (str/split ssm-param #",")
       (remove str/blank?)
       (map str/trim)
       (map keyword)
       set))

(defn enabled-features-config []
  (or (some-> [:enabled-features]
              (ssm-param-default nil)
              parse-enabled-features)
      (do
        (log/warn "SSM parameter enabled-features not found, treating all as disabled")
        #{})))

(defn tara-config []
  (let [p (partial ssm-param :auth :tara)]
    {:endpoint-url (p :endpoint)
     :base-url (p :baseurl)
     :client-id (p :clientid)
     :client-secret (p :secret)}))

(defn log-timezone-config! []
  (log/info "local timezone:" (ZoneId/systemDefault)))

(defn init-ion-config! [ion-config]
  (log-timezone-config!)
  (swap! config
         (fn [base-config]
           (let [config (merge base-config ion-config)
                 config (assoc-in config [:auth :jwt-secret]
                                  (ssm-param :api :jwt-secret))
                 bap (ssm-param :api :basic-auth-password)
                 tara (tara-config)
                 ;;
                 config (-> config
                            (assoc :enabled-features (enabled-features-config))
                            (assoc :tara tara)
                            (assoc :session-cookie-key
                                   (ssm-param :auth :session-key))
                            (assoc-in [:auth :basic-auth-password] bap)
                            (assoc :base-url (ssm-param :base-url))
                            (assoc :api-url (ssm-param :api :url))
                            (assoc-in [:document-storage :bucket-name] (ssm-param :s3 :document-bucket))
                            (assoc-in [:thk :export-bucket-name] (ssm-param-default [:thk :teet-to-thk :bucket-name] nil))
                            (assoc-in [:thk :export-dir] (ssm-param-default [:thk :teet-to-thk :unprocesseddir] nil))
                            (assoc-in [:road-registry]
                                      {:wfs-url (ssm-param-default [:road-registry :wfs-url] nil)
                                       :wms-url (ssm-param-default [:road-registry :wms-url] nil)})
                            (assoc-in [:xroad] {:query-url (ssm-param-default [:xroad-query-url] nil)
                                                :instance-id (ssm-param-default [:xroad-instance-id] nil)
                                                :kr-subsystem-id (ssm-param-default [:xroad-kr-subsystem-id] nil)})
                            (assoc-in [:eelis]
                                      {:wms-url (ssm-param-default [:eelis :wms-url] nil)}))]
             config))))

(defn load-local-config!
  "Load local development configuration from outside repository"
  ([] (load-local-config! (io/file ".." ".." ".." "mnt-teet-private" "config.edn")))
  ([file]
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

(defn config-map [key-path-map]
  (reduce-kv (fn [acc key path]
               (assoc acc key (apply config-value path)))
             {} key-path-map))

(def datomic-client
  (memoize #(d/client (config-value :datomic :client))))

(def schema (delay (-> "schema.edn" io/resource slurp read-string)))

(defn migrate
  ([conn]
   (migrate conn false))
  ([conn force?]
   (log/info "Migrate, db: " (:db-name conn))
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

(def ^:dynamic *connection* nil)

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

(defn check-site-password [given-password]
  (let [actual-pw (config-value :auth :basic-auth-password)]
    (or (nil? actual-pw)
        (= given-password actual-pw))))

(defn feature-enabled? [feature]
  (config-value :enabled-features feature))

(defmacro when-feature [feature & body]
  `(when (feature-enabled? ~feature)
     ~@body))

(defn api-context
  "Convenience for getting a PostgREST API context map."
  []
  {:api-url (config-value :api-url)
   :api-secret (config-value :auth :jwt-secret)})
