(ns teet.environment-test
  (:require [clojure.test :refer :all]
            [teet.environment :as environment]))

(defmacro with-environment [env & body]
  `(let [old-env# (deref environment/config)]
     (reset! environment/config ~env)
     ~@body
     (reset! environment/config old-env#)))

(deftest feature-enabled?
  (testing "feature-enabled? returns true if and only if the feature is defined in config"
    (with-environment {:enabled-features #{}}
      (is (not (environment/feature-enabled? :test-feature))))
    (with-environment {:enabled-features #{:test-feature}}
      (is (environment/feature-enabled? :test-feature)))))

(deftest with-feature
  (testing "with-feature evaluates its body if and only if the feature is defined"
    (with-environment {:enabled-features #{}}
      (is (nil? (environment/when-feature :test-feature
                                          :not-evaluated))))
    (with-environment {:enabled-features #{:test-feature}}
      (is (= (environment/when-feature :test-feature
                                       :evaluated)
             :evaluated)))))

(deftest parse-enabled-features
  (testing "parses a comma separated list of feature names into keyword set, trimming whitespace"
    (is (= (environment/parse-enabled-features " foo , bar-bar,quux,,,")
           #{:foo :bar-bar :quux}))))
