(ns ^:db teet.file.file-commands-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [teet.test.utils :as tu]
            [teet.file.file-model :as file-model]
            [teet.file.file-storage :as file-storage]
            [clojure.string :as str]
            [datomic.client.api :as d]
            [teet.file.file-db :as file-db]))

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

(defn fake-upload
  "Upload (or replace) file.
  Doesn't really upload anything to S3, just invokes the backend commands."
  ([upload-to-task-id file-info]
   (fake-upload upload-to-task-id file-info nil))
  ([upload-to-task-id file-info previous-version-id]
   (with-redefs [file-storage/upload-url (fn [name]
                                           (str "UPLOAD:" name))]
     (let [{:keys [url task-id file]}
           (tu/local-command (if previous-version-id
                               :file/replace
                               :file/upload)
                             (merge
                              {:task-id upload-to-task-id
                               :file file-info}
                              (when previous-version-id
                                {:previous-version-id previous-version-id})))]
       (is (str/starts-with? url "UPLOAD:"))
       (is (str/ends-with? url (:file/name file-info)))
       (when-not previous-version-id
         (is (= task-id upload-to-task-id)))
       (is (:db/id file))
       (is (not (contains? file :file/id)))

       ;; Mark upload as complete
       (let [{id :db/id file-id :file/id :as uploaded}
             (tu/local-command :file/upload-complete
                               {:db/id (:db/id file)})]
         (is (= id (:db/id uploaded)))
         (is (uuid? file-id))
         uploaded)))))

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
      (tu/store-data! :original-file
                      (fake-upload (tu/get-data :task-id)
                                   {:file/name "image.png"
                                    :file/size 666})))

    (testing "Uploading replacement moves id"
      (tu/store-data! :replacement-file
                      (fake-upload (tu/get-data :task-id)
                                   {:file/name "replacement.png"
                                    :file/size 420}
                                   (:db/id (tu/get-data :original-file))))
      (is (= (:file/id (tu/get-data :replacement-file))
             (:file/id (tu/get-data :original-file)))
          "Replacement has same (moved) UUID"))

    (testing "database has correct info"
      (let [f (d/pull (tu/db)
                      '[:db/id :file/id
                        {:file/previous-version [:db/id :file/id]}]
                      (:db/id (tu/get-data :replacement-file)))]
        (is (:file/id f))
        (is (nil? (get-in f [:file/previous-version :file/id])))))))

(deftest file-unique-metadata
  (tu/local-login tu/mock-user-boss)
  (tu/store-data!
   :task-id
   (tu/create-task {:user tu/mock-user-boss
                    :activity (tu/->db-id "p1-lc1-act1")
                    :task {:task/type :task.type/third-party-review
                           :task/group :task.group/land-purchase}}))
  (testing "Uploading file with metadata"
    (tu/store-data!
     :first-file
     (fake-upload (tu/get-data :task-id)
                  {:file/name "first file.png"
                   :file/size 10240
                   :file/document-group :file.document-group/general
                   :file/sequence-number 666}))
    (is (= {:thk.project/id "11111"
            :description "first file"
            :extension "png"
            :document-group "0"
            :sequence-number 666
            :activity "MO"
            :task "EK"
            :part "00"}
           (file-db/file-metadata-by-id (tu/db)
                                        (:db/id (tu/get-data :first-file))))))

  (testing "Uploading another file with different metadata"
    (tu/store-data!
     :second-file
     (fake-upload (tu/get-data :task-id)
                  {:file/name "second file.png"
                   :file/size 4096
                   :file/document-group :file.document-group/annexes
                   :file/sequence-number 420}))
    (is (= {:thk.project/id "11111"
            :description "second file"
            :extension "png"
            :document-group "9"
            :sequence-number 420
            :activity "MO"
            :task "EK"
            :part "00"}
           (file-db/file-metadata-by-id (tu/db)
                                        (:db/id (tu/get-data :second-file))))))

  (testing "Changing first file to have same metadata as second"
    (is
     (thrown-with-msg?
      Exception #"Two files with same metadata"
      (tu/local-command :file/modify
                        {:db/id (:db/id (tu/get-data :second-file))
                         :file/name "first file.png"
                         :file/sequence-number 666
                         :file/document-group (d/pull (tu/db)
                                                      [:db/id :db/ident]
                                                      :file.document-group/general)
                         :file/part nil})))))
