(ns teet.document.document-commands-test
  (:require [clojure.test :refer :all]
            [teet.document.document-commands :as document-commands]))

(deftest validate-document
  (testing "too large files are invalid"
    (is (= (:error (document-commands/validate-document {:document/type "image/png"
                                                         :document/size (* 1024 1024 1024)}))
           :file-too-large)))
  (testing "files of illegal type are invalid"
    (is (= (:error (document-commands/validate-document {:document/type "application/nonsense"
                                                         :document/size 1024}))
           :file-type-not-allowed)))
  (testing "other files are valid"
    (is (nil? (document-commands/validate-document
               {:document/type (rand-nth (vec document-commands/upload-allowed-file-types))
                :document/size (rand (inc document-commands/upload-max-file-size))})))))
