(ns teet.file.file-commands-test
  (:require [clojure.test :refer :all]
            [teet.file.file-model :as file-model]))

(deftest validate-document
  (reset! file-model/upload-allowed-file-suffixes #{"doc" "xslx" "mp4" "png"})
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
               {:file/name "myfile.doc"
                :file/size (rand (inc file-model/upload-max-file-size))})))
    (is (nil? (file-model/validate-file
               {:file/name "myfile.xslx"
                :file/size (rand (inc file-model/upload-max-file-size))})))
    (is (nil? (file-model/validate-file
               {:file/name "myfile.mp4"
                :file/size (rand (inc file-model/upload-max-file-size))})))))
