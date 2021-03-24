(ns teet.account.account-commands-test
  (:require [clojure.test :refer [is deftest testing use-fixtures]]
            [teet.test.utils :as tu]
            teet.account.account-commands
            [datomic.client.api :as d]))

(use-fixtures :each tu/with-environment (tu/with-db))

(deftest change-account-email
  (testing "Can't change email if it would cause duplicate"
    (tu/is-thrown-with-data?
     {:teet/error :email-address-already-in-use}
     (tu/local-command tu/mock-user-boss
                       :account/update
                       {:user/email "edna.e.consultant@example.com"
                        :user/phone-number "123123"})))

  (testing "Can change email to unique value"
    (tu/local-command tu/mock-user-boss
                      :account/update
                       {:user/email "totally-new-email@example.com"
                        :user/phone-number "123123"})
    (is (= (ffirst
            (d/q '[:find ?email
                   :where [?boss :user/email ?email]
                   :in $ ?boss]
                 (tu/db) tu/mock-user-boss))
           "totally-new-email@example.com"))))
