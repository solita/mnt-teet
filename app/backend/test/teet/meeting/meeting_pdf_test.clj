(ns ^:db teet.meeting.meeting-pdf-test
  (:require teet.meeting.meeting-pdf
            [teet.meeting.meeting-commands-test :as meeting-commands-test]
            [teet.test.utils :as tu]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [hiccup.core :as core]))

(use-fixtures :each tu/with-environment (tu/with-db) tu/with-global-data)

(def title-fo-block "<fo:block font-family=\"Helvetica, Arial, sans-serif\" font-size=\"32px\" font-style=\"normal\" font-weight=\"300\" line-height=\"48px\" space-after=\"5\" space-before=\"5\">Test meeting for PDF report #1</fo:block>")

(defn test-meeting []
  (let [now (System/currentTimeMillis)]
    {:meeting/title "Test meeting for PDF report"
     :meeting/location "Somewhere inside the test runner"
     :meeting/start (java.util.Date. (+ now (* 1000 60)))
     :meeting/end (java.util.Date. (+ now (* 1000 60 60)))
     :meeting/organizer tu/mock-user-manager}))

(deftest print-meeting-pdf
  (tu/local-login tu/mock-user-boss)
  (let [new-meeting-id (meeting-commands-test/create-meeting! (tu/->db-id "p1-lc1-act1") (test-meeting))]
  (testing
    "PDF has correct title"
    (let [pdf (teet.meeting.meeting-pdf/meeting-pdf (tu/db) (tu/logged-user) new-meeting-id)]
      (is (= (core/html (nth (last (last pdf)) 2)) title-fo-block))))))
