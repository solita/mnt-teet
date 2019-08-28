(ns teet.workflow.workflow-schema
  "Datomic schema for workflow information"
  (:require [datomic.client.api :as d]))




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

(comment
  (def q-test
    (let [db nil
          id (ffirst (d/q '[:find ?e :where [?e :workflow/name "Implement TEET"]] db))]
      (d/pull db
              [:workflow/name {:workflow/phases [:phase/name
                                                 {:phase/tasks [:task/name
                                                                {:task/status [:db/ident]}]}]}]
              id))))

(defn update-status! [conn task-name new-status]
  (let [db  (d/db conn)
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
