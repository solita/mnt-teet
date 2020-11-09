(ns teet.util.datomic-test
  (:require [clojure.test :refer :all]
            [teet.util.datomic :as du]))

(deftest enum->kw
  (is (nil? (du/enum->kw nil)))
  (is (= :keyword (du/enum->kw :keyword)))
  (is (= :keyword (du/enum->kw {:db/ident :keyword})))
  (is (thrown? AssertionError (du/enum->kw [:not :kw :or :enum :map]))))

(deftest enum=
  (is (du/enum= :kw :kw))
  (is (du/enum= :kw {:db/ident :kw}))
  (is (du/enum= {:db/ident :kw} :kw))
  (is (du/enum= nil nil))
  (is (not (du/enum= nil :kw)))
  (is (thrown? AssertionError (du/enum= :kw [:not :kw :or :enum :map]))))
