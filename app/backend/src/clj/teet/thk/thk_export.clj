(ns teet.thk.thk-export
  "Export project lifecycle and activity data to THK"
  (:require [datomic.client.api :as d]
            [teet.activity.activity-model :as activity-model]
            [teet.thk.thk-mapping :as thk-mapping]
            [teet.meta.meta-query :as meta-query]))

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
        (d/q '[:find (pull ?e [*
                               {:task/type [*
                                            {:thk/task-type [:thk/code]}]}])
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

(defn- without-deleted [db entities]
  (meta-query/without-deleted db entities
                              ;; Keep activities, even if they are deleted
                              #(some? (:thk.activity/id %))))

(defn export-thk-projects [connection]
  (let [db (d/db connection)
        projects (->> db
                      all-projects
                      (without-deleted db)
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
           activity (filter activity-model/exported-to-thk?
                            (:thk.lifecycle/activities lifecycle))

           ;; THK only has activities, so activities and tasks under it are sent as activity rows
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
                 (str (thk-mapping/uuid->number (:integration/id lifecycle)))
                 "activity_teetid"
                 (str (thk-mapping/uuid->number (:integration/id activity)))

                 ;; Regular columns
                 (let [{task-mapping :task :as mapping}
                       (thk-mapping/thk->teet csv-column)

                       {:keys [attribute format override-kw]}
                       (if (or (= activity activity-or-task)
                               (nil? task-mapping))
                         ;; This is an activity row and column has
                         ;; override or column has no override for tasks
                         mapping

                         ;; This is a task row, use override values
                         (merge mapping task-mapping))

                       ;; If task mapping overrides column, use task data
                       data (if task-mapping
                              activity-or-task
                              data)

                       format (or format str)
                       value (if override-kw
                               (override-kw data (attribute data))
                               (attribute data))]
                   #_(when (and (not= activity activity-or-task) task-mapping)
                     (log/info "COLUMN " csv-column " HAS task " attribute
                               "VALUE: " value))
                   (if value
                     (format value)
                     ""))))
             thk-mapping/csv-column-names)))))
