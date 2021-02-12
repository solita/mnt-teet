(ns ^:db teet.db-api.db-api-large-text-test
  (:require [teet.db-api.db-api-large-text :refer [with-large-text store-large-text!]]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [teet.integration.postgrest :as postgrest]
            [teet.util.hash :refer [sha256 hex]]
            [clojure.string :as str]))

(def large-text (atom {}))
(def ctx {}) ;; dummy ctx

(use-fixtures :each
  (fn [tests]
    (reset! large-text {})
    ;; Mock PostgREST as we don't have it in CI tests
    (with-redefs [postgrest/rpc
                  (fn [_ name {:keys [text hash]}]
                    (case name
                      :fetch_large_text
                      (@large-text hash)

                      :store_large_text
                      (let [h (hex (sha256 text))]
                        (swap! large-text assoc h text)
                        h)))]
      (tests))))

(deftest small-text
  (testing "Small text is not stored"
    (is (= (store-large-text! ctx #{:text}
                              {:text "hello"})
           {:text "hello"}))
    (is (empty? @large-text))))

(def gen-chars (into [" "]
                     (map (comp str char))
                     (range (int \a) (int \z))))

(deftest large-text-stored
  (testing "Large text is stored"
    (let [text (str/join (repeatedly 2000 #(rand-nth gen-chars)))
          stored (store-large-text! ctx #{:text}
                                    {:text text})]
      (is (str/starts-with? (:text stored) "["))
      (is (str/ends-with? (:text stored) "]"))
      (is (= text (-> @large-text first val)))
      (is (= {:text text}
             (with-large-text ctx #{:text} stored))))))
