(ns teet.migration.road-safety-task-type
  (:require [datomic.client.api :as d]
            [teet.util.datomic :as du]            
            [teet.task.task-db :as task-db]
            [teet.activity.activity-db :as activity-db]))

(defn activity-for-task-id [db task-id]
  (ffirst (d/q '[:find ?activity
                 :in $ ?t
                 :where
                 [?activity :activity/tasks ?t]]
               db task-id)))


(defn parent-is-design? [db task]
  (let [activity-eid (task-db/activity-for-task-id db (:db/id task))
        activity-entity (du/entity db activity-eid)
        activity-name (-> activity-entity :activity/name :db/ident)
        lifecycle-eid (activity-db/lifecycle-id-for-activity-id db activity-eid)
        lc-type (-> (du/entity db lifecycle-eid) :thk.lifecycle/type :db/ident)]
    
    (println "ac name for task" (:db/id task) "parent is" activity-name)   
    (= :thk.lifecycle-type/design lc-type)))

(defn use-design-road-safety-audit [conn]  
  (let [db (d/db conn)
        r (d/q '[:find (pull ?t [:db/id :task/type])
                 :where [?t :task/type _]]
               db)
        result (mapv first r)
        tx-data (vec (for [task (map first r)
                           :when (parent-is-design? db task)]
                       {:db/id (:db/id task)
                        :thk.task-type :thk.task-type/design-road-safety-audit}))]
    (clojure.pprint/pprint tx-data)))

;; TEET-1307 road-safety-audit task thk/code mapping problem todo / notes
;; - we first thought that in migrations we have 2 conflicting idents: :thk.task-type/road-safety-audit :thk/code "4011" and a later :thk.task-type/road-safety-audit :thk/code "4009"
;; - we actually need these to be separate codes and to coexist: 4011 for designand 4009 for construction
;;    - later found out that we actually do have 2 separate idents, but mistakenly the thk code for design was applied to the construction side task type in a migration:
;;    - there's design-road-safety-audit task type that has translations and is valid for design-approval group
;;    -> actually this migration seems unnecessary
;; - then generate thk export csv and verify results
;; - write test?
