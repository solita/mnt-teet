(ns teet.file.file-export
  "Export task or activity files as a zip file."
  (:require [teet.file.file-db :as file-db]
            [teet.integration.integration-s3 :as integration-s3]
            [teet.task.task-db :as task-db]
            [datomic.client.api :as d]
            [teet.file.filename-metadata :as filename-metadata]
            [teet.localization :refer [tr with-language]]
            [teet.file.file-storage :as file-storage]
            [ring.util.io :as ring-io]
            [clojure.java.io :as io])
  (:import (java.util.zip ZipOutputStream ZipEntry)))

(defn task-zip-entries
  "Return zip file entries for current versions of all files in the given task."
  ([db task-id] (task-zip-entries db task-id ""))
  ([db task-id folder-prefix]
   (let [file-ids (task-db/latest-task-files db task-id)
         files (map #(merge
                      (d/pull db [:db/id :file/s3-key] %)
                      (file-db/file-metadata-by-id db %))
                    file-ids)
         part-names (into {"00" (tr [:file-upload :general-part])}
                          (map #(update % 0 (partial format "%02d")))
                          (d/q '[:find ?num ?name
                                 :in $ [?file ...]
                                 :where
                                 [?file :file/part ?part]
                                 [?part :file.part/number ?num]
                                 [?part :file.part/name ?name]]
                               db file-ids))]
     (for [{:keys [part document-group] :as f} files
           :let [name (filename-metadata/metadata->filename f)]]
       {:folder (str folder-prefix part "_" (part-names part)
                     (when document-group
                       (str "/" document-group)))
        :name name
        :input #(let [{:keys [bucket file-key]} (file-storage/document-s3-ref f)]
                  (integration-s3/get-object bucket file-key))}))))

(defn- zip-stream
  "Create an input stream where the zip file can be streamed from."
  [entries]
  (ring-io/piped-input-stream
   (fn [ostream]
     (with-open [out (ZipOutputStream. ostream)]
       (doseq [{:keys [folder name input]} entries]
         (.putNextEntry out (ZipEntry. (str (when folder
                                              (str folder "/"))
                                            name)))
         (io/copy (input) out))))))

(defn task-zip
  "Create zip from all the files in a task. Returns map with :filename for the zip
  and :input-stream where the zip file can be read from."
  [db task-id]
  (with-language :et
    (let [export-info
          (d/pull db
                  '[;; task filename code
                    {:task/type [:filename/code]}

                    {:activity/_tasks
                     [;; activity filename code
                      {:activity/name [:filename/code]}

                      {:thk.lifecycle/_activities
                       [;; project THK id
                        {:thk.project/_lifecycles [:thk.project/id]}]}]}]
                  task-id)
          filename (str "MA" (get-in export-info [:activity/_tasks 0
                                                  :thk.lifecycle/_activities 0
                                                  :thk.project/_lifecycles 0
                                                  :thk.project/id])
                        "_" (get-in export-info [:activity/_tasks 0 :activity/name :filename/code])
                        "_TL"
                        "_" (get-in export-info [:task/type :filename/code])
                        ".zip")]
      {:filename filename
       :input-stream (zip-stream (task-zip-entries db task-id))})))

(defn activity-zip
  "Create zip from all the files in all tasks for an activity.
  Returns map with :filename for the zip and :input-stream where the zip file can be read from."
  [db activity-id]
  (with-language :et
    (let [export-info
          (d/pull db
                  '[{:activity/name [:filename/code]}
                    {:thk.lifecycle/_activities [{:thk.project/_lifecycles [:thk.project/id]}]}]
                  activity-id)
          filename (str "MA" (get-in export-info [:thk.lifecycle/_activities 0
                                                  :thk.project/_lifecycles 0
                                                  :thk.project/id])
                        "_" (get-in export-info [:activity/name :filename/code])
                        "_TL.zip")
          tasks (d/q '[:find ?task ?code
                       :where
                       [?act :activity/tasks ?task]
                       [(missing? $ ?task :meta/deleted?)]
                       [?task :task/type ?type]
                       [?type :filename/code ?code]
                       :in $ ?act]
                     db activity-id)]
      {:filename filename
       :input-stream (zip-stream (mapcat (fn [[task-id code]]
                                           (task-zip-entries db task-id (str code "/")))
                                         tasks))})))