(ns ^:db teet.project.project-commands-test
  (:require [clojure.test :refer [deftest is testing] :as t]
            [teet.project.project-model :as project-model])
  (:import (java.util Date)))

#_(t/use-fixtures :each
  tu/with-environment
  (tu/with-db))

;; PENDING: No tests at the moment (pm test was moved to activity command tests)
(def date-in-the-future
  (Date. (+ (* 7 86400 1000)
            (.getTime (Date.)))))

(def date-in-the-past
  (Date. (- (.getTime (Date.))
            (* 7 86400 1000))))


(def project-on-schedule {:thk.project/estimated-start-date date-in-the-past
                          :thk.project/manager #:db{:id 34168423344767456}
                          :thk.project/lifecycles [#:thk.lifecycle{:activities [{:db/id 27078772368869867
                                                                                 :activity/estimated-start-date date-in-the-past
                                                                                 :activity/estimated-end-date date-in-the-future
                                                                                 :activity/tasks [{:task/type #:db{:ident :task.type/terms-of-reference}
                                                                                                   :task/group #:db{:ident :task.group/base-data}
                                                                                                   :task/status #:db{:ident :task.status/not-started}
                                                                                                   :task/estimated-start-date date-in-the-past
                                                                                                   :meta/created-at #inst"2020-08-12T07:31:37.945-00:00"
                                                                                                   :task/estimated-end-date date-in-the-future}]
                                                                                 :activity/name #:db{:ident :activity.name/pre-design}
                                                                                 :activity/status #:db{:ident :activity.status/in-preparation}}]}]
                          :thk.project/estimated-end-date date-in-the-future
                          :thk.project/name "Jõhvi- Tartu- Valga"
                          :thk.project/id "214"
                          :thk.project/owner #:db{:id 22144164183409119}})


(def project-unassigned {:thk.project/estimated-start-date date-in-the-future
                         :thk.project/manager #:db{:id 34168423344767456}
                         :thk.project/lifecycles [#:thk.lifecycle{:activities [{:activity/estimated-start-date date-in-the-past
                                                                                :activity/estimated-end-date date-in-the-future
                                                                                :activity/tasks [{:task/send-to-thk? false
                                                                                                  :task/type #:db{:ident :task.type/terms-of-reference}
                                                                                                  :task/group #:db{:ident :task.group/base-data}
                                                                                                  :task/status #:db{:ident :task.status/not-started}
                                                                                                  :task/estimated-start-date date-in-the-past
                                                                                                  :task/estimated-end-date date-in-the-future}]
                                                                                :activity/name #:db{:ident :activity.name/pre-design}
                                                                                :activity/status #:db{:ident :activity.status/in-preparation}}]}]
                         :thk.project/estimated-end-date date-in-the-future
                         :thk.project/name "Jõhvi- Tartu- Valga"
                         :thk.project/id "214"})

(def project-unassigned-over-start-date {:thk.project/estimated-start-date date-in-the-past
                                         :thk.project/manager #:db{:id 34168423344767456}
                                         :thk.project/lifecycles [#:thk.lifecycle{:activities [{:activity/estimated-start-date date-in-the-past
                                                                                                :activity/estimated-end-date date-in-the-past
                                                                                                :activity/tasks [{:task/type #:db{:ident :task.type/terms-of-reference}
                                                                                                                  :task/group #:db{:ident :task.group/base-data}
                                                                                                                  :task/status #:db{:ident :task.status/not-started}
                                                                                                                  :task/estimated-start-date date-in-the-past
                                                                                                                  :task/estimated-end-date date-in-the-past}]
                                                                                                :activity/name #:db{:ident :activity.name/pre-design}
                                                                                                :activity/status #:db{:ident :activity.status/in-preparation}}]}]
                                         :thk.project/estimated-end-date date-in-the-future
                                         :thk.project/name "Jõhvi- Tartu- Valga"
                                         :thk.project/id "214"
                                         :db/id 20864332648677825})


(def activity-over-deadline {:thk.project/estimated-start-date date-in-the-past
                             :thk.project/manager #:db{:id 34168423344767456}
                             :thk.project/lifecycles [#:thk.lifecycle{:activities [{:activity/estimated-start-date date-in-the-past
                                                                                    :activity/estimated-end-date date-in-the-past
                                                                                    :activity/tasks [{:task/type #:db{:ident :task.type/terms-of-reference}
                                                                                                      :task/group #:db{:ident :task.group/base-data}
                                                                                                      :task/status #:db{:ident :task.status/not-started}
                                                                                                      :task/estimated-start-date date-in-the-past
                                                                                                      :task/estimated-end-date date-in-the-past}]
                                                                                    :activity/name #:db{:ident :activity.name/pre-design}
                                                                                    :activity/status #:db{:ident :activity.status/in-preparation}}]}]
                             :thk.project/estimated-end-date date-in-the-future
                             :thk.project/name "Jõhvi- Tartu- Valga"
                             :thk.project/id "214"
                             :thk.project/owner #:db{:id 22144164183409119}})

(def activity-deadline-current-date {:thk.project/estimated-start-date date-in-the-past
                                     :thk.project/manager #:db{:id 34168423344767456}
                                     :thk.project/lifecycles [#:thk.lifecycle{:activities [{:activity/estimated-start-date date-in-the-past
                                                                                            :activity/estimated-end-date (Date.)
                                                                                            :activity/tasks [{:task/type #:db{:ident :task.type/terms-of-reference}
                                                                                                              :task/group #:db{:ident :task.group/base-data}
                                                                                                              :task/status #:db{:ident :task.status/not-started}
                                                                                                              :task/estimated-start-date date-in-the-past
                                                                                                              :task/estimated-end-date date-in-the-future}]
                                                                                            :activity/name #:db{:ident :activity.name/pre-design}
                                                                                            :activity/status #:db{:ident :activity.status/in-preparation}}]}]
                                     :thk.project/estimated-end-date date-in-the-future
                                     :thk.project/name "Jõhvi- Tartu- Valga"
                                     :thk.project/id "214"
                                     :thk.project/owner #:db{:id 22144164183409119}})

(def task-over-deadline {:thk.project/estimated-start-date date-in-the-past
                         :thk.project/manager #:db{:id 34168423344767456}
                         :thk.project/lifecycles [#:thk.lifecycle{:activities [{:activity/estimated-start-date date-in-the-past
                                                                                :activity/estimated-end-date date-in-the-future
                                                                                :activity/tasks [{:task/send-to-thk? false
                                                                                                  :task/type #:db{:ident :task.type/terms-of-reference}
                                                                                                  :task/group #:db{:ident :task.group/base-data}
                                                                                                  :task/status #:db{:ident :task.status/not-started}
                                                                                                  :task/estimated-start-date date-in-the-past
                                                                                                  :task/estimated-end-date date-in-the-future}]
                                                                                :activity/name #:db{:ident :activity.name/pre-design}
                                                                                :activity/status #:db{:ident :activity.status/in-preparation}}]}
                                                  #:thk.lifecycle{:activities [{:activity/estimated-start-date date-in-the-past
                                                                                :activity/estimated-end-date date-in-the-past
                                                                                :activity/tasks [{:task/type #:db{:ident :task.type/terms-of-reference}
                                                                                                  :task/group #:db{:ident :task.group/base-data}
                                                                                                  :task/status #:db{:ident :task.status/not-started}
                                                                                                  :task/estimated-start-date date-in-the-past
                                                                                                  :task/estimated-end-date date-in-the-past}
                                                                                                 {:task/type #:db{:ident :task.type/terms-of-reference}
                                                                                                  :task/group #:db{:ident :task.group/base-data}
                                                                                                  :task/status #:db{:ident :task.status/not-started}
                                                                                                  :task/estimated-start-date date-in-the-past
                                                                                                  :task/estimated-end-date date-in-the-future}
                                                                                                 {:task/type #:db{:ident :task.type/terms-of-reference}
                                                                                                  :task/group #:db{:ident :task.group/base-data}
                                                                                                  :task/status #:db{:ident :task.status/not-started}
                                                                                                  :task/estimated-start-date date-in-the-past
                                                                                                  :task/estimated-end-date date-in-the-future}
                                                                                                 {:task/type #:db{:ident :task.type/terms-of-reference}
                                                                                                  :task/group #:db{:ident :task.group/base-data}
                                                                                                  :task/status #:db{:ident :task.status/not-started}
                                                                                                  :task/estimated-start-date date-in-the-past
                                                                                                  :task/estimated-end-date date-in-the-future}]
                                                                                :activity/name #:db{:ident :activity.name/pre-design}
                                                                                :activity/status #:db{:ident :activity.status/completed}}]}
                                                  #:thk.lifecycle{:activities [{:activity/estimated-start-date date-in-the-past
                                                                                :activity/estimated-end-date date-in-the-past
                                                                                :activity/tasks [{:task/type #:db{:ident :task.type/terms-of-reference}
                                                                                                  :task/group #:db{:ident :task.group/base-data}
                                                                                                  :task/status #:db{:ident :task.status/not-started}
                                                                                                  :task/estimated-start-date date-in-the-past
                                                                                                  :task/estimated-end-date date-in-the-future}]
                                                                                :activity/name #:db{:ident :activity.name/pre-design}
                                                                                :activity/status #:db{:ident :activity.status/completed}}]}]
                         :thk.project/estimated-end-date date-in-the-future
                         :thk.project/name "Jõhvi- Tartu- Valga"
                         :thk.project/id "214"
                         :thk.project/owner #:db{:id 22144164183409119}})


(deftest project-list-with-status
  (testing "Project on time => Green status"
    (is (= :on-schedule (:thk.project/status (project-model/project-with-status
                                  project-on-schedule)))))
  (testing "Project is unassigned but not late => Gray status"
    (is (= :unassigned (:thk.project/status (project-model/project-with-status
                                              project-unassigned)))))
  (testing "Project is unassigned and late => orange status"
    (is (= :unassigned-over-start-date (:thk.project/status (project-model/project-with-status
                                                              project-unassigned-over-start-date)))))
  (testing "Project has an activity over deadline othwise project assigned and on schedule"
    (is (= :activity-over-deadline (:thk.project/status (project-model/project-with-status
                                                              activity-over-deadline)))))
  (testing "Project has an activity over deadline othwise project assigned and on schedule"
    (is (= :task-over-deadline (:thk.project/status (project-model/project-with-status
                                                      task-over-deadline)))))
  (testing "Activity deadline current date not over deadline"
    (is (= :on-schedule (:thk.project/status (project-model/project-with-status
                                                      activity-deadline-current-date))))))
