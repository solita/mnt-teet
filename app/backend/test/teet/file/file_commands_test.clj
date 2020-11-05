(ns teet.file.file-commands-test
  (:require [clojure.test :refer :all]
            [teet.test.utils :as tu]
            [teet.file.file-model :as file-model]))

(use-fixtures :each
  (tu/with-config {:file {:allowed-suffixes #{"png" "doc" "xls"}
                          :image-suffixes #{"png" "jpg" "gif"}}}))

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
