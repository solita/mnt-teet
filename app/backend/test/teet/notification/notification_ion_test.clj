(ns ^:db teet.notification.notification-ion-test
  (:require [clojure.test :refer :all]
            [teet.test.utils :as tu]
            [clojure.test :as t]
            [teet.util.datomic :as du]
            [teet.util.date :as dateu]
            [datomic.client.api :as d]
            [teet.notification.notification-ion :as notification-ion]
            [teet.util.date :as date]))

(t/use-fixtures :each
  (tu/with-config {:notify {:application-expire-days "45"}})
  tu/with-environment
  (tu/with-db))

(defn project-id [db] (:thk.project/id (du/entity db (tu/->db-id "p6"))))

(def cooperation-3rd-party-name "Notification Test")

(defn valid-date-for-application
  [db]
  (-> (du/entity db
        (tu/->db-id "p1-lc1-act20"))
    :activity/estimated-start-date
    (dateu/inc-days 1)))

(defn create-cooperation-3rd-party
  "Create test 3rd party and return its :teet/id"
  [db]
  (let [cooperation-3rd-party {:cooperation.3rd-party/name cooperation-3rd-party-name
                               :cooperation.3rd-party/id-code "00000001"
                               :cooperation.3rd-party/email "cooperation3rdparty@entity.te"
                               :cooperation.3rd-party/phone "111111111111111"}
        third-party-id (get-in
                        (tu/local-command :cooperation/create-3rd-party {:thk.project/id (project-id db)
                                                                         :third-party cooperation-3rd-party})
                        [:tempids "new-third-party"])]
    (:teet/id (du/entity (tu/db) third-party-id))))

(defn application
  [db]
  {:cooperation.application/type :cooperation.application.type/building-permit-draft
   :cooperation.application/response-type :cooperation.application.response-type/consent
   :cooperation.application/date (valid-date-for-application db)})

(defn application-payload
  [db third-party-teet-id]
  {:thk.project/id (project-id db)
   :third-party-teet-id third-party-teet-id
   :application (application db)})

(defn response-payload
  [db application-id]
  {:thk.project/id (project-id db)
   :application-id application-id
   :form-data {:cooperation.response/valid-months 1
               :cooperation.response/valid-until (date/inc-days (date/now) 30)
               :cooperation.response/date (date/now)
               :cooperation.response/content "One month approval response"
               :cooperation.response/status :cooperation.response.status/no-objection}})

(defn create-cooperation-application
  [db third-party-teet-id]
  (tu/local-command :cooperation/create-application
                    (application-payload db third-party-teet-id)))

(defn create-application-response
  [db application-id]
  (tu/local-command :cooperation/save-application-response (response-payload db application-id)))

(defn fetch-application-to-expire-notifications
  [db]
  (d/q '[:find ?notification
         :where [?notification
                 :notification/type :notification.type/cooperation-application-expired-soon]] db))

(deftest application-expired-notify
  (tu/local-login tu/mock-user-boss)
  ;; Create Application
  (let [third-party-teet-id (create-cooperation-3rd-party (tu/db))
        create-application-result (create-cooperation-application (tu/db) third-party-teet-id)]
    (is (some? third-party-teet-id))
    (is (some? create-application-result))
    ;; Run notify ion and check - no notifications created
    (notification-ion/notify)
    (testing "No notification before"
      (is (empty? (fetch-application-to-expire-notifications (tu/db)))))
    ;; Create response with validity 1 month
    (create-application-response (tu/db)
                                 (get (:tempids create-application-result) "new-application"))
    ;; Run notify ion and verify there is new notification
    (notification-ion/notify)
    (testing "Notification created"
      (is (not (empty? (fetch-application-to-expire-notifications (tu/db))))))
    ;; Run notify ion once again and verify no new notifications created
    (notification-ion/notify)
    (testing "No additional notifications created"
      (is (= 1 (count (fetch-application-to-expire-notifications (tu/db))))))))
