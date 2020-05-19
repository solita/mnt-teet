(ns user
  (:require [datomic.client.api :as d]
            [teet.main :as main]
            [teet.environment :as environment]
            [teet.test.utils :as tu]
            [teet.thk.thk-integration-ion :as thk-integration]
            [clojure.string :as str])
  (:import (java.util Date)
           (java.util.concurrent TimeUnit Executors)))

(defn go []
  (main/restart)
  (environment/datomic-connection))

(def restart go)

(def db-connection environment/datomic-connection)

(defn db []
  (d/db (db-connection)))

(def q d/q)
(def pull d/pull)

(defn entity [eid]
  (pull (db) '[*] eid))

(defn tx
  "Transact given maps to db"
  [& maps]
  (d/transact (environment/datomic-connection)
              {:tx-data (into [] maps)}))

(def mock-users tu/mock-users)

(def danny-uuid (-> mock-users first :user/id))

(defn give-admin-permission
  [user-eid]
  (tx {:db/id            user-eid
       :user/permissions [{:db/id                 "new-permission"
                           :user/roles :admin
                           :permission/role       :admin
                           :permission/valid-from (Date.)}]}))

(defn give-manager-permission
  [user-eid]
  (tx {:db/id            user-eid
       :user/permissions [{:db/id                 "new-permission"
                           :permission/role       :manager
                           :permission/valid-from (Date.)}]}))

(defn give-external-consultant-permission
  [user-eid]
  (tx {:db/id            user-eid
       :user/permissions [{:db/id                 "new-permission"
                           :permission/role       :external-consultant
                           :permission/valid-from (Date.)}]}))

(defn remove-permission [user-uuid permission-eid]
  (d/transact (environment/datomic-connection)
              {:tx-data [[:db/retract [:user/id user-uuid]
                          :user/permissions permission-eid]]}))

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
  (apply tx mock-users))

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
  (environment/migrate (db-connection) true))

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

(defn output-datomic-entity-diagram []
  (let [include-entities #{"thk.project" "thk.lifecycle" "activity"
                           "task" "document" "file" "comment" "permission"
                           "user" "meta"}
        types {:permission/projects "List<thk.project>"
               :thk.project/lifecycles "List<thk.lifecycle>"
               :thk.lifecycle/activities "List<activity>"
               :activity/tasks "List<task>"
               :task/documents "List<document>"
               :document/files "List<file>"
               :document/comments "List<comment>"
               :file/comments "List<comment>"
               :thk.project/manager "user"
               :thk.project/owner "user"
               :file/author "user"
               :task/assignee "user"
               :task/author "user"
               :task/comments "List<comment>"
               :task/status "enum status"
               :task/type "enum type"
               :activity/name "enum activity name"
               :activity/status "enum activity status"
               :thk.lifecycle/type "enum lifecycle type"
               :user/permissions "List<permission>"
               :meta/creator "user"
               :meta/modifier "user"
               :document/category "enum category"
               :document/sub-category "enum sub-category"
               :document/status "enum status"
               :comment/author "user"}
        entities (->>
                  (q '[:find (pull ?e [*])
                       :where [?e :db/valueType _]]
                     (db))
                  (map first)
                  (group-by (comp namespace :db/ident)))]
    (str "@startuml\n"
         (str/join "\n"
                   (for [[entity-name _ :as entity] entities
                         :when (include-entities entity-name)]
                     (attrs->plantuml-entity entity types)))
         "thk.project ||-d-o{ thk.lifecycle\n"
         "thk.lifecycle ||-d-o{ activity\n"
         "activity ||-r-o{ task\n"
         "task ||-r-o{ document\n"
         "document ||--o{ comment\n"
         "file ||--o{ comment\n"
         "document ||-r-o{ file\n"
         "permission ||--o{ thk.project\n"
         "meta --> user\n"
         "thk.project -r-> user\n"
         "user ||--o{ permission\n"

         "note left of meta \n  meta fields are part of all entities but\n  modeled separately for convenience\nend note\n"
         "\n@enduml")))

(comment
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
(def local-query   tu/local-query)
(def local-command tu/local-command)

(defn pprint-file [filename output]
  (spit filename (with-out-str (clojure.pprint/pprint output))))

; (local-query :comment/fetch-comments {:for :task :db/id 34287170600567084})
