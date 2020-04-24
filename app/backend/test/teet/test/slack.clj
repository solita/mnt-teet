(ns teet.test.slack
  "Report test status to slack"
  (:require [kaocha.report :as report]
            [kaocha.hierarchy :as hierarchy]
            [clojure.java.io :as io]
            [cheshire.core :as cheshire]
            [org.httpkit.client :as client]
            [clojure.string :as str]
            [clojure.java.shell :refer [sh]]))

(def results (atom nil))

(defmulti slack :type :hierarchy #'hierarchy/hierarchy)

(defmethod slack :default [m]
  (println "SOME EVENT" (pr-str m)))

(defmethod slack :begin-test-suite [m]
  (println "START SUITE")
  (reset! results {:success 0
                   :fail 0
                   :error 0
                   :failures []
                   :errors []}))

(defmethod slack :pass [_]
  (swap! results update :success inc))

(defmethod slack :fail [{msg :message testable :kaocha/testable}]
  (swap! results
         #(-> %
              (update :fail inc)
              (update :failures conj (assoc (:kaocha.testable/meta testable)
                                            :message msg)))))


(defmethod slack :error [{msg :message testable :kaocha/testable}]
  (swap! results
         #(-> %
              (update :error inc)
              (update :errors conj (assoc (:kaocha.testable/meta testable)
                                          :message msg)))))

(defn list-tests [tests]
  (str/join
   "\n"
   (for [{:keys [line file name message]} tests]
     (str "- `[" file ":" line "] " name "`: " message))))

(defn webhook-url []
  (-> (sh "aws" "ssm" "get-parameters" "--names" "/teet/slack/webhook-url")
      :out
      (cheshire/decode keyword)
      (get-in [:Parameters 0 :Value])))

(defmethod slack :summary [_]
  (let [{:keys [success fail error failures errors]} @results]
    (when (or (pos? fail)
              (pos? error))
      @(client/post (webhook-url)
                    {:headers {"Content-Type" "application/json"}
                     :body
                     (cheshire/encode
                      {:blocks [{:type :section
                                 :text {:type :mrkdwn
                                        :text
                                        (str "db-tests failed: "
                                             success " success, "
                                             fail " fail, "
                                             error " error.\n\n"
                                             (when (seq failures)
                                               (str "*Failures*\n"
                                                    (list-tests failures)))
                                             (when (seq errors)
                                               (str "\n\n*Errors*\n"
                                                    (list-tests errors))))}}]})}))))

(let [out (io/writer (io/file "test.out"))]
  (defn r [m]
    (.write out (str "TYPE: " (:type m) "\n"
                     (pr-str m) "\n"
                     "-----------------------\n"
                      ))))

(def report [slack r])
