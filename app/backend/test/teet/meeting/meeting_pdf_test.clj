(ns ^:db teet.meeting.meeting-pdf-test
  (:require teet.meeting.meeting-pdf
            [teet.test.utils :as tu]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [datomic.client.api :as d]))

(use-fixtures :each tu/with-environment (tu/with-db) tu/with-global-data)

(deftest print-meeting-pdf
  (tu/local-login tu/mock-user-boss)
  (testing
    "PDF fails to be printed"
    (is (thrown?
          Exception (teet.meeting.meeting-pdf/meeting-pdf (tu/db) (tu/logged-user) 1) {})
        "Meeting PDF for non-existing meeting"))
  (testing
    "PDF succeeded to be printed"
    (is (not (thrown? Exception (teet.meeting.meeting-pdf/meeting-pdf (tu/db) (tu/logged-user) (tu/meeting-id)))))))
