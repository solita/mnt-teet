(ns ^:db teet.project.project-commands-test
  (:require [clojure.test :refer [deftest is testing] :as t]
            [teet.test.utils :as tu]
            [datomic.client.api :as d]))

(t/use-fixtures :each
  tu/with-environment
  (tu/with-db))

(deftest assigning-pm-gives-permission
  (let [current-manager #(get-in (tu/entity (tu/->db-id "p1"))
                                 [:thk.project/manager :user/id] :no-manager)]
    (testing "Initially p1 doesn't have a manager"
      (is (= :no-manager (current-manager))))

    (testing "Assigning p1 project manager"
      (tu/local-command tu/mock-user-boss
                        :thk.project/update
                        {:thk.project/id "11111"
                         :thk.project/manager {:user/id (second tu/mock-user-edna-consultant)}})

      (testing "sets the new manager"
        (is (= (second tu/mock-user-edna-consultant)
               (current-manager))))


      (testing "gives the new pm permission"
        (let [permissions (d/q '[:find (pull ?p [:permission/valid-from])
                                 :where
                                 [?user :user/permissions ?p]
                                 [?p :permission/role :manager]
                                 [?p :permission/projects ?project]
                                 [?p :permission/valid-from ?valid-from]
                                 [(.before ?valid-from ?now)]
                                 [(missing? $ ?p :permission/valid-until)]
                                 :in
                                 $ ?user ?project ?now]
                               (tu/db)
                               tu/mock-user-edna-consultant
                               [:thk.project/id "11111"]
                               (java.util.Date.))]
          (is (seq permissions) "permission is present"))))))
