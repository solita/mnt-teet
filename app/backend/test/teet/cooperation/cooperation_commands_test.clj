(ns ^:db teet.cooperation.cooperation-commands-test
  (:require [clojure.test :refer [deftest is testing] :as t]
            [teet.test.utils :as tu]
            [teet.util.date :as dateu]
            teet.task.task-commands
            [teet.util.datomic :as du]))

(t/use-fixtures
  :each
  tu/with-environment
  (tu/with-db))

(deftest test-for-third-party
  (tu/local-login tu/mock-user-boss)
  (let [project-id (:thk.project/id (du/entity (tu/db) (tu/->db-id "p1")))
        third-party-name "party of third"
        third-party {:db/id "new-third-party"
                     :cooperation.3rd-party/name third-party-name
                     :cooperation.3rd-party/id-code "123"
                     :cooperation.3rd-party/email "party@example.om"
                     :cooperation.3rd-party/phone "2222"}
        third-party-id (get-in (tu/local-command :cooperation/save-3rd-party
                                                 {:thk.project/id project-id
                                                  :third-party third-party})
                               [:tempids "new-third-party"])
        third-party-teet-id (:teet/id (du/entity (tu/db) third-party-id))
        valid-date-for-application (-> (du/entity (tu/db) (tu/->db-id "p1-lc1-act2"))
                                       :activity/estimated-start-date
                                       (dateu/inc-days 1))
        invalid-date-for-application (-> (du/entity (tu/db) (tu/->db-id "p1-lc1-act2"))
                                         :activity/estimated-start-date
                                         (dateu/dec-days 1))
        application {:cooperation.application/type :cooperation.application.type/building-permit-draft
                     :cooperation.application/response-type :cooperation.application.response-type/consent
                     :cooperation.application/date valid-date-for-application}
        invalid-date-application (assoc application :cooperation.application/date invalid-date-for-application)
        application-payload {:thk.project/id project-id
                             :third-party-teet-id third-party-teet-id
                             :application application}
        invalid-third-party-application-payload {:thk.project/id project-id
                                                 :cooperation.3rd-party/name "No such third party exists"
                                                 :application application}
        res (tu/local-command :cooperation/create-application
                              application-payload)

        ;; Editing
        new-application-id (get-in res [:tempids "new-application"])]

    (testing "Can create a third party"
      (is (some? third-party-id)))

    (testing "Can create application with valid date"
      (is (some? res)))

    (testing "Can't create application without matching third-party"
      (is (thrown?
            Exception
            (tu/local-command :cooperation/create-application
                              invalid-third-party-application-payload))))

    (testing "Can't create application without matching the creation date with a task"
      (is (thrown?
            Exception
            (tu/local-command :cooperation/create-application
                              {:thk.project/id project-id
                               :cooperation.3rd-party/name third-party-name
                               :application invalid-date-application}))))

    (testing "Application and project need to match when editing"
      (is (thrown?
           Exception
           (tu/local-command :cooperation/edit-application
                             ;; Using the THK project id of another project
                             {:thk.project/id (:thk.project/id (du/entity (tu/db) (tu/->db-id "p2")))
                              :application {:db/id new-application-id
                                            :cooperation.application/date valid-date-for-application

                                            :cooperation.application/type :cooperation.application.type/building-permit-order}}))))


    ;; Application editing
    (testing "Application editability"
      ;; Can't change date
      (is (thrown? Exception
             (tu/local-command :cooperation/edit-application
                               {:thk.project/id project-id
                                :application {:db/id new-application-id
                                              :cooperation.application/date #inst "2021-04-12T12:00:00"
                                              :cooperation.application/type :cooperation.application.type/building-permit-order}})))
      
      (is (some? (tu/local-command :cooperation/edit-application
                                   {:thk.project/id project-id
                                    :application {:db/id new-application-id
                                                  :cooperation.application/date valid-date-for-application
                                                  :cooperation.application/type :cooperation.application.type/building-permit-order}}))))


    (testing "Application can't be edited if it has a third party response"
      (let [;; Create new application
            new-application-id (-> (tu/local-command :cooperation/create-application
                                                     application-payload)
                                   (get-in [:tempids "new-application"]))]
        ;; Create response
        (tu/local-command :cooperation/save-application-response
                          {:thk.project/id project-id
                           :application-id new-application-id
                           :form-data {:cooperation.response/status :cooperation.response.status/no-objection
                                       :cooperation.response/date valid-date-for-application
                                       :cooperation.response/valid-months 12}})

        ;; Can't be edited
        (is (tu/is-thrown-with-data?
             {:teet/error :application-has-third-party-response}
             (tu/local-command :cooperation/edit-application
                               {:thk.project/id project-id
                                :application {:db/id new-application-id
                                              :cooperation.application/date valid-date-for-application
                                              :cooperation.application/type :cooperation.application.type/building-permit-order}})))))
    
    ;; Application deletion
    (testing "Application can be deleted"
      (is (some? (tu/local-command :cooperation/delete-application
                                   {:thk.project/id project-id
                                    :db/id new-application-id}))))

    (testing "Application can't be deleted if it has a third party response"
      (let [;; Create new application
            new-application-id (-> (tu/local-command :cooperation/create-application
                                                     application-payload)
                                   (get-in [:tempids "new-application"]))]
        ;; Create response
        (tu/local-command :cooperation/save-application-response
                          {:thk.project/id project-id
                           :application-id new-application-id
                           :form-data {:cooperation.response/status :cooperation.response.status/no-objection
                                       :cooperation.response/date valid-date-for-application
                                       :cooperation.response/valid-months 12}})

        ;; Can't be deleted
        (is (thrown?
             Exception
             (tu/local-command :cooperation/delete-application
                               {:thk.project/id project-id
                                :db/id new-application-id})))))

    
    (testing "External consultant can add add responses to involved party cooperation applications (bug TEET-1416)"
      (let [;; Create new application
            new-application-id (-> (tu/local-command :cooperation/create-application
                                                     application-payload)
                                   (get-in [:tempids "new-application"]))]
        ;; switch user to ext
        (tu/local-login tu/mock-user-carla-consultant)
        ;; add response
        (tu/local-command :cooperation/save-application-response
                          {:thk.project/id project-id
                           :application-id new-application-id
                           :form-data {:cooperation.response/status :cooperation.response.status/no-objection
                                       :cooperation.response/date valid-date-for-application
                                       :cooperation.response/valid-months 12}})

        ;; Can't be deleted
        (is (thrown?
             Exception
             (tu/local-command :cooperation/delete-application
                               {:thk.project/id project-id
                                :db/id new-application-id})))))))
