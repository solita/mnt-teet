(ns ^:db teet.admin.admin-commands-test
  (:require [clojure.test :refer :all]
            teet.admin.admin-commands
            [teet.util.datomic :as du]
            [teet.test.utils :as tu]
            [teet.user.user-db :as user-db]
            [datomic.client.api :as d]))

(use-fixtures :each tu/with-environment (tu/with-db) tu/with-global-data)

(deftest create-user
  (tu/give-admin-permission tu/mock-user-boss)

  (testing "New user can be created"
    (is (tu/local-command tu/mock-user-boss
                          :admin/create-user
                          {:user/person-id "EE44556677880"
                           :user/email "test1@test.com"})))

  (testing "Proper permissions are required"
    (is (thrown? Exception
                 (tu/local-command tu/mock-user-carla-consultant
                                   :admin/create-user
                                   {:user/person-id "EE44556677880"
                                    :user/email "test2@test.com"}))))

  (testing "Person id needs to be provided and valid (for some value of valid)"
    (is (:body (tu/local-command tu/mock-user-boss
                                 :admin/create-user
                                 {:user/person-id "invalid"
                                  :user/email "test3@test.com"}))
        "Spec validation failed")
    (is (:body (tu/local-command tu/mock-user-boss
                                 :admin/create-user
                                 {}))
        "Spec validation failed"))

  (testing "Creating another user with the same email fails"
    (tu/is-thrown-with-data?
     {:teet/error :email-address-already-in-use}
     (tu/local-command tu/mock-user-boss
                       :admin/create-user
                       {:user/person-id "EE55667788990"
                        :user/email "test1@test.com"}))))

(deftest edit-user-checks-unique-email
  (tu/give-admin-permission tu/mock-user-boss)
  (doseq [u [{:user/person-id "EE11223344556" :user/email "foo@example.com"}
             {:user/person-id "EE11223344557" :user/email "bar@example.com"}]]
    (tu/local-command tu/mock-user-boss :admin/create-user u))

  (tu/is-thrown-with-data?
   {:teet/error :email-address-already-in-use}
   (tu/local-command tu/mock-user-boss
                     :admin/edit-user
                     {:user/person-id "EE11223344557"
                      :user/email "foo@example.com"}))

  (is (= "bar@example.com" (:user/email (d/pull (tu/db) [:user/email]
                                                [:user/person-id "EE11223344557"])))
      "email hasn't been changed")

  (tu/local-command tu/mock-user-boss
                    :admin/edit-user
                    {:user/person-id "EE11223344557"
                     :user/email "bar1@example.com"})

  (is (= "bar1@example.com" (:user/email (d/pull (tu/db) [:user/email]
                                                 [:user/person-id "EE11223344557"])))
      "email has been changed"))

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
