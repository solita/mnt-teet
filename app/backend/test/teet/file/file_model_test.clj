(ns teet.file.file-model-test
  (:require [clojure.test :refer [deftest is]]
            [teet.file.file-model :as file-model]))

(deftest validate-file
  (is (= (:error (file-model/validate-file {:file/size -3}))
         :file-empty))
  (is (= (:error (file-model/validate-file {:file/size 0}))
         :file-empty)))
