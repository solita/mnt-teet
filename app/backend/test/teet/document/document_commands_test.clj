(ns teet.document.document-commands-test
  (:require [clojure.test :refer :all]
            [teet.document.document-model :as document-model]))

(deftest validate-document
  (testing "too large files are invalid"
    (is (= (:error (document-model/validate-file {:file/type "image/png"
                                                     :file/size (* 1024 1024 3001)}))
           :file-too-large)))
  (testing "files of illegal type are invalid"
    (is (= (:error (document-model/validate-file {:file/type "application/nonsense"
                                                  :file/size 1024}))
           :file-type-not-allowed)))
  (testing "other files are valid"
    (is (nil? (document-model/validate-file
                {:file/type (rand-nth (vec document-model/upload-allowed-file-types))
                 :file/size (rand (inc document-model/upload-max-file-size))})))))
