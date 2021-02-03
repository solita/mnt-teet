(ns teet.integration.integration-email-test
  (:require [teet.integration.integration-email :as integration-email]
            [clojure.test :refer [deftest is] :as t]
            [teet.test.utils :as tu]
            [clojure.string :as str]))

(def outbox (atom []))

(defn with-outbox []
  (fn [tests]
    (reset! outbox [])
    (with-redefs [integration-email/send-email!* #(swap! outbox conj %)]
      (tests))))

(t/use-fixtures :each (with-outbox))

(def test-message {:from "test@example.com"
                   :to "foo.barsky@example.com"
                   :subject "Hello there"
                   :parts [{:headers {"Content-Type" "text/plain"}
                            :body "hello"}]})

(deftest email-subject-prefix
  (tu/run-with-config
   {:email {:subject-prefix "[TESTING]"}}
   (integration-email/send-email! test-message)
   (is (= "[TESTING] Hello there"
          (:subject (first @outbox))))))

(deftest email-subject-no-prefix
  (tu/run-with-config
   {:email {:subject-prefix nil}}
   (integration-email/send-email! test-message)
   (is (= "Hello there"
          (:subject (first @outbox))))))

(deftest data-protection-footer
  (tu/run-with-config
   {:email {:contact-address "FOO@EXAMPLE.COM"}}
   (integration-email/send-email! test-message)
   (let [txt (get-in @outbox [0 :parts 0 :body])]
     (is (str/includes? txt "e-posti aadressiga FOO@EXAMPLE.COM")
         "body contains estonian footer")
     (is (str/includes? txt "e-mail address FOO@EXAMPLE.COM")
         "body contains english footer"))))
