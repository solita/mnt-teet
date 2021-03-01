#!/usr/bin/env bb
(require '[clojure.java.io :as io])
(require '[cheshire.core :as cheshire])
(require '[clojure.java.shell :as sh])
(require '[clojure.string :as str])
(require '[org.httpkit.client :as http])
(require '[clojure.walk :as walk])

(defn read-report [f]
  (cheshire/decode (slurp f) keyword))

(defn stats [reports]
  (reduce (fn [stats report]
            (merge-with +
                        stats
                        (select-keys (:stats report)
                                     [:testsRegistered :suites :tests
                                      :skipped :passes :failures])))
          {} reports))

(defn success?
  "Tests are successfull if there are some passes (tests were run)
  and no failures."
  [{:keys [passes failures]}]
  (and (pos? passes)
       (zero? failures)))

(defn webhook-url []
  (let [{:keys [exit out] :as res}
        (sh/sh "aws" "ssm" "get-parameters"
               "--names" "/teet/slack/webhook-url"
               "--query" "Parameters[0].Value"
               "--output" "text")]
    (if-not (zero? exit)
      (throw (ex-info "Can't determine Slack webhook URL"
                      res))
      (str/trim out))))

(defn collect-failing-tests
  ([suites] (collect-failing-tests [] suites))
  ([prefix suites]
   (mapcat
    (fn [{:keys [title suites tests]}]
      (concat (collect-failing-tests (if-not (str/blank? title)
                                       (conj prefix title)
                                       prefix)
                                     suites)
              (map #(assoc % :title
                           (if (seq prefix)
                             (str "*" (str/join " / " prefix) "* " (:title %))
                             (:title %)))
                   (filter :fail tests))))
    suites)))

(defn format-slack-message [reports]
  {:text "Cypress tests failed"
   :icon_emoji ":thisisfine:"
   :blocks
   (into [{:type "section"
           :text {:type "mrkdwn"
                  :text (str "Cypress tests failed for "
                             (System/getenv "CODEBUILD_RESOLVED_SOURCE_VERSION"))}}]
         (for [{:keys [results]} reports
               :let [failures (collect-failing-tests results)]
               :when (seq failures)]
           {:type "section"
            :text {:type "mrkdwn"
                   :text (str (count failures) " failures:\n"
                              (str/join
                               "\n"
                               (for [{:keys [title] :as f} failures]
                                 (str " - " title))))}}))})
;; Main
(let [reports (mapv read-report
                    (seq (.listFiles (io/file "cypress/reports/mocha"))))
      stats (stats reports)]
  (when-not (success? stats)
    (let [url (webhook-url)]
      @(http/post url {:headers {"Content-Type" "application/json"}
                         :body (cheshire/encode (format-slack-message reports))})
      "ok")))
