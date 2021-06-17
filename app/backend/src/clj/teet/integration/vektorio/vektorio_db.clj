(ns teet.integration.vektorio.vektorio-db
  (:require [datomic.client.api :as d]))

(defn activity-and-task-info
  "Returns the task and activity information for file-id to be used in Vektor.io filepath"
  [db file-id]
  (first (d/q '[:find ?activity-code ?activity-desc ?task-code ?task-desc
                :in $ ?file
                :keys activity-code activity-name task-code task-name
                :where
                [?task :task/files ?file]
                [?task :task/type ?type]
                [?activity :activity/tasks ?task]
                [?activity :activity/name ?activity-name]
                [?activity-name :db/ident ?activity-desc]
                [?activity-name :filename/code ?activity-code]
                [?type :filename/code ?task-code]
                [?type :db/ident ?task-desc]]
              db file-id)))

(defn file-part-info
  "Returns the file part info (if exists) to be used in Vektor.io filepath"
  [db file-id]
  (first (d/q '[:find ?part-name ?part-number
                :in $ ?file
                :keys part-name part-number
                :where
                [?file :file/part ?file-part]
                [?file-part :file.part/name ?part-name]
                [?file-part :file.part/number ?part-number]]
              db file-id)))