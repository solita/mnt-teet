(ns teet.file.file-model-test
  (:require [clojure.test :refer [deftest is]]
            [teet.file.file-model :as file-model]))

(deftest validate-file
  (is (= (:error (file-model/validate-file {:file/size -3}))
         :file-empty))
  (is (= (:error (file-model/validate-file {:file/size 0}))
         :file-empty)))

(deftest file-editable
  (is (true? (file-model/editable? {:file/status :file.status/draft})))
  (is (true? (file-model/editable? {:file/status {:db/ident :file.status/draft}})))
  (is (true? (file-model/editable? {})) "Defaults to editable if no status info")
  (is (false? (file-model/editable? {:file/status :file.status/final})))
  (is (false? (file-model/editable? {:file/status {:db/ident :file.status/final}}))))
