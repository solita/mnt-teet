(ns teet.file.file-model-test
  (:require [clojure.test :refer [deftest is testing]]
            [teet.file.file-model :as file-model]))

(deftest type-by-suffix
  (testing "`:type` of returned file map is determined by file name suffix"
    (is (= (file-model/type-by-suffix {:file/name "image.jpg" :file/type "image/ecw"})
           {:file/name "image.jpg" :file/type "image/jpeg"}))))
