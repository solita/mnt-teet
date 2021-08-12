(ns user
  (:require [datomic.client.api :as d]
            [teet.main :as main]
            [teet.meta.meta-model :as meta-model]
            [teet.environment :as environment]
            [teet.test.utils :as tu]
            [teet.thk.thk-integration-ion :as thk-integration]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [datomic.dev-local :as dl]
            [cognitect.aws.client.api :as aws]
            [teet.log :as log]
            [teet.project.project-geometry :as project-geometry])
  (:import (java.util Date)
           (java.util.concurrent TimeUnit Executors)))

(defn go []
  (main/restart)
  (environment/datomic-connection))

(def restart go)

(def db-connection environment/datomic-connection)
(def asset-connection environment/asset-connection)

(defn db []
  (d/db (db-connection)))

(defn adb []
  (environment/asset-db))

(def q d/q)
(def pull d/pull)

(defn pull*
  "Pull all attributes of entity.
  Takes optional depth to pull maps recursively."
  ([eid]
   (pull* eid 0))
  ([eid depth]
   (pull* (db) eid depth))
  ([db eid depth]
   (walk/postwalk
    (fn [f]
      (if (and (pos? depth)
               (map? f)
               (= #{:db/id} (set (keys f))))
        (pull* db (:db/id f) (dec depth))
        f))
    (d/pull db '[*] eid))))


(defn entity [eid]
  (pull (db) '[*] eid))

(defn tx
  "Transact given maps to db"
  [& maps]
  (d/transact (environment/datomic-connection)
              {:tx-data (into [] maps)}))

(defn atx
  "Transact given maps to asset db"
  [& maps]
  (d/transact (asset-connection)
              {:xt-data (into [] maps)}))

(def mock-users tu/mock-users)

(def manager-uid tu/manager-id)
(def external-consultant-id tu/external-consultant-id)
(def internal-consultant-id tu/internal-consultant-id)
(def boss-uid tu/boss-id)

(defn set-email
  [user-eid email-address]
  (tx {:db/id      user-eid
       :user/email email-address}))

(defn give-admin-permission
  [user-eid]
  (tx {:db/id            user-eid
       :user/permissions [{:db/id                 "new-permission"
                           :permission/role       :admin
                           :permission/valid-from (Date.)}]}))

(defn give-manager-permission
  [user-eid]
  (tx {:db/id            user-eid
       :user/permissions [{:db/id                 "new-permission"
                           :permission/role       :ta-project-manager
                           :permission/valid-from (Date.)}]}))

(defn give-external-consultant-permission
  [user-eid]
  (tx {:db/id            user-eid
       :user/permissions [{:db/id                 "new-permission"
                           :permission/role       :external-consultant
                           :permission/valid-from (Date.)}]}))

(defn give-internal-consultant-permission
  [user-eid]
  (tx {:db/id            user-eid
       :user/permissions [{:db/id                 "new-permission"
                           :permission/role       :ta-consultant
                           :permission/valid-from (Date.)}]}))

(defn remove-permission [user-uuid permission-eid]
  (d/transact (environment/datomic-connection)
              {:tx-data [[:db/retract [:user/id user-uuid]
                          :user/permissions permission-eid]]}))

(defn make-file-incomplete!
  "reverse of local-command :file/upload-complete - for testing incomplete file handling"
  [file-uuid]
  (d/transact (environment/datomic-connection)
              {:tx-data [[:db/retract [:file/id file-uuid]
                          :file/upload-complete? true]]}))


(defn all-comments
  []
  (q '[:find (pull ?e [*])
       :where [?e :comment/comment _]]
     (db)))

(defn all-permissions
  []
  (q '[:find (pull ?e [*])
       :where [?e :permission/role _]]
     (db)))

(defn revoke-permission
  [permission-eid]
  (tx {:db/id                  permission-eid
       :permission/valid-until (Date.)}))

(defn query-project
  [project-id]
  (q '[:find (pull ?e [*])
       :in $ ?project-id
       :where [?e :thk.project/id ?project-id]] (db) project-id))

(defn query-all-users
  []
  (map first
       (q '[:find (pull ?e [*])
            :in $
            :where [?e :user/id _]] (db))))

(defn retract-from-project!
  "use like: (retract-from-project! \"17187\" :thk.project/manager 45264694692282960)"
  [project-id a v]
  (d/transact (environment/datomic-connection)
              {:tx-data [[:db/retract [:thk.project/id project-id]
                          a v]]}))

(defn make-mock-users!
  []
  (apply tx mock-users)
  (give-manager-permission [:user/id manager-uid])
  (give-admin-permission [:user/id boss-uid])
  (give-external-consultant-permission [:user/id external-consultant-id])
  (give-internal-consultant-permission [:user/id internal-consultant-id]))

(defn delete-db
  [db-name]
  (d/delete-database (environment/datomic-client) {:db-name db-name}))

(defn create-db
  [db-name]
  (d/create-database (environment/datomic-client) {:db-name db-name}))

(defn force-migrations!
  "Forces all migrations to rerun." ;; TODO: reload schema from environment to reload schema.edn
  []
  (environment/load-local-config!)
  (environment/migrate (db-connection) @environment/schema true))

;; TODO: Add function for importing projects to Datomic
;; See teet.thk.thk-import

(defn import-thk-from-local-file
  [filepath]
  (thk-integration/import-thk-local-file filepath))

(defn attrs->plantuml-entity [[entity-name attrs] types]
  (let [sorted-attrs
        ;; Sort unique fields first, then alphabetically by name
        (sort-by (juxt (complement :db/unique)
                       (comp name :db/ident)) attrs)
        id-attrs (take-while :db/unique sorted-attrs)
        attrs (drop-while :db/unique sorted-attrs)

        format-attr (fn [{:db/keys [ident valueType cardinality unique]}]
                      (let [many? (= :db.cardinality/many (:db/ident cardinality))]
                        (str  "  "
                              (when unique "* ")
                              (name ident)
                              " : " (or (types ident)
                                        (str
                                         (when many? "List<")
                                         (-> valueType :db/ident name)
                                         (when many? ">"))))))]
    (str "entity " entity-name " {\n"

         (str/join "\n" (map format-attr id-attrs))
         (when (seq id-attrs)
           "\n  --\n")
         (str/join "\n" (map format-attr attrs))
         "\n}\n")))

(comment
  (defn output-datomic-entity-diagram []
    (let [include-entities #{"thk.project" "thk.lifecycle" "activity"
                             "task" "file" "comment" "permission"
                             "user" "meta" "user-license"
                             "meeting" "meeting.agenda" "meeting.decision"
                             "participation" "land-acquisition" "notification"
                             "estate-procedure" "estate-compensation"
                             "estate-process-fee" "land-exchange"
                             "cooperation.3rd-party"
                             "cooperation.application"
                             "cooperation.response"
                             "cooperation.opinion"
                             "thk.contract"
                             "company"
                             "company-contract"
                             "company-contract-employee"
                             "review"
                             "cost-index"
                             "index-value"}
          types {:permission/projects "List<thk.project>"
                 :thk.project/lifecycles "List<thk.lifecycle>"
                 :thk.lifecycle/activities "List<activity>"
                 :activity/tasks "List<task>"
                 :task/files "List<file>"
                 :task/comments "List<comment>"
                 :file/comments "List<comment>"
                 :thk.project/manager "user"
                 :thk.project/owner "user"
                 :file/author "user"
                 :task/assignee "user"
                 :task/author "user"
                 :task/status "enum status"
                 :task/type "enum type"
                 :activity/name "enum activity name"
                 :activity/status "enum activity status"
                 :thk.lifecycle/type "enum lifecycle type"
                 :user/permissions "List<permission>"
                 :user/licenses "List<user-license>"
                 :meta/creator "user"
                 :meta/modifier "user"
                 :comment/author "user"
                 :meeting/responsible "user"
                 :participation/participant "user"
                 :participation/in "any"
                 :comment/mentions "List<user>"
                 :comment/files "List<file>"
                 :meeting.agenda/decisions "List<meeting.decision>"
                 :meeting/agenda "List<meeting.agenda>"
                 :meeting/comments "List<comment>"
                 :land-acquisition/project "thk.project"
                 :notification/project "thk.project"
                 :notification/receiver "user"
                 :estate-procedure/compensations "List<estate-compensation>"
                 :estate-procedure/land-exchanges "List<land-exchange>"
                 :estate-procedure/process-fees "List<estate-process-fee>"
                 :estate-procedure/third-party-compensations "List<estate-process-fee>"
                 :estate-procedure/project "thk.project"
                 :meeting.agenda/responsible "user"
                 :cooperation.3rd-party/applications "List<cooperation.application>"
                 :thk.contract/targets "List<activity or task>"
                 :thk.contract/type "enum contract type"
                 :company-contract-employee/user "user"
                 :company-contract-employee/role "enum role"
                 :company-contract-employee/attached-licenses "List<user-license>"
                 :company-contract-employee/attached-files "List<file>"
                 :company-contract/employees "List<company-contract-employee>"
                 :company-contract/contract "thk.contract"
                 :company-contract/company "company"
                 :company-contract/sub-company-contracts "List<company-contract>"
                 :review/reviewer "user"
                 :review/decision "enum review decision"
                 :review/of "meeting or company-contract-employee"
                 :notification/status "enum status"
                 :notification/type "enum type"
                 :cost-index/values "List<index-value>"
                 :cost-index/type "enum index type"
                 }
          skip #{:thk.contract/authority-contact :thk.contract/partner-name
                 :company/emails :company/phone-numbers}
          entities (->>
                    (q '[:find (pull ?e [*])
                         :where [?e :db/valueType _]]
                       (db))
                    (map first)
                    (remove (comp skip :db/ident))
                    (group-by (comp namespace :db/ident)))]
      (str "@startuml\n"
           (str/join "\n"
                     (for [[entity-name _ :as entity] entities
                           :when (include-entities entity-name)]
                       (attrs->plantuml-entity entity types)))
           "thk.project ||-d-o{ thk.lifecycle\n"
           "thk.lifecycle ||-d-o{ activity\n"
           "activity ||-r-o{ task\n"
           "task ||-r-o{ file\n"
           "file ||--o{ comment\n"
           "permission ||--o{ thk.project\n"
           "meta --> user\n"
           "thk.project -r-> user\n"
           "user ||--o{ permission\n"
           "user ||--o{ \"user-license\"\n"
           "comment ||--o{ file\n"
           "task ||--o{ comment\n"
           "activity ||--o{ meeting\n"
           "meeting ||--o{ comment\n"
           "meeting ||--o{ meeting.agenda\n"
           "meeting.agenda ||--o{ meeting.decision\n"
           "meeting ||--o{ participation\n"
           "meeting.agenda --> user\n"
           "\"land-acquisition\" --> thk.project\n"
           "notification --> thk.project\n"
           "notification --> user\n"
           "\"estate-procedure\" ||--o{ \"estate-compensation\"\n"
           "\"estate-procedure\" ||--o{ \"estate-process-fee\"\n"
           "\"estate-procedure\" ||--o{ \"land-exchange\"\n"
           "\"estate-procedure\" --> thk.project\n"
           "\"cooperation.3rd-party\" --> thk.project\n"
           "\"cooperation.3rd-party\" ||--o{ cooperation.application\n"
           "cooperation.application --> cooperation.response\n"
           "cooperation.application --> cooperation.opinion\n"
           "thk.contract ||--o{ task\n"
           "thk.contract ||--o{ activity\n"
           "\"company-contract\" ||--o{ \"company-contract-employee\"\n"
           "\"company-contract\" --> company\n"
           "\"company-contract\" --> thk.contract\n"
           "\"company-contract\" ||--o{ \"company-contract\"\n"
           "\"company-contract-employee\" ||--o{ \"user-license\"\n"
           "review --> meeting\n"
           "review --> \"company-contract-employee\"\n"
           "review --> user\n"
           "\"company-contract-employee\" --> user\n"
           "comment --> user\n"
           "\"cost-index\" ||--o{ \"index-value\"\n"
           "note top of meta \n  meta fields are part of all entities but\n  modeled separately for convenience\nend note\n"

           "note top of notification\n   target may refer to any entity type\nend note\n"

           "\n@enduml")))

  (spit "entity-diagram.puml" (output-datomic-entity-diagram)))

(defn delete-all-imported-thk-projects! []
  (let [db (db)
        entity-ids #(into #{} (map first)
                          (q % db))
        projects (entity-ids '[:find ?e :where [?e :thk.project/id _]])
        lifecycles (entity-ids '[:find ?e :where [?e :thk.lifecycle/id _]])
        activities (entity-ids '[:find ?e :where [?e :thk.activity/id _]])]
    (println "Deleting THK entities: " (count projects) " projects, "
             (count lifecycles) " lifecycles"
             (count activities) " activities. Press enter to continue!")
    (read-line)
    (apply tx
           (for [id (concat projects lifecycles activities)]
             [:db/retractEntity id]))))

(defn delete-all-imported-thk-contracts! []
  (let [db (db)
        entity-ids #(into #{} (map first)
                          (q % db))
        contracts (entity-ids '[:find ?e :where [?e :thk.contract/procurement-id _]])]
    (println "Deleting THK contract entities: " (count contracts) " contracts, "
              "Press enter to continue!")
    (read-line)
    (apply tx
           (for [id contracts]
             [:db/retractEntity id]))))

(defn delete-all-imported-thk-tasks!
  "Special tasks imported from activities 4006 and 4009"
  []
  (let [db (db)
        entity-ids #(into #{} (map first)
                      (q % db))
        tasks (entity-ids '[:find ?e
                                :where (or [?e :task/type :task.type/owners-supervision]
                                         [?e :task/type :task.type/road-safety-audit])])]
    (println "Deleting THK task entities: " (count tasks) " tasks, "
      "Press enter to continue!")
    (read-line)
    (apply tx
      (for [id tasks]
        [:db/retractEntity id]))))


(defn keep-connection-alive
  "Starts a thread where a single datomic query is ran every minute to keep datomic proxy open."
  []
  (.scheduleWithFixedDelay
    (Executors/newScheduledThreadPool 1)
    #(q '[:find ?e :where [?e :db/ident :task.status/accepted]] (db))
    1 1 TimeUnit/MINUTES))

(defn reset-activity-status [act-id]
  ;; when testing submit / approve workflow this lets you walk back the status
  (let [status-res (d/pull (db) '[:activity/status] act-id)
        curr-status (-> status-res :activity/status :db/ident)]
    (when curr-status
      (tx
       {:db/id act-id
        :activity/status :activity.status/in-progress}))))


;;
;; Commands and queries from the REPL
;;
(def logged-user   tu/logged-user)
(def local-login   tu/local-login)
(defn local-query [& args]
  (binding [tu/*connection* (db-connection)
            tu/*asset-connection* (asset-connection)]
    (apply tu/local-query args)))

(defn local-command [& args]
  (binding [tu/*connection* (db-connection)
            tu/*asset-connection* (asset-connection)]
    (apply tu/local-command args)))

(defn pprint-file [filename output]
  (spit filename (with-out-str (clojure.pprint/pprint output))))

; (local-query :comment/fetch-comments {:for :task :db/id 34287170600567084})

(defn add-activity-manager [adding-user-id activity-id manager-user-id]
  (let [adding-user (ffirst (q '[:find (pull ?e [*])
                                 :in $ ?uid
                                 :where [?e :user/id ?uid]]
                               (db) adding-user-id))]
    (tx (merge {:db/id activity-id
                :activity/manager [:user/id manager-user-id]}
               (meta-model/modification-meta adding-user)))))

(defn user-by-person-id [person-id]
  (ffirst
       (q '[:find (pull ?e [*])
            :in $ ?wanted-pid
            :where [?e :user/person-id ?wanted-pid]] (db) person-id)))

(defn testenv-privilege-clears-20201007 []
  (let [;; person-ids ["EE94837264730"] ; dev
        person-ids ["EE60001018800" "EE60001019906" "EE10101010005"]  ;; test
        dry-run? false]
    (doseq [pid person-ids]
      (let [user (user-by-person-id pid)]
        (println "doing user" (:db/id user)  pid" found with" (count (:user/permissions user)) "permissions")
        (doseq [perm (:user/permissions user)]

          (println "remove-permission" (:user/id user) (:db/id perm))
          (when-not dry-run?
            (println
             (remove-permission (:user/id user)
                                (:db/id perm)))))))))


(def cloud-client {:server-type :ion
                   :region "eu-central-1"
                   :system "teet-datomic"
                   :endpoint "http://entry.teet-datomic.eu-central-1.datomic.net:8182/"
                   :proxy-port 8182})

(def local-client {:server-type :dev-local
                   :system "teet"})


(defn import-cloud [cloud-db-name local-db-name]
  (dl/import-cloud {:source (assoc cloud-client :db-name cloud-db-name)
                    :dest (assoc local-client :db-name local-db-name)}))

(defn- today-db-name [prefix]
  (let [c (java.util.Calendar/getInstance)]
    (format "%s%d%02d%02d" prefix
            (.get c java.util.Calendar/YEAR)
            (inc (.get c java.util.Calendar/MONTH))
            (.get c java.util.Calendar/DATE))))

(defn import-current-cloud-dbs
  "Import current teet and asset databases from dev.
  See backend/README.md for usage instructions."
  []
  (let [c (aws/client {:api :ssm})
        {cloud-teet-db-name "/teet/datomic/db-name"
         cloud-asset-db-name "/teet/datomic/asset-db-name"}
        (into {}
              (map (juxt :Name :Value))
              (:Parameters (aws/invoke c {:op :GetParameters
                                          :request {:Names ["/teet/datomic/db-name"
                                                            "/teet/datomic/asset-db-name"]}})))

        local-teet-db-name (today-db-name "teet")
        local-asset-db-name (today-db-name "asset")]
    (println "Importing databases from cloud:\n"
             "CLOUD: " cloud-teet-db-name " => LOCAL: " local-teet-db-name "\n"
             "CLOUD: " cloud-asset-db-name " => LOCAL: " local-asset-db-name "\n"
             "Press enter to continue or abort evaluation.")

    (when (read-line)
      (println "Importing TEET db")
      (import-cloud cloud-teet-db-name local-teet-db-name)
      (println "\nImporting asset db")
      (import-cloud cloud-asset-db-name local-asset-db-name)
      (println "\nDone. Remember to change config.edn to use new databases."))))

(defn update-project-geometries! []
  (project-geometry/update-project-geometries!
   (merge {:delete-stale-projects? true}
          (environment/config-map {:wfs-url [:road-registry :wfs-url]}))
   (map first
        (d/q '[:find (pull ?e [:db/id :integration/id
                               :thk.project/project-name :thk.project/name
                               :thk.project/road-nr
                               :thk.project/carriageway
                               :thk.project/start-m :thk.project/end-m
                               :thk.project/custom-start-m :thk.project/custom-end-m])
               :in $
               :where [?e :thk.project/road-nr _]]
             (db))))
  :ok)

(def ^:dynamic *time-level* 0)
(defmacro time-with-name [name expr]
  `(binding [*time-level* (inc *time-level*)]
     (let [n# ~name
           start# (System/currentTimeMillis)]
       (try
         ~expr
         (finally
           (println "TIME" (apply str (repeat *time-level* "--")) n# ": "
                    (- (System/currentTimeMillis) start#) "msecs"))))))


(defn log-level! [level]
  (log/merge-config! {:level level}))
