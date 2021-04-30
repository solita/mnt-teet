(ns user
  (:require [datomic.client.api :as d]
            [teet.main :as main]
            [teet.meta.meta-model :as meta-model]
            [teet.environment :as environment]
            [teet.test.utils :as tu]
            [teet.thk.thk-integration-ion :as thk-integration]
            [clojure.string :as str]
            [clojure.walk :as walk])
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

(comment
  (defn output-datomic-entity-diagram []
    (let [include-entities #{"thk.project" "thk.lifecycle" "activity"
                             "task" "file" "comment" "permission"
                             "user" "meta"
                             "meeting" "meeting.agenda" "meeting.decision"
                             "participation" "land-acquisition" "notification"
                             "estate-procedure" "estate-compensation"
                             "estate-process-fee" "land-exchange"
                             "cooperation.3rd-party"
                             "cooperation.application"
                             "cooperation.response"
                             "cooperation.opinion"}
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
                 :cooperation.3rd-party/applications "List<cooperation.application>"}
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
           "task ||-r-o{ file\n"
           "file ||--o{ comment\n"
           "permission ||--o{ thk.project\n"
           "meta --> user\n"
           "thk.project -r-> user\n"
           "user ||--o{ permission\n"
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

           "note top of meta \n  meta fields are part of all entities but\n  modeled separately for convenience\nend note\n"
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
