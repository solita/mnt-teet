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
                     (let [{:keys [path default-value]}
                           (name->parameter missing-parameter-name)]
                       (if (= default-value ::no-default-value)
                         (throw (ex-info "Missing parameter that has no default value"
                                         {:parameter-name missing-parameter-name
                                          :parameter-path path}))
                         [path default-value]))))
              (:InvalidParameters response)))))))

(defn- resolve-ssm-parameters
  "Resolve multiple SSM parameters in a nested structure."
  [form]
  (cu/replace-deep
   (ssm-parameters
    (cu/collect (partial instance? SSMParameter) form))
   form))

(def teet-ssm-config
  {:env (->ssm [:env])
   :backup {:bucket-name (->ssm [:s3 :backup-bucket])}
   :enabled-features (->ssm [:enabled-features] #{} parse-enabled-features)
   :tara {:endpoint-url (->ssm [:auth :tara :endpoint])
          :base-url (->ssm [:auth :tara :baseurl])
          :client-id (->ssm [:auth :tara :clientid])
          :client-secret (->ssm [:auth :tara :secret])}
   :session-cookie-key (->ssm [:auth :session-key])
   :auth {:basic-auth-password (->ssm [:api :basic-auth-password] nil)}
   :base-url (->ssm [:base-url])
   :api-url (->ssm [:api :url])
   :document-storage {:bucket-name (->ssm [:s3 :document-bucket])}
   :thk {:export-bucket-name (->ssm [:thk :teet-to-thk :bucket-name] nil)
         :export-dir (->ssm [:thk :teet-to-thk :unprocesseddir] nil)}
   :road-registry {:wfs-url (->ssm [:road-registry :wfs-url] nil)
                   :wms-url (->ssm [:road-registry :wms-url] nil)}
   :xroad {:query-url (->ssm [:xroad-query-url] nil)
           :instance-id (->ssm [:xroad-instance-id] nil)
           :kr-subsystem-id (->ssm [:xroad-kr-subsystem-id] nil)}
   :eelis {:wms-url (->ssm [:eelis :wms-url] nil)}
   :email {:from (->ssm [:email :from] nil)}})

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
    (.scheduleWithFixedDelay
     (Executors/newScheduledThreadPool 1)
     #(load-ssm-config! base-config)
     0 1 TimeUnit/MINUTES)))

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
