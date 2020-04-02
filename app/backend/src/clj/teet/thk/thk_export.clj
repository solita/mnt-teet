(ns teet.thk.thk-export
  "Export project lifecycle and activity data to THK"
  (:require [datomic.client.api :as d]
            [teet.thk.thk-mapping :as thk-mapping]))

(defn- all-projects [db]
  (d/q '[:find (pull ?e [*
                         {:thk.project/owner [:user/person-id]}
                         {:thk.project/lifecycles
                          [*
                           {:thk.lifecycle/activities
                            [*]}]}])
         :where [?e :thk.project/id _]]
       db))

(defn- tasks-to-send [db activity-id]
  (mapv first
        (d/q '[:find (pull ?e [*])
               :where
               [?activity :activity/tasks ?e]
               [?e :task/send-to-thk? true]
               :in $ ?activity]
             db activity-id)))

(defn read-integration-info [info-str]
  (when info-str
    (binding [*read-eval* false]
      (read-string info-str))))

(defn find-last-tx-of [db id]
  (ffirst
   (d/q '[:find (max ?txi)
          :where
          [?e _ _ ?tx]
          [?tx :db/txInstant ?txi]
          :in $ ?e]
        db id)))

(defn export-thk-projects [connection]
  (let [db (d/db connection)
        projects (->> db
                      all-projects

                      ;; Take the pulled map from each result
                      (map first)

                      ;; Sort projects by THK project id (as number)
                      ;; This outputs them in the same order as THK->TEET
                      ;; for easier comparison
                      (sort-by (comp #(Integer/parseInt %) :thk.project/id)))]
    (into
     [thk-mapping/csv-column-names]
     (for [project projects
           lifecycle (:thk.project/lifecycles project)
           activity (:thk.lifecycle/activities lifecycle)
           activity-or-task (into [activity] (tasks-to-send db (:db/id activity)))
           :let [data (merge project lifecycle activity
                             (read-integration-info (:thk.project/integration-info project))
                             (read-integration-info (:thk.lifecycle/integration-info lifecycle))
                             (read-integration-info (:activity/integration-info activity)))]]
       (mapv (fn [csv-column]
               (case csv-column
                 ;; Fetch TEET update timestamps from tx info
                 "object_teetupdstamp"
                 (thk-mapping/datetime-str (find-last-tx-of db (:db/id project)))
                 "phase_teetupdstamp"
                 (thk-mapping/datetime-str (find-last-tx-of db (:db/id lifecycle)))
                 "activity_teetupdstamp"
                 (thk-mapping/datetime-str (find-last-tx-of db (:db/id activity)))

                 ;; TEET id for phase and activity
                 "phase_teetid"
                 (str (:db/id lifecycle))
                 "activity_teetid"
                 (str (:db/id activity))

                 ;; Id for task id (only when a task is being sent to THK)
                 "activity_taskid"
                 (if (= activity-or-task activity)
                   ""
                   (str (:db/id activity-or-task)))

                 ;; Regular columns
                 (let [[teet-kw _ fmt override-kw] (thk-mapping/thk->teet csv-column)
                       fmt (or fmt str)
                       value (if override-kw
                               (get data override-kw (get data teet-kw))
                               (get data teet-kw))]
                   (if value
                     (fmt value)
                     ""))))
             thk-mapping/csv-column-names)))))
