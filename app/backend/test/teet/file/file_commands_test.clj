(ns ^:db teet.file.file-commands-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [teet.test.utils :as tu]
            [teet.file.file-model :as file-model]
            [teet.file.file-storage :as file-storage]
            [clojure.string :as str]
            [datomic.client.api :as d]))

(use-fixtures :each
  (tu/with-config {:file {:allowed-suffixes #{"png" "doc" "xls"}
                          :image-suffixes #{"png" "jpg" "gif"}}})
  tu/with-environment
  (tu/with-db)
  tu/with-global-data)

(deftest validate-document
  (testing "too large files are invalid"
    (is (= (:error (file-model/validate-file {:file/name "image.png"
                                              :file/size (* 1024 1024 3001)}))
           :file-too-large)))
  (testing "files with illegal type suffix are invalid"
    (is (= (:error (file-model/validate-file {:file/name "myfile.nonsense"
                                              :file/size 1024}))
           :file-type-not-allowed)))
  (testing "other files are valid"
    (is (nil? (file-model/validate-file
               {:file/name (str "myfile." (rand-nth (seq (file-model/upload-allowed-file-suffixes))))
                :file/size (rand (inc file-model/upload-max-file-size))})))))

(deftest file-uuid
  (tu/local-login tu/mock-user-boss)
  (with-redefs [file-storage/upload-url (fn [name]
                                          (str "UPLOAD:" name))]
    (tu/store-data!
     :task-id
     (tu/create-task {:user tu/mock-user-boss
                      :activity (tu/->db-id "p1-lc1-act1")
                      :task {:task/type :task.type/third-party-review
                             :task/group :task.group/land-purchase}}))

    (is (tu/get-data :task-id) "Task was created")

    (testing "Uploading new file gets UUID after completion"
      (let [{:keys [url task-id file]}
            (tu/local-command :file/upload
                              {:task-id (tu/get-data :task-id)
                               :file {:file/name "image.png"
                                      :file/size 666}})]
        (is (str/starts-with? url "UPLOAD:"))
        (is (str/ends-with? url "image.png"))
        (is (= task-id (tu/get-data :task-id)))
        (is (:db/id file))
        (is (not (contains? file :file/id)))

        ;; Mark upload as complete
        (let [{id :db/id file-id :file/id :as original}
              (tu/local-command :file/upload-complete
                                {:db/id (:db/id file)})]
          (tu/store-data! :original-file original)
          (is (= id (:db/id file)))
          (is (uuid? file-id)))))

    (testing "Uploading replacement moves id"
      (let [{:keys [url file]}
            (tu/local-command :file/replace
                              {:task-id (tu/get-data :task-id)
                               :file {:file/name "replacement.png"
                                      :file/size 420}
                               :previous-version-id (:db/id (tu/get-data :original-file))})]
        (is (str/starts-with? url "UPLOAD:"))
        (is (str/ends-with? url "replacement.png"))
        (is (:db/id file))

        ;; Mark upload complete moves previous UUID to latest version
        (let [{id :db/id file-id :file/id :as replacement}
              (tu/local-command :file/upload-complete
                                {:db/id (:db/id file)})]
          (tu/store-data! :replacement-file replacement)
          (is (uuid? file-id))
          (is (= file-id
                 (:file/id (tu/get-data :original-file)))))))

    (testing "database has correct info"
      (let [f (d/pull (tu/db)
                      '[:db/id :file/id
                        {:file/previous-version [:db/id :file/id]}]
                      (:db/id (tu/get-data :replacement-file)))]
        (is (:file/id f))
        (is (nil? (get-in f [:file/previous-version :file/id])))))))
