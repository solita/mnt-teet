 (ns ^:db teet.file.file-import-test
  (:require [teet.file.file-import :as file-import]
            [teet.integration.vektorio.vektorio-core :as vektorio-core]
            [teet.test.utils :as tu]
            [clojure.test :as t :refer [deftest is testing]]
            [teet.meta.meta-model :as meta-model]
            [teet.util.datomic :as du]))


(t/use-fixtures :each
  (tu/with-config {:file {:allowed-suffixes #{"png" "doc" "xls" "ifc"}
                          :image-suffixes #{"png" "jpg" "gif"}}})
  tu/with-environment
  tu/with-global-data
  (tu/with-db))

(deftest vektorio-import-for-old-files
  (tu/local-login tu/mock-user-boss)
  (let [activity-id (tu/->db-id "p1-lc1-act1")
        activity-entity (du/entity (tu/db) activity-id)
        task-id (tu/create-task {:activity activity-id
                                 :task {:task/group :task.group/land-purchase
                                        :task/type :task.type/plot-allocation-plan
                                        :task/assignee {:user/id (second tu/mock-user-edna-consultant)}
                                        :task/estimated-start-date (:activity/estimated-start-date activity-entity)
                                        :task/estimated-end-date (:activity/estimated-end-date activity-entity)}})
        old-file (tu/fake-upload task-id
                                 (merge (meta-model/creation-meta tu/mock-user-edna-consultant)
                                        #:file {:name "model0.ifc" :file/size 666}
                                        {:meta/created-at #inst "2012-12-12T12:12"}))
        
        old-file-2 (tu/fake-upload task-id
                                 (merge (meta-model/creation-meta tu/mock-user-edna-consultant)
                                        #:file {:name "model1.ifc" :file/size 666}
                                        {:vektorio/model-id 42}
                                        {:meta/created-at #inst "2012-12-12T12:12"}))
        
        new-file (tu/fake-upload task-id
                                 (merge (meta-model/creation-meta tu/mock-user-edna-consultant)
                                        #:file {:name "model2.ifc" :file/size 667}))
        mock-upload-log (atom [])]
    ;; 1. mock 2 files, one over and one under the threshold that are missing model-ids
    ;; 1.5. plus one  file that already has model id
    ;; 2. call scheduled-import fn
    ;; 3. check that only the older file without model id is uploaded
    (with-redefs [vektorio-core/upload-file-to-vektor!
                  (fn mock-upload-fn [db-conn vektor-config file-eid]
                    (swap! mock-upload-log conj file-eid))]
      (file-import/scheduled-file-import* (tu/connection)))
    ;; only old file without :vektorio/model-id is uploaded
    (= 1 (count @mock-upload-log))
    (is (some? (:db/id old-file)))
    (= (:db/id old-file) (first @mock-upload-log))))
