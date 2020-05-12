(ns ^:db teet.login.login-commands-test
  (:require [clojure.test :refer [deftest use-fixtures is testing]]
            [teet.util.datomic :as du]
            [teet.test.utils :as tu]
            [teet.login.login-commands :as login-commands]))

(use-fixtures :each
  tu/with-global-data
  tu/with-environment
  (tu/with-db))

(def test-user
  {:user/person-id "EE21122331112"
   :user/given-name "John"
   :user/family-name "Random"})

(defn- user-info-is-correct? [user-from-db user-from-tara]
  (and (= (:user/person-id user-from-db)
          (:user/person-id user-from-tara))
       (= (:user/given-name user-from-db)
          (:user/given-name user-from-tara))
       (= (:user/family-name user-from-db)
          (:user/family-name user-from-tara))))

(deftest ensure-user!
  (testing "If there is no user with the given person id, one is added"
    (is (nil? (-> (du/entity (tu/db) [:user/person-id (:user/person-id test-user)]) :db/id)))
    (login-commands/ensure-user! (tu/connection)
                                 (:user/person-id test-user)
                                 (:user/given-name test-user)
                                 (:user/family-name test-user))
    (let [user-from-db (du/entity (tu/db) [:user/person-id (:user/person-id test-user)])]
      (is (some? (:db/id user-from-db)) "The user exists")
      (is (user-info-is-correct? user-from-db test-user)
          "The user info is correct")))

  (testing "If the user given and family name don't match those in db, they're updated"
    (login-commands/ensure-user! (tu/connection)
                                 (:user/person-id test-user)
                                 (:user/given-name test-user)
                                 "Rambo")
    (let [user-from-db (du/entity (tu/db) [:user/person-id (:user/person-id test-user)])]
      (is (user-info-is-correct? user-from-db
                                 (assoc test-user
                                        :user/family-name "Rambo"))
          "The user info has changed"))))
