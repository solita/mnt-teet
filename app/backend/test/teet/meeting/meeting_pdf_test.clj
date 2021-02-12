(ns ^:db teet.meeting.meeting-pdf-test
  (:require teet.meeting.meeting-pdf
            [teet.meeting.meeting-commands-test :as meeting-commands-test]
            [teet.test.utils :as tu]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [hiccup.core :as core]
            [taoensso.timbre :as log]))

(use-fixtures :each tu/with-environment (tu/with-db) tu/with-global-data)

(def title-fo-block "<fo:block font-family=\"Arial, sans-serif\" font-size=\"24px\" font-style=\"normal\" font-weight=\"300\" line-height=\"24px\" space-after=\"5\" space-before=\"5\">Test meeting for PDF report #1</fo:block>")
(def topic-underlied-text-fo "<fo:block><fo:block>This <fo:inline text-decoration=\"underline\">is some underlined</fo:inline> text</fo:block></fo:block>")

(defn test-meeting []
  (let [now (System/currentTimeMillis)]
    {:meeting/title "Test meeting for PDF report"
     :meeting/location "Somewhere inside the test runner"
     :meeting/start (java.util.Date. (+ now (* 1000 60)))
     :meeting/end (java.util.Date. (+ now (* 1000 60 60)))
     :meeting/organizer tu/mock-user-boss}))

(deftest print-meeting-pdf
  (tu/local-login tu/mock-user-boss)
  (let [new-meeting-id (meeting-commands-test/create-meeting! (tu/->db-id "p1-lc1-act1") (test-meeting))]
    (testing
      "Added new agenda with underlined text"
      (is (get-in (tu/local-command :meeting/update-agenda
                                    {:db/id          new-meeting-id
                                     :meeting/agenda [{:db/id                      "new-agenda"
                                                       :meeting.agenda/topic       "TEST TOPIC"
                                                       :meeting.agenda/body        "This ++is some underlined++ text"
                                                       :meeting.agenda/responsible tu/mock-user-carla-consultant}]})
                  [:tempids "new-agenda"]) "new agenda with underlined text is created"))
    (testing
      "PDF has correct title"
      (let [pdf (teet.meeting.meeting-pdf/meeting-pdf (tu/db) (tu/logged-user) "en" new-meeting-id)]
        (is (= (core/html (nth (last (last pdf)) 2)) title-fo-block))))
    (testing
      "Underlied text rendered correctly"
      (let [pdf-2 (teet.meeting.meeting-pdf/meeting-pdf (tu/db) (tu/logged-user) "en" new-meeting-id)
            fo-with-topic (nth (last (last pdf-2)) 5)
            block-with-underlined-text (nth (nth (last (nth (first (nth (nth fo-with-topic 1) 2)) 3)) 3) 2)]
        (log/debug fo-with-topic)
        (is (= (core/html block-with-underlined-text) topic-underlied-text-fo))))))
