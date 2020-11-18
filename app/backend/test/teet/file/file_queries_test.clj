(ns ^:db teet.file.file-queries-test
  (:require [clojure.test :refer [use-fixtures deftest is testing]]
            [teet.test.utils :as tu]
            teet.file.file-queries))

(use-fixtures :each
  tu/with-global-data
  tu/with-environment
  (tu/with-db))

(deftest file-metadata-resolve
  (tu/local-login tu/mock-user-boss)

  (testing "Metadata for incorrectly formatted filename is empty"
    (is (= {} (tu/local-query :file/resolve-metadata
                              {:file/name "you_can't_parse_this.mp3"}))))

  (testing "Filename for p1 is resolved"
    ;; create task plot allocation plan (KY) in land acquision activity (MO)
    (let [task-id (tu/create-task
                   {:user tu/mock-user-boss
                    :activity (tu/->db-id "p1-lc1-act1")
                    :task {:task/type :task.type/plot-allocation-plan
                           :task/group :task.group/land-purchase}})
          metadata
          (tu/local-query :file/resolve-metadata
                          {:file/name "MA11111_MO_TL_KY_00_1-01_this is my file.pdf"})]
      (is (= {:description "this is my file"
              :extension "pdf"
              :activity-id (tu/->db-id "p1-lc1-act1")
              :task-id task-id
              :file-id nil
              :project-eid [:thk.project/id "11111"]
              :part "00"
              :sequence-number 1
              :task "KY"
              :activity "MO"}
             (select-keys metadata [:description :extension
                                    :activity-id :task-id :file-id
                                    :project-eid
                                    :part :sequence-number
                                    :task :activity])))))

  (testing "Filename for non-existant project is still parsed"
    (let [metadata (tu/local-query :file/resolve-metadata
                                   {:file/name "MA998877_MO_TL_KY_00_1-01_this is my file.pdf"})]
      (is (= {:description "this is my file"
              :extension "pdf"
              :activity-id nil
              :task-id nil
              :file-id nil
              :project-eid [:thk.project/id "998877"]
              :part "00"
              :sequence-number 1
              :task "KY"
              :activity "MO"}
             (select-keys metadata [:description :extension
                                    :activity-id :task-id :file-id
                                    :project-eid
                                    :part :sequence-number
                                    :task :activity]))))))
