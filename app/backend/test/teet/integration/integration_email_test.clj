(ns teet.integration.integration-email-test
  (:require [teet.integration.integration-email :as integration-email]
            [clojure.test :refer [deftest is] :as t]
            [teet.test.utils :as tu]
            [clojure.string :as str]))

(def outbox (atom [:server {} :msg {}]))

(defn- update-outbox [server msg] (swap! outbox conj server msg))

(defn with-outbox []
  (fn [tests]
    (reset! outbox [])
    (with-redefs [integration-email/send-email-smtp!* update-outbox]
      (tests))))

(t/use-fixtures :each (with-outbox))

(def test-message {:from "test@example.com"
                   :to "foo.barsky@example.com"
                   :subject "Hello there"
                   :parts [{:headers {"Content-Type" "text/plain"}
                            :body "hello"}]})
(def test-smtp-config
  {:server
   {:host "localhost", :user "user",
    :pass "pass", :port 567, :tls true}})

(deftest email-subject-prefix
  (tu/run-with-config
   {:email {:subject-prefix "[TESTING]"}}
   (integration-email/send-email! test-message)
   (is (= "[TESTING] Hello there"
          (:subject (second @outbox))))))

(deftest email-subject-no-prefix
  (tu/run-with-config
   {:email {:subject-prefix nil}}
   (integration-email/send-email! test-message)
   (is (= "Hello there"
          (:subject (second @outbox))))))

(deftest data-protection-footer
  (tu/run-with-config
   {:email {:contact-address "FOO@EXAMPLE.COM"}}
   (integration-email/send-email! test-message)
   (let [txt (get-in @outbox [1 :parts 0 :body])]
     (is (str/includes? txt "e-posti aadressiga FOO@EXAMPLE.COM")
         "body contains estonian footer")
     (is (str/includes? txt "e-mail address FOO@EXAMPLE.COM")
         "body contains english footer"))))

(deftest smtp-config-map
  (tu/run-with-config
    {:email test-smtp-config}
    (integration-email/send-email! test-message)
    (let [smtp-conf (first @outbox)]
      (is (str/includes? (:host smtp-conf) "localhost")
        "SMTP configuration contains host name")
      (is (str/includes? (:user smtp-conf) "user")
        "SMTP configuration contains user name")
      (is (str/includes? (:pass smtp-conf) "pass")
        "SMTP configuration contains user password")
      (is (str/includes? (:port smtp-conf) "567")
        "SMTP configuration contains port number")
      (is (str/includes? (:tls smtp-conf) "true")
        "SMTP configuration contains TLS switch"))))
