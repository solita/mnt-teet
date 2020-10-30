(ns teet.meeting.meeting-commands-test
  (:require [teet.meeting.meeting-commands :as meeting-commands]
            [teet.test.utils :as tu]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [datomic.client.api :as d]))

(use-fixtures :each tu/with-environment (tu/with-db) tu/with-global-data)

(defn test-meeting []
  (let [now (System/currentTimeMillis)]
    {:meeting/title "test meeting"
     :meeting/location "inside the test runner"
     :meeting/start (java.util.Date. (+ now (* 1000 60)))
     :meeting/end (java.util.Date. (+ now (* 1000 60 60)))
     :meeting/organizer tu/mock-user-manager}))

(defn create-meeting! [activity-eid meeting]
  (let [response
        (tu/local-command :meeting/create
                          {:activity-eid activity-eid
                           :meeting/form-data meeting})]
    (if-let [id (get-in response [:tempids "new-meeting"])]
      id
      (throw (ex-info "Meeting creation failed"
                      (merge {:activity-eid activity-eid
                              :meeting meeting}
                             (select-keys response [:status :body])))))))
(deftest create-meeting
  (tu/local-login tu/mock-user-boss)
  (testing "Invalid meeting isn't created"
    (is (thrown?
         Exception
         (create-meeting! (tu/->db-id "p1-lc1-act1") {:meeting/title "foo"}))))

  (testing "Valid meeting is created"
    (let [id (create-meeting! (tu/->db-id "p1-lc1-act1") (test-meeting))]
      (is id)
      (is (= 1 (:meeting/number (d/pull (tu/db) '[:meeting/number] id)))
          "meeting is the first of its number")))

  (testing "Creating a new meeting gets next number"
    (let [id (create-meeting! (tu/->db-id "p1-lc1-act1") (test-meeting))]
      (is id)
      (is (= 2 (:meeting/number (d/pull (tu/db) '[:meeting/number] id)))))))

(deftest duplicate-meeting
  (tu/local-login tu/mock-user-boss)
  (let [meeting (assoc (test-meeting)
                       :meeting/organizer tu/mock-user-boss)
        meeting-id (create-meeting! (tu/->db-id "p1-lc1-act1")
                                    meeting)]

    ;; Add agenda
    (is (get-in (tu/local-command :meeting/update-agenda
                                  {:db/id meeting-id
                                   :meeting/agenda [{:db/id "new-agenda"
                                                     :meeting.agenda/topic "TEST DUPLICATION"
                                                     :meeting.agenda/body "This *tests* duplication of meetings feature"
                                                     :meeting.agenda/responsible tu/mock-user-carla-consultant}]})
                [:tempids "new-agenda"]) "new agenda is created")

    ;; Add participation
    (is (get-in (tu/local-command :meeting/add-participation
                                  {:participation/in meeting-id
                                   :participation/role :participation.role/reviewer
                                   :participation/participant tu/mock-user-edna-consultant})
                [:tempids "new-participation"]) "new participation is created")

    (let [new-start  (java.util.Date. (+ (.getTime (:meeting/start meeting))
                                         (* 1000 60 60)))
          new-end  (java.util.Date. (+ (.getTime (:meeting/end meeting))
                                       (* 1000 60 60)))
          response
          (tu/local-command :meeting/duplicate
                            {:db/id meeting-id
                             :meeting/start new-start
                             :meeting/end new-end})
          new-meeting-id (get-in response [:tempids "new-meeting"])
          [old-meeting new-meeting] (for [m [meeting-id new-meeting-id]]
                                      (d/pull (tu/db) '[*
                                                        {:meeting/agenda [*]}
                                                        {:participation/_in [*]}] m))]
      (testing "Duplicated meeting has required information"
        ;; check same fields, number is different, agenda and participations same
        (is (= (select-keys old-meeting [:meeting/title :meeting/location :meeting/organizer])
               (select-keys new-meeting [:meeting/title :meeting/location :meeting/organizer])))
        (is (> (:meeting/number new-meeting) (:meeting/number old-meeting))
            "duplicated meeting has new number")
        (is (= new-start (:meeting/start new-meeting)))
        (is (= new-end (:meeting/end new-meeting)))
        (is (apply =
                   (for [{p :participation/_in} [old-meeting new-meeting]]
                     (into #{}
                           (map (juxt :participation/role :participation/participant))
                           p)))
            "participants are the same")))))
