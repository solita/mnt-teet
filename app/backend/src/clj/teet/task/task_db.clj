(ns teet.task.task-db
  "Task related queries"
  (:require [datomic.client.api :as d]))


(defn files-for-task
  "Returns files for a given task. Returns latest versions of files as vector
  and adds all previous versions of a files as :versions key."
  [db task-eid]
  (let [files (mapv first
                    (d/q '[:find (pull ?f [*
                                           {:file/previous-version [:db/id]}
                                           {:meta/creator [:user/id :user/family-name :user/given-name]}])
                           :where [?t :task/files ?f]
                           :in $ ?t] db task-eid))
        ;; Group files to first versions and next versions
        {next-versions true
         first-versions false}
        (group-by (comp boolean :file/previous-version) files)

        ;; Find next version for given file
        next-version (fn [file]
                       (some #(when (= (:db/id file)
                                       (get-in % [:file/previous-version :db/id])) %)
                             next-versions))]
    (vec
     (for [f first-versions
           :let [[latest-version & previous-versions]
                 (reverse
                  (take-while some? (iterate next-version f)))]]
       (assoc latest-version
              :versions previous-versions)))))
