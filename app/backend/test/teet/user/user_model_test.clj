(ns teet.user.user-model-test
  (:require [clojure.test :refer [deftest testing is]]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [teet.user.user-model :as user-model])
  (:import (java.util Date)))

(deftest permissions-valid-at
  (is (empty? (user-model/permissions-valid-at {:user/permissions nil} (Date.)))
      "Returns empty seq for user with no permissions")

  (is (empty? (user-model/permissions-valid-at {:user/permissions [{:permission/role :admin}]} (Date.)))
      "Ignores permission if `valid-from` not present")

  (is (empty? (user-model/permissions-valid-at {:user/permissions [{:permission/role :admin
                                                                    :permission/valid-from (-> (Date.)
                                                                                               tc/from-date
                                                                                               (t/minus (t/days 7))
                                                                                               tc/to-date)
                                                                    :permission/valid-until (-> (Date.)
                                                                                                tc/from-date
                                                                                                (t/minus (t/days 5))
                                                                                                tc/to-date)}]}
                                               (Date.)))
      "Ignores permission if `valid-until` is before `timestamp`")

  (is (empty? (user-model/permissions-valid-at {:user/permissions [{:permission/role :admin
                                                                    :permission/valid-from (-> (Date.)
                                                                                               tc/from-date
                                                                                               (t/plus (t/days 1))
                                                                                               tc/to-date)
                                                                    :permission/valid-until (-> (Date.)
                                                                                                tc/from-date
                                                                                                (t/plus (t/days 2))
                                                                                                tc/to-date)}]}
                                               (Date.)))
      "Ignores permission if `valid-before` is after `timestamp`")

  (let [timestamp (Date.)
        permission {:permission/role :admin
                    :permission/valid-from timestamp
                    :permission/valid-until timestamp}
        permissions (user-model/permissions-valid-at {:user/permissions [permission]}
                                                     timestamp)]
    (is (= (count permissions) 1)
        "The `valid-from` and `valid-until` timestamps are accepted")
    (is (= (first permissions)
           permission)
        "The valid permission is returned")))

(deftest projects-with-valid-permission-at
  (is (empty? (user-model/projects-with-valid-permission-at {:user/permissions nil} (Date.)))
      "Returns empty seq for user with no permissions")

  (let [timestamp (Date.)
        permission {:permission/role :admin
                    :permission/valid-from timestamp
                    :permission/valid-until timestamp}
        projects (user-model/projects-with-valid-permission-at {:user/permissions [permission]}
                                                               timestamp)]
    (is (empty? projects)
        "Returns no projects for global permissions"))

  (let [timestamp (Date.)
        permissions [{:permission/role :manager
                      :permission/valid-from timestamp
                      :permission/valid-until timestamp
                      :permission/projects [{:db/id 1}
                                            {:db/id 2}]}
                     {:permission/role :admin
                      :permission/valid-from timestamp
                      :permission/valid-until timestamp
                      :permission/projects [{:db/id 2}
                                            {:db/id 3}]}]
        projects (user-model/projects-with-valid-permission-at {:user/permissions permissions}
                                                               timestamp)]
    (is (= (count projects) 4)
        "Returns a map for each role-project pair")

    (is (= (set projects)
           #{{:db/id 1 :permission/role :manager}
             {:db/id 2 :permission/role :manager}
             {:db/id 2 :permission/role :admin}
             {:db/id 3 :permission/role :admin}})
        "For each pair returns both the db id and related role")))
