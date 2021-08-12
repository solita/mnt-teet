(ns teet.test.utils
  (:require [datomic.client.api :as d]
            teet.comment.comment-commands
            teet.comment.comment-queries
            [teet.db-api.db-api-handlers :as db-api-handlers]
            [teet.environment :as environment]
            [teet.log :as log]
            teet.task.task-commands
            [teet.user.user-model :as user-model]
            [teet.user.user-db :as user-db]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [teet.util.datomic :as du]
            [teet.file.file-storage :as file-storage]
            [clojure.string :as str]
            [clojure.test :refer [is]])
  (:import (java.util Date)))

;; Convenient shortcuts
(def manager-id #uuid "4c8ec140-4bd8-403b-866f-d2d5db9bdf74")
(def external-consultant-id #uuid "ccbedb7b-ab30-405c-b389-292cdfe85271")
(def internal-consultant-id #uuid "008af5b7-0f45-01ba-03d0-003c111c8f00")
(def internal-consultant-2-id #uuid "fa8af5b7-df45-41ba-93d0-603c543c8801")
(def boss-id #uuid "fa8af5b7-df45-41ba-93d0-603c543c880d")

(def mock-users
  [{:user/id manager-id
    :user/person-id "EE12345678900"
    :user/given-name "Danny D."
    :user/family-name "Manager"
    :user/email "danny.d.manager@example.com"
                                        ; :user/organization "Maanteeamet"
    }

   {:user/id external-consultant-id
    :user/person-id "EE33445566770"
    :user/given-name "Carla"
    :user/family-name "Consultant"
    :user/email "carla.consultant@example.com"
                                        ; :user/organization "ACME Road Consulting, Ltd."
    :user/permissions [{:db/id "carla-permission"
                        :permission/role :external-consultant
                        :permission/valid-from (java.util.Date.)}]
    }

   {:user/id boss-id
    :user/person-id "EE94837264730"
    :user/given-name "Benjamin"
    :user/family-name "Boss"
    :user/email "benjamin.boss@example.com"
    :user/permissions [{:db/id "boss-permission"
                        :permission/role :ta-project-manager
                        :permission/valid-from (java.util.Date.)}]
    }

   {:user/id internal-consultant-id
    :user/person-id "EE12337261232"
    :user/given-name "Edna E."
    :user/family-name "Consultant"
    :user/email "edna.e.consultant@example.com"
                                        ; :user/organization "Maanteeamet"
    }
   {:user/id internal-consultant-2-id
    :user/person-id "EE12345678955"
    :user/given-name "Irma I."
    :user/family-name "Consultant"
    :user/email "irma.i.consultant@example.com"
    ;; :user/permissions [{:db/id "irma-permission"
    ;;                     :permission/role :ta-consultant
    ;;                     :permission/valid-from (java.util.Date.)}]
    ;; :user/organization "Maanteeamet"
                  }])

(def mock-user-manager [:user/id manager-id])
(def mock-user-carla-consultant [:user/id external-consultant-id])
(def mock-user-boss [:user/id boss-id])
(def mock-user-edna-consultant [:user/id internal-consultant-id])


;;
;; Test fixtures
;;

(def ^:dynamic *connection* "Datomic connection during tests" nil)
(def ^:dynamic *asset-connection* "Asset db Datomic connection during tests" nil)
(def ^:dynamic *data-fixture-ids* ":db/id values for entities created in data fixtures" nil)
(def ^:dynamic *global-test-data* "Test specific data that can be stored and retrieved within a single test." nil)

(defn connection
  "Returns the current connection. Can only be called within tests using with-db fixture."
  []
  *connection*)

(defn asset-connection
  "Returns the current asset db connection. Can only be called within tests using with-db fixture."
  []
  *asset-connection*)

(defn db
  "Returns the current database. Can only be called within tests using with-db fixture."
  []
  (d/db (connection)))

(defn asset-db
  "Returns the current asset database. Can only be called within tests using with-db fixture."
  []
  (d/db (asset-connection)))

(def adb asset-db)

(defn entity
  "Returns navigable entity from the current db."
  [eid]
  (du/entity (db) eid))

(defn tx
  "Transact the given tx-data maps. Can only be called within tests using with-db fixture."
  [& tx-data]
  (d/transact (connection) {:tx-data (vec tx-data)}))

(defn ->db-id [data-fixture-temp-id]
  (assert *data-fixture-ids* "->db-id can only be used within with-db fixture")
  (or (get @*data-fixture-ids* data-fixture-temp-id)
      (throw (ex-info "No db id found for data-fixture-temp-id"
                      {:temp-id data-fixture-temp-id}))))

#_(defn- datomic-client-env
  "Get Datomic client info from environment variables (running in CI)"
  []
  (let [region (System/getenv "DATOMIC_REGION")
        system (System/getenv "DATOMIC_SYSTEM")]
    (when (and region system)
      {:server-type :ion
       :region region
       :system system
       :query-group system
       :endpoint (str "http://entry." system "." region ".datomic.net:8182/")
       :proxy-port 8182})))

(defn- datomic-client-env
  []
  {:server-type :dev-local
   :storage-dir :mem
   :system "teet-db-test"})

(defn with-config [nested-config-map]
  (fn with-config-fixture [t]
    (let [old-config @environment/config]
      (try
        (environment/merge-config! nested-config-map)
        (t)
        (finally
          (reset! environment/config old-config))))))

(defmacro run-with-config [config & body]
  `(let [with-config-fn# (with-config ~config)]
     (with-config-fn#
       (fn []
         ~@body))))

(defn with-environment [f]
  (run-with-config
   {:datomic {:client (datomic-client-env)}
    :document-storage {:bucket-name (System/getenv "DOCUMENT_BUCKET")}}
   (f)))

(defn with-postgresql [f]
  (run-with-config
   {:postgresql {:uri "jdbc:postgresql://localhost:5432/teet"
                 :user "authenticator"
                 :password ""}}
   (f)))

(defn with-db
  ([] (with-db {}))
  ([{:keys [data-fixtures migrate? mock-users? skip-delete? timestamp]
     :or {data-fixtures [:projects]
          migrate? true
          mock-users? true
          skip-delete? false
          timestamp (System/currentTimeMillis)} :as _opts}]
   (fn with-db-fixture [f]
     (let [test-db-name (str "test-db-" timestamp)
           test-asset-db-name (str "test-asset-db-" timestamp)]
       (reset! @#'environment/asset-db-migrated? false)
       (run-with-config
        {:datomic {:db-name test-db-name
                   :asset-db-name test-asset-db-name}}
        (log/redirect-ion-casts! :stderr)
        (let [client (d/client (environment/config-value :datomic :client))
              db-name {:db-name test-db-name}
              asset-db-name {:db-name test-asset-db-name}
              asset-db? (environment/feature-enabled? :asset-db)]
          (try
            (log/info "Creating database " test-db-name)
            (d/create-database client db-name)
            (when asset-db?
              (log/info "Creating asset database " asset-db-name)
              (d/create-database client asset-db-name))
            (binding [*connection* (d/connect client db-name)
                      *asset-connection* (when asset-db?
                                           (d/connect client asset-db-name))
                      *data-fixture-ids* (atom {})]
              (binding [environment/*connection* *connection*
                        environment/*asset-connection* *asset-connection*]
                (when migrate?
                  (environment/migrate *connection* @environment/schema)
                  (when asset-db?
                    (environment/migrate-asset-db *asset-connection*)))

                (when mock-users?
                  (d/transact *connection* {:tx-data mock-users}))
                (doseq [df data-fixtures
                        :let [resource (str "resources/" (name df) ".edn")
                              fixture-data (-> resource io/resource
                                               slurp read-string)
                              fixture-db (or (:db (meta fixture-data)) :teet)
                              fixture-conn (case fixture-db
                                             :teet *connection*
                                             :asset *asset-connection*)]]
                  (log/info "Transacting data " fixture-db " fixture: " df)
                  (let [{tempids :tempids}
                        (d/transact fixture-conn {:tx-data fixture-data})]
                    (swap! *data-fixture-ids*
                           (fn [current-tempids]
                             (if-let [duplicates (seq (set/intersection (set (keys current-tempids))
                                                                        (set (keys tempids))))]
                               (throw (ex-info "Data fixtures have duplicate tempids!"
                                               {:duplicates duplicates}))
                               (merge current-tempids tempids))))))
                (with-redefs [d/q (fn [& args]
                                    (apply du/q args))]
                  (f))))
            (finally
              (when-not skip-delete?
                (log/info "Deleting databases: " test-db-name ", " test-asset-db-name)
                (d/delete-database client db-name)
                (d/delete-database client asset-db-name))))))))))

(defn with-global-data [f]
  (binding [*global-test-data* (atom {})]
    (f)))

(defn store-data! [key value]
  (assert *global-test-data* "store-data! can only be used within with-global-data fixture")
  (swap! *global-test-data* assoc key value)
  value)

(defn get-data [key]
  (assert *global-test-data* "get-data can only be used within with-global-data fixture")
  (let [data @*global-test-data*]
    (assert (contains? data key) (str key " was never stored with store-data!"))
    (get data key)))


;;
;; Database
;;

(def db-connection environment/datomic-connection)


;;
;; Local login, queries, commands
;;
(def ^:private logged-in-user-ref (atom nil))

(defn logged-user []
  @logged-in-user-ref)

(defn local-login
  [user-ref]
  (reset! logged-in-user-ref user-ref)
  (log/info "Locally logged in as " user-ref))

(defn local-logout []
  (reset! logged-in-user-ref nil))

(defn- action-ctx
  "A valid datomic reference must be obtainable from `user` with `user-model/user-ref`"
  [user]
  (let [db (db)
        user-ref (when user
                   (user-model/user-ref user))]
    (merge
     {:conn (db-connection)
      :db db
      :user (when user-ref
              (user-db/user-info db user-ref))
      :session "foo"}
     (when (environment/config-value :postgresql)
       ;; NOTE: we are not closing these...
       {:sql-conn {:connection (environment/get-pg-connection)}})
     (when (environment/feature-enabled? :asset-db)
       {:asset-conn (asset-connection)
        :asset-db (d/db (asset-connection))}))))

(defn local-query
  ([query args]
   (if-let [user-id @logged-in-user-ref]
     (local-query user-id query args)
     (log/error "Not logged in! Call user/local-login with an existing user id to log in.")))

  ([user-ref query args]
   (db-api-handlers/raw-query-handler (action-ctx user-ref)
                                      {:args args
                                       :query query})))

(defn local-command
  ([command args]
   (log/info "Calling " command " with " args)
   (if-let [user-id @logged-in-user-ref]
     (local-command user-id command args)
     (log/error "Not logged in! Call user/local-login with an existing user id to log in.")))

  ([user-id command args]
   (db-api-handlers/raw-command-handler (action-ctx user-id)
                                        {:payload args
                                         :command command})))

;;
;; Various helpers
;;
(defn create-task [{:keys [user activity task]} & [global-test-data-key]]
  (when user
    (local-login user))
  (let [activity-entity (du/entity (db) activity)
        task (merge (merge {:task/send-to-thk? false
                            :task/type :task.type/design-requirements
                            :task/group :task.group/base-data
                            :db/id "new-id"
                            :task/description "Design requirements for testing."
                            :task/assignee {:user/id (second user)}
                            :task/estimated-start-date (:activity/estimated-start-date activity-entity)
                            :task/estimated-end-date (:activity/estimated-end-date activity-entity)}
                           task))
        payload {:db/id activity
                 :task/estimated-start-date (:task/estimated-start-date task)
                 :task/estimated-end-date (:task/estimated-end-date task)
                 :activity/tasks-to-add [[(:task/group task)
                                          (:task/type task)
                                          (:task/send-to-thk? task)]]}
        tempid (str "NEW-TASK-" (name (:task/group task)) "-" (name (:task/type task)))
        task-id (-> (local-command :activity/add-tasks payload)
                    :tempids
                    (get tempid))]
    (local-command :task/update
                   (merge
                    {:db/id task-id}
                    (select-keys task [:task/assignee :task/description])))
    (if global-test-data-key
      (store-data! global-test-data-key task-id)
      task-id)))




(defn create-comment [{:keys [user entity-type entity-id comment]} & [global-test-data-key]]
  (local-login user)
  (let [comment-id (-> (local-command :comment/create (merge {:entity-id entity-id
                                                              :entity-type entity-type
                                                              :comment "Test comment"
                                                              :files []
                                                              :visibility :comment.visibility/all}
                                                             comment))
                       :tempids
                       (get "new-comment"))]
    (if global-test-data-key
      (store-data! global-test-data-key comment-id)
      comment-id)))

(defn complete-task [{:keys [user task-id] :as params}]
  (local-login user)
  (let [ret1 (local-command :task/submit params)
        ret2 (local-command :task/start-review params)
        ret3 (local-command :task/review (merge params {:result :accept}))]
    [ret1 ret2 ret3]))

(defn give-admin-permission
  [user-id]
  (tx {:db/id            (user-model/user-ref user-id)
       :user/permissions [{:db/id                 "new-permission"
                           :permission/role       :admin
                           :permission/valid-from (Date.)}]}))

(defn fake-upload
  "Upload (or replace) file.
  Doesn't really upload anything to S3, just invokes the backend commands."
  ([upload-to-task-id file-info]
   (fake-upload upload-to-task-id file-info nil))
  ([upload-to-task-id file-info previous-version-id]
   (with-redefs [file-storage/upload-url (fn [name]
                                           (str "UPLOAD:" name))]
     (let [{:keys [url task-id file]}
           (local-command
             (logged-user)
             (if previous-version-id
                            :file/replace
                            :file/upload)
                          (merge
                           {:task-id upload-to-task-id
                            :file file-info}
                           (when previous-version-id
                             {:previous-version-id previous-version-id})))]
       (is (str/starts-with? url "UPLOAD:"))
       (is (str/ends-with? url (:file/name file-info)))
       (when-not previous-version-id
         (is (= task-id upload-to-task-id)))
       (is (:db/id file))
       (is (not (contains? file :file/id)))

       ;; Mark upload as complete
       (let [{id :db/id file-id :file/id :as uploaded}
             (local-command :file/upload-complete
                            {:db/id (:db/id file)})]
         (is (= id (:db/id uploaded)))
         (is (uuid? file-id))
         uploaded)))))

(defn fake-upload-attachment
  "Upload (or replace) attachment file.
  params maps should have same keys as the upload-attachment command: file, project-id, attach-to
  Doesn't really upload anything to S3, just invokes the backend commands."
  [{:keys [file project-id attach-to] :as params}]
  (with-redefs [file-storage/upload-url (fn [name]
                                          (str "UPLOAD:" name))]
    (let [{:keys [url task-id file]}
          (local-command :file/upload-attachment
                         params)]
      (is (str/starts-with? url "UPLOAD:"))
      (is (str/ends-with? url (:file/name file)))
      (is (:db/id file))
      (is (not (contains? file :file/id)))

      ;; Mark upload as complete
      (let [{id :db/id file-id :file/id :as uploaded}
            (local-command :file/upload-complete
                           {:db/id (:db/id file)})]
        (is (= id (:db/id uploaded)))
        (is (uuid? file-id))
        uploaded))))

(defmacro is-thrown-with-data?
  "Check that body throws given exception data."
  [thrown-ex-data & body]
  `(try
     ~@body
     (is false "No exception was thrown")
     (catch Exception ex#
       (let [d# (ex-data ex#)]
         (is (= (select-keys d# (keys ~thrown-ex-data))
                ~thrown-ex-data)
             "Exception data matches specified data")))))
