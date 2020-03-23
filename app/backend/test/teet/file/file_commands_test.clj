(ns teet.file.file-commands-test
  (:require [clojure.test :refer :all]
            [teet.file.file-model :as file-model]))

(deftest validate-document
  (testing "too large files are invalid"
    (is (= (:error (file-model/validate-file {:file/type "image/png"
                                                     :file/size (* 1024 1024 3001)}))
           :file-too-large)))
  (testing "files of illegal type are invalid"
    (is (= (:error (file-model/validate-file {:file/type "application/nonsense"
                                                  :file/size 1024}))
           :file-type-not-allowed)))
  (testing "other files are valid"
    (is (nil? (file-model/validate-file
                {:file/type (rand-nth (vec file-model/upload-allowed-file-types))
                 :file/size (rand (inc file-model/upload-max-file-size))})))))
