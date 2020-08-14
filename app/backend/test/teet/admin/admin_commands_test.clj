(ns teet.admin.admin-commands-test
  (:require [clojure.test :refer :all]
            teet.admin.admin-commands
            [teet.util.datomic :as du]
            [teet.test.utils :as tu]))

(use-fixtures :each tu/with-environment (tu/with-db) tu/with-global-data)

(deftest create-user
  (tu/give-admin-permission tu/mock-user-boss)

  (testing "New user can be created"
    (is (tu/local-command tu/mock-user-boss
                          :admin/create-user
                          {:user/person-id "EE44556677880"})))

  (testing "Proper permissions are required"
    (is (thrown? Exception
                 (tu/local-command tu/mock-user-carla-consultant
                                   :admin/create-user
                                   {:user/person-id "EE44556677880"}))))

  (testing "Person id needs to be provided and valid (for some value of valid)"
    (is (:body (tu/local-command tu/mock-user-boss
                                 :admin/create-user
                                 {:user/person-id "invalid"}))
        "Spec validation failed")
    (is (:body (tu/local-command tu/mock-user-boss
                                 :admin/create-user
                                 {}))
        "Spec validation failed")))

(deftest create-user-global-permissions
  (tu/give-admin-permission tu/mock-user-boss)

  (testing "New user can be granted a global role"
    (tu/local-command tu/mock-user-boss
                      :admin/create-user
                      {:user/person-id "EE44556677880"
                       :user/add-global-permission :admin})
    (let [new-user-permissions (-> (du/entity (tu/db) [:user/person-id "EE44556677880"])
                                   :user/permissions)]
      (is (= (count new-user-permissions) 1))
      (is (= (-> new-user-permissions first :permission/role)
             :admin))))

  (testing "Existing user can be granted a global role"
    (tu/local-command tu/mock-user-boss
                      :admin/create-user
                      {:user/person-id "EE55667788990"})
    (let [new-user-permissions (-> (du/entity (tu/db) [:user/person-id "EE55667788990"])
                                   :user/permissions)]
      (is (= (count new-user-permissions) 0)))

    (tu/local-command tu/mock-user-boss
                      :admin/create-user
                      {:user/person-id "EE55667788990"
                       :user/add-global-permission :admin})
    (let [existing-user-permissions (-> (du/entity (tu/db) [:user/person-id "EE55667788990"])
                                   :user/permissions)]
      (is (= (count existing-user-permissions) 1))
      (is (= (-> existing-user-permissions first :permission/role)
             :admin)))

    ;; Can't add the same global role multiple times
    (tu/local-command tu/mock-user-boss
                      :admin/create-user
                      {:user/person-id "EE55667788990"
                       :user/add-global-permission :admin})
    (let [existing-user-permissions (-> (du/entity (tu/db) [:user/person-id "EE55667788990"])
                                        :user/permissions)]
      (is (= (count existing-user-permissions) 1) "Can't add the same global role multiple times")
      (is (= (-> existing-user-permissions first :permission/role)
             :admin)))

    ;; Can add multiple global roles
    (tu/local-command tu/mock-user-boss
                      :admin/create-user
                      {:user/person-id "EE55667788990"
                       :user/add-global-permission :internal-consultant})
    (let [existing-user-permissions (-> (du/entity (tu/db) [:user/person-id "EE55667788990"])
                                        :user/permissions)]
      (is (= (count existing-user-permissions) 2) "Can add multiple global roles")
      (is (= (->> existing-user-permissions (map :permission/role) set)
             #{:admin :internal-consultant})))))
