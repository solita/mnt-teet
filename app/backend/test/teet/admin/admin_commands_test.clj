(ns ^:db teet.admin.admin-commands-test
  (:require [clojure.test :refer :all]
            teet.admin.admin-commands
            [teet.util.datomic :as du]
            [teet.test.utils :as tu]
            [teet.user.user-db :as user-db]))

(use-fixtures :each tu/with-environment (tu/with-db) tu/with-global-data)

(deftest create-user
  (tu/give-admin-permission tu/mock-user-boss)

  (testing "New user can be created"
    (is (tu/local-command tu/mock-user-boss
                          :admin/create-user
                          {:user/person-id "EE44556677880"
                           :user/email "test@test.com"})))

  (testing "Proper permissions are required"
    (is (thrown? Exception
                 (tu/local-command tu/mock-user-carla-consultant
                                   :admin/create-user
                                   {:user/person-id "EE44556677880"
                                    :user/email "test@test.com"}))))

  (testing "Person id needs to be provided and valid (for some value of valid)"
    (is (:body (tu/local-command tu/mock-user-boss
                                 :admin/create-user
                                 {:user/person-id "invalid"
                                  :user/email "test@test.com"}))
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
                       :user/global-role :admin
                       :user/email "foo@bar.com"})
    (let [new-user-permissions (-> (du/entity (tu/db) [:user/person-id "EE44556677880"])
                                   :user/permissions)]
      (is (= (count new-user-permissions) 1))
      (is (= (-> new-user-permissions first :permission/role)
             :admin))))

  (testing "Existing user can be granted a global role"
    (tu/local-command tu/mock-user-boss
                      :admin/create-user
                      {:user/person-id "EE55667788990"
                       :user/email "new-user@test.com"})
    (let [new-user-permissions (-> (du/entity (tu/db) [:user/person-id "EE55667788990"])
                                   :user/permissions)]
      (is (= (count new-user-permissions) 0)))

    (tu/local-command tu/mock-user-boss
                      :admin/edit-user
                      {:user/person-id "EE55667788990"
                       :user/global-role :admin
                       :user/email "admin@admin.com"})
    (let [existing-user-permissions (-> (du/entity (tu/db) [:user/person-id "EE55667788990"])
                                        :user/permissions)]
      (is (= (count existing-user-permissions) 1))
      (is (= (-> existing-user-permissions first :permission/role)
             :admin)))

    ;; Can't add the same global role multiple times
    (tu/local-command tu/mock-user-boss
                      :admin/create-user
                      {:user/person-id "EE55667788990"
                       :user/global-role :admin})
    (let [existing-user-permissions (-> (du/entity (tu/db) [:user/person-id "EE55667788990"])
                                        :user/permissions)]
      (is (= (count existing-user-permissions) 1) "Will override the previous permission")
      (is (= (-> existing-user-permissions first :permission/role)
             :admin)))

    ;; Can add multiple global roles
    (tu/local-command tu/mock-user-boss
                      :admin/edit-user
                      {:user/person-id "EE55667788990"
                       :user/email "test@test.com"
                       :user/global-role :internal-consultant})
    (let [existing-user-permissions (user-db/users-valid-global-permissions
                                      (tu/db)
                                      (:db/id (du/entity (tu/db) [:user/person-id "EE55667788990"])))
          ]
      (is (= (count existing-user-permissions) 1) "Can't add multiple global roles")
      (is (= (->> existing-user-permissions (map :permission/role) set)
             #{:internal-consultant})
          "The latest global role stays as the only valid role"))))
