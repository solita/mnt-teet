(ns teet.workflow.workflow-schema
  "Datomic schema for workflow information"
  (:require [datomic.client.api :as d]))

(def workflow-schema
  [{:db/ident :workflow/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Name of this workflow"}

   {:db/ident :thk/id
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The THK projet id a workflow is associated with"}

   {:db/ident :workflow/due-date
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one
    :db/doc "The planned due date"}

   {:db/ident :workflow/phases
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many}])

(def phase-schema
  [{:db/ident :phase/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident :phase/ordinality
    :db/valueType :db.type/long
    :db/cardinality :db.cardinality/one}

   {:db/ident :phase/due-date
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one}

   {:db/ident :phase/tasks
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many}])

(def task-schema
  [{:db/ident :task/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident :task/description
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}

   {:db/ident :task/assignee
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}

   {:db/ident :task/status
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}])


(def task-status-schema
  [;; Status update entities
   {:db/ident :task.status/timestamp
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one}

   {:db/ident :task.status/status
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}

   {:db/ident :task.status/user
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}

   {:db/ident :task.status/comment
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}])

(def task-statuses
  [{:db/ident :task.status/not-started}
   {:db/ident :task.status/in-progress}
   {:db/ident :task.status/completed}
   {:db/ident :task.status/accepted}
   {:db/ident :task.status/rejected}])


(def client
  (d/client
   {:server-type :ion
    :region "eu-central-1"
    :system "teet-dev-datomic"
    :creds-profile "default"
    :endpoint "http://entry.teet-dev-datomic.eu-central-1.datomic.net:8182/"
    :proxy-port 8182}
   #_{:server-type :peer-server
    :endpoint "localhost:8998"
    :secret "mysecret"
    :access-key "myaccesskey"
    :validate-hostnames false}))


(def conn (d/connect client {:db-name "tatu-test-1"}))


(def test-data
  [{:db/id "wf1"
    :workflow/name "Implement TEET"
    :workflow/due-date #inst "2020-04-08T05:52:02.511-00:00"
    :workflow/phases [{:db/id "p1"
                       :phase/name "Planning"
                       :phase/due-date #inst "2019-11-08T05:52:02.511-00:00"
                       :phase/tasks [{:db/id "p1-t1"
                                      :task/name "do some initial planning"
                                      :task/status [:db/ident :task.status/completed]}
                                     {:db/id "p1-t2"
                                      :task/name "finalize plans"
                                      :task/status [:db/ident :task.status/in-progress]}]}

                      {:db/id "p2"
                       :phase/name "Implementation"
                       :phase/due-date  #inst "2020-03-22T05:52:02.511-00:00"
                       :phase/tasks [{:db/id "p2-t1"
                                      :task/name "write the code"
                                      :task/status [:db/ident :task.status/not-started]}]}]}])

(defn tx-schema []
  (d/transact conn {:tx-data (concat workflow-schema
                                     phase-schema
                                     task-schema
                                     task-status-schema)})
  (d/transact conn {:tx-data task-statuses}))

(def q-test
  (let [db (d/db conn)
        id (ffirst (d/q '[:find ?e :where [?e :workflow/name "Implement TEET"]] db))]
    (d/pull db
            [:workflow/name {:workflow/phases [:phase/name
                                               {:phase/tasks [:task/name
                                                              {:task/status [:db/ident]}]}]}]
            id)))

(defn update-status! [task-name new-status]
  (let [db (d/db conn)
        id (ffirst (d/q '[:find ?e
                          :in $ ?task
                          :where [?e :task/name ?task]] db task-name))]
    (d/transact conn {:tx-data [{:db/id id
                                 :task/current-status [:task.status/name new-status]}]})))

(comment
  #:workflow{:name "Implement TEET",
           :phases
           [#:phase{:name "Planning",
                    :tasks
                    [#:task{:name "do some initial planning",
                            :current-status #:db{:id 17592186045431}}
                     #:task{:name "finalize plans",
                            :current-status
                            #:db{:id 17592186045430}}]}
            #:phase{:name "Implementation",
                    :tasks
                    [#:task{:name "write the code",
                            :current-status
                            #:db{:id 17592186045429}}]}]})
