(ns teet.integration.integration-s3-test
  (:require [clojure.test :as test :refer [deftest is]]
            [teet.integration.integration-s3 :as integration-s3]))

(def s3-trigger-event-example
  "{\"Records\": [{\"s3\": {\"bucket\": {\"name\": \"the-files\"}, \"object\": {\"key\": \"somefile.pdf\"}}}]}")

(deftest s3-trigger-event-read
  (is (= {:bucket "the-files"
          :file-key "somefile.pdf"}
         (-> {:event {:input s3-trigger-event-example}}
             integration-s3/read-trigger-event
             :s3))))
