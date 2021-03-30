(ns ^:db teet.file.file-export-test
  (:require [teet.file.file-export :as file-export]
            [teet.test.utils :as tu]
            [clojure.test :as t :refer [deftest is testing]]
            [teet.util.datomic :as du]
            [teet.integration.integration-s3 :as integration-s3]
            [clojure.java.io :as io]
            [teet.localization :refer [with-language]]))

(t/use-fixtures :each
  (tu/with-config {:file {:allowed-suffixes #{"png" "doc" "xls"}
                          :image-suffixes #{"png" "jpg" "gif"}}})
  tu/with-environment
  tu/with-global-data
  (tu/with-db))

(defn- read-entry [in]
  ;; slurp closes the zip input so we can't use that
  (let [out (java.io.ByteArrayOutputStream.)]
    (io/copy in out)
    (String. (.toByteArray out))))

(deftest export-task
  ;; We can't actually read files from the input stream because the fake uploaded files
  ;; don't have any content in S3, but we can redef the get-object call to give us some fake
  ;; data for it
  (tu/local-login tu/mock-user-boss)
  (with-redefs [integration-s3/get-object
                (fn [_bucket file-key]
                  (java.io.ByteArrayInputStream. (.getBytes file-key "UTF-8")))]
    (let [activity-id (tu/->db-id "p1-lc1-act1")
          activity-entity (du/entity (tu/db) activity-id)
          task-id (tu/create-task {:activity activity-id
                                   :task {:task/group :task.group/land-purchase
                                          :task/type :task.type/plot-allocation-plan
                                          :task/assignee {:user/id (second tu/mock-user-edna-consultant)}
                                          :task/estimated-start-date (:activity/estimated-start-date activity-entity)
                                          :task/estimated-end-date (:activity/estimated-end-date activity-entity)}})
          file (tu/fake-upload task-id #:file {:name "image.png" :file/size 666})
          s3-file-key (:file/s3-key (du/entity (tu/db) (:db/id file)))
          {:keys [filename input-stream]} (with-language :en (file-export/task-zip (tu/db) task-id))]
      ;; MO = land acqusiiton, KY = plot allocation plan
      (is (= filename "MA11111_MO_TL_KY.zip"))
      (let [in (java.util.zip.ZipInputStream. input-stream)
            entry (.getNextEntry in)]
        (is (= (.getName entry) "00_General/MA11111_MO_TL_KY_00_image.png"))
        (is (= s3-file-key (read-entry in)))
        (is (nil? (.getNextEntry in)))))))

(deftest handle-duplicate-filenames
  ;; from TEET-1479 steps to reproduce:
  ;; 1.
  ;;   a Create a task under an activity
  ;;   b upload a file to it
  ;;   c submit the task for review
  ;;   d and accept the task (task status = Completed).
  ;; 2. a Add the same task again under the same activity,
  ;;    b upload the same file to it (the TEET filenames will match for both files)
  ;; 3. Request the activity zip download link and download the zip
  ;; 
  (tu/local-login tu/mock-user-boss)

  ;; 1a
  (let [activity-id (tu/->db-id "p1-lc1-act1")        
        activity-entity (du/entity (tu/db) activity-id)
        task-params {:activity activity-id
                     :task {:task/group :task.group/land-purchase
                            :task/type :task.type/plot-allocation-plan
                            :task/assignee {:user/id (second tu/mock-user-edna-consultant)}
                            :task/estimated-start-date (:activity/estimated-start-date activity-entity)
                            :task/estimated-end-date (:activity/estimated-end-date activity-entity)}}
        first-task-id (tu/create-task task-params)
        ;; 1b
        file (tu/fake-upload first-task-id #:file {:name "image.png" :file/size 666})
        ]
    ;; 1c
    (tu/local-command :task/submit {:task-id first-task-id})
    (tu/local-command :task/start-review {:task-id first-task-id})
    ;; 1d - accept
    (tu/local-command :task/review {:task-id first-task-id
                                    :result :accept} )
    ;; 2a
    (with-redefs [integration-s3/get-object
                (fn [_bucket file-key]
                  (java.io.ByteArrayInputStream. (.getBytes file-key "UTF-8")))]
      (let [second-task-id (tu/create-task task-params)
            ;; 2b
            file (tu/fake-upload second-task-id #:file {:name "image.png" :file/size 666})
            s3-file-key (:file/s3-key (du/entity (tu/db) (:db/id file)))
            {:keys [filename input-stream]} (with-language :en (file-export/activity-zip (tu/db) activity-id))]
        (let [in (java.util.zip.ZipInputStream. input-stream)
              first-entry (.getNextEntry in)
              second-entry (.getNextEntry in)]
          (is (= "KY/00_General/MA11111_MO_TL_KY_00_image.png" (.getName first-entry) ))
          (is (= "KY/00_General/MA11111_MO_TL_KY_00_image (1).png" (some-> second-entry .getName)))
          (is (nil? (.getNextEntry in))))))))
