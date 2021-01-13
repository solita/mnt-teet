(ns ^:db teet.file.file-commands-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [teet.test.utils :as tu]
            [teet.file.file-model :as file-model]
            [teet.file.file-storage :as file-storage]
            [clojure.string :as str]
            [datomic.client.api :as d]
            [teet.file.file-db :as file-db]
            [teet.util.datomic :as du]))

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

(def fake-upload tu/fake-upload)

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
    (tu/is-thrown-with-data?
     {:teet/error :file-metadata-not-unique}
     (tu/local-command :file/modify
                       {:db/id (:db/id (tu/get-data :second-file))
                        :file/name "first file.png"
                        :file/sequence-number 666
                        :file/document-group :file.document-group/general
                        :file/part nil})))

  (testing "Changing group/seq# metadata to empty"
    (tu/local-command :file/modify
                      {:db/id (:db/id (tu/get-data :first-file))
                       :file/name "first file.png"
                       :file/document-group nil
                       :file/sequence-number nil})
    (let [after (d/pull (tu/db) [:file/document-group :file/sequence-number]
                        (:db/id (tu/get-data :first-file)))]
      (is (not (contains? after :file/document-group)) "document group no longer exists")
      (is (not (contains? after :file/sequence-number)) "seq# no longer exists"))

    (testing "Changing other file to empty as well"
      (tu/is-thrown-with-data?
       {:teet/error :file-metadata-not-unique}
       (tu/local-command :file/modify
                         {:db/id (:db/id (tu/get-data :second-file))
                          :file/name "first file.png"
                          :file/document-group nil
                          :file/sequence-number nil})))))

(deftest delete-part
  (tu/local-login tu/mock-user-boss)
  (tu/store-data!
   :task-id
   (tu/create-task {:user tu/mock-user-boss
                    :activity (tu/->db-id "p1-lc1-act1")
                    :task {:task/type :task.type/third-party-review
                           :task/group :task.group/land-purchase}}))

  ;; Upload file and replacement for it
  (tu/store-data!
   :original-file
   (fake-upload (tu/get-data :task-id)
                {:file/name "file in part 1.png"
                 :file/size 4096
                 :file/document-group :file.document-group/annexes
                 :file/sequence-number 420
                 :file/part {:file.part/number 1
                             :file.part/name "this is the part"}}))

  (tu/store-data!
   :replacement-file
   (fake-upload (tu/get-data :task-id)
                {:file/name "file in part 1.png"
                 :file/size 4096
                 :file/document-group :file.document-group/annexes
                 :file/sequence-number 420
                 :file/part {:file.part/number 1
                             :file.part/name "this is the part"}}
                (:db/id (tu/get-data :original-file))))

  (tu/store-data!
   :original-part-id
   (get-in (du/entity (tu/db) (:db/id (tu/get-data :replacement-file)))
           [:file/part :db/id]))

  (is (thrown-with-msg?
       Exception #"part being deleted contains files"
       (tu/local-command :task/delete-part
                         {:part-id (tu/get-data :original-part-id)}))
      "Part can't be deleted because it has files")

  ;; Create new part and change file to new part
  (tu/store-data!
   :new-part-id
   (get-in
    (tu/local-command :task/create-part
                      {:task-id (tu/get-data :task-id)
                       :part-name "other part"})
    [:tempids "new-part"]))

  (tu/local-command
   :file/modify
   {:db/id (:db/id (tu/get-data :replacement-file))
    :file/name "file in part 1.png"
    :file/size 4096
    :file/document-group (d/pull (tu/db)
                                 [:db/id :db/ident]
                                 :file.document-group/annexes)
    :file/sequence-number 420
    :file/part {:db/id (tu/get-data :new-part-id)}})

  (is (thrown-with-msg?
       Exception #"part being deleted contains files"
       (tu/local-command :task/delete-part
                         {:part-id (tu/get-data :new-part-id)}))
      "New part can't be deleted")

  (testing "Deleting old part works"
    (tu/local-command :task/delete-part
                      {:part-id (tu/get-data :original-part-id)})

    (is (:meta/deleted? (du/entity (tu/db) (tu/get-data :original-part-id)))
        "Old part has deleted flag")))


(deftest file-extension-can-change-with-file-upload
  (tu/local-login tu/mock-user-boss)
  (tu/store-data!
    :task-id
    (tu/create-task {:user tu/mock-user-boss
                     :activity (tu/->db-id "p1-lc1-act1")
                     :task {:task/type :task.type/third-party-review
                            :task/group :task.group/land-purchase}}))

  (testing "Uploading file with metadata"
    (tu/store-data!
      :original-file
      (fake-upload (tu/get-data :task-id)
                   {:file/name "first-file.png"
                    :file/size 10240
                    :file/document-group :file.document-group/general
                    :file/sequence-number 666}))

    (is (= {:thk.project/id "11111"
            :description "first-file"
            :extension "png"
            :document-group "0"
            :sequence-number 666
            :activity "MO"
            :task "EK"
            :part "00"}
           (file-db/file-metadata-by-id (tu/db)
                                        (:db/id (tu/get-data :original-file))))))

  (testing "Replacing a file with different extension changes extension, but keeps everything else"
    (tu/store-data!
      :replacing-file
      (fake-upload (tu/get-data :task-id)
                   {:file/name "second-file.doc"
                    :file/size 10240
                    :file/sequence-number 666}
                   (:db/id (tu/get-data :original-file))))

    (is (= {:thk.project/id "11111"
            :description "first-file"
            :extension "doc"
            :document-group "0"
            :sequence-number 666
            :activity "MO"
            :task "EK"
            :part "00"}
           (file-db/file-metadata-by-id (tu/db)
                                        (:db/id (tu/get-data :replacing-file)))))))
