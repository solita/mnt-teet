(ns ^:db teet.activity-commands-test
  (:require [clojure.test :refer :all]
            [datomic.client.api :as d]
            teet.activity.activity-commands
            [teet.test.utils :as tu]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]))

(use-fixtures :each tu/with-environment (tu/with-db) tu/with-global-data)


(defn act1-id []
  (tu/->db-id "p1-lc1-act1"))

(defn act1-status []
  (->> (act1-id) (d/pull (tu/db) '[:activity/status] ) :activity/status :db/ident))

(deftest activity-submit-statuses
  (tu/local-command tu/mock-user-boss
                    :thk.project/add-permission
                    {:project-id (tu/->db-id "p1")
                     :user {:user/id tu/manager-id}
                     :role :manager})

  (tu/local-command tu/mock-user-boss
                    :thk.project/add-permission
                    {:project-id (tu/->db-id "p1")
                     :user {:user/id tu/internal-consultant-id}
                     :role :internal-consultant})
  
  (let [task-id (tu/create-task {:user tu/mock-user-manager :activity (act1-id)})]    
    (is (number? task-id))
    
    (tu/complete-task {:user tu/mock-user-manager
                       :task-id task-id}))

  (testing "activity submission happy path yields expected status changes"
    (is (= :activity.status/in-progress (act1-status)))
    (tu/local-login tu/mock-user-carla-consultant)
    (is (thrown? Exception
                 (tu/local-command :activity/submit-for-review {:activity-id (act1-id)})))
    (is (= :activity.status/in-progress (act1-status)))
    ;; project manager required for submission
    (tu/local-login tu/mock-user-manager)
    (tu/local-command :activity/submit-for-review {:activity-id (act1-id)})    
    (is (= :activity.status/in-review (act1-status)))
    (tu/local-login tu/mock-user-edna-consultant)
    (is (thrown? Exception
                 (tu/local-command :activity/review {:activity-id (act1-id) :status :activity.status/completed})))
    ;; project owner is required for review
    (tu/local-login tu/mock-user-manager)
    (is (= :activity.status/in-review (act1-status)))
    (tu/local-login tu/mock-user-manager)
    (tu/local-command :activity/review {:activity-id (act1-id) :status :activity.status/completed})
    (is (= :activity.status/completed (act1-status)))))


;; repl compatible fixtureless draft version below for later repl iteration
#_(do 
  (def *gdata (atom {}))
  (def *actid 56937110132752661)
  (defn repltest []
    (tu/local-login tu/mock-user-manager)
    (binding [tu/*global-test-data* *gdata]
      (let [task-id (tu/create-task {:user tu/mock-user-manager :activity *actid} :task-id)]    
        (is (some? task-id)))
      (doseq [task-id-map (:activity/tasks (d/pull (tu/db) '[:activity/name :activity/tasks] *actid))]
        (tu/complete-task {:user tu/mock-user-manager
                           :task-id (:db/id task-id-map)}))
      
      (tu/local-command :activity/submit-for-review {:activity-id *actid :user tu/mock-user-manager})
      (tu/local-command :activity/review {:activity-id *actid :user tu/mock-user-manager :status :activity.status/completed})
      
      (println (= :completed (->> *actid (d/pull (tu/db) '[:activity/status] ) :activity/status :db/ident ))))))
