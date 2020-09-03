#!/usr/bin/env bb
(require '[cheshire.core :as cheshire])
(require '[babashka.curl :as curl])
(require '[clojure.java.shell :as sh])
(require '[clojure.string :as str])
(require '[clojure.set :as set])


(defn without-ion-dep [func]
  (let [orig-deps (slurp "app/backend/deps.edn")]
    (spit "app/backend/deps.edn"
          (str/replace orig-deps
                       "com.datomic/ion {:mvn/version \"0.9.43\"}"
                       ""))
    (try
      (func)
      (finally
        (spit "app/backend/deps.edn" orig-deps)))))

(defn run-nvd []
  (sh/with-sh-dir "app/backend"
    (sh/sh "clojure" "-A:clj-nvd" "check")))

(defn read-report []
  (cheshire/decode (slurp "app/backend/target/nvd/dependency-check-report.json") keyword))

(defn dependency-vulns [{:keys [fileName vulnerabilities]}]
  (for [v vulnerabilities]
    (merge {:file fileName}
           (select-keys v [:name :description :source :severity]))))

(defn check-new-vulns [nvd-lock vulns]
  (set/difference (into #{}
                        (map :name)
                        vulns)
                  nvd-lock))

(defn read-nvd-lock []
  (-> "app/backend/nvd-lock.edn" slurp read-string))

(defn post-annotations [url head-sha vulns new-vulns]
  (println "Posting " (count vulns) " annotations with " (count new-vulns) " new vulnerabilities to " url " for HEAD SHA " head-sha)
  (curl/post
   url
   {:headers {"Content-Type" "application/json"
              "Accept" "application/vnd.github.antiope-preview+json"
              "Authorization" (str "Bearer " (System/getenv "GITHUB_TOKEN"))}
    :body (cheshire/encode
           {:name "nvd"
            :head_sha head-sha
            :output {:title "NVD scan output"
                     :summary (str (count new-vulns) " NEW vulnerabilities, " (count vulns) " total")
                     :annotations
                     (into (if (seq new-vulns)
                             [{:path "app/backend/deps.edn"
                               :start_line 1 :end_line 1
                               :annotation_level "failure"
                               :message (str (count new-vulns) " new vulnerabilities: "
                                             (str/join ", " new-vulns))}]
                             [])
                           (map (fn [{:keys [name description severity]}]
                                  {:path "app/backend/deps.edn"
                                   ;; FIXME: should track the line?
                                   :start_line 1 :end_line 1
                                   :annotation_level "warning"
                                   :message (str "[" severity "] " name ": " description)}))
                           vulns)}})}))

(when (nil? (System/getenv "GITHUB_REPOSITORY"))
  (println "Not running in github actions")
  (System/exit 1))

(def annotation-url (str "https://api.github.com/repos/"
                         (System/getenv "GITHUB_REPOSITORY")
                         "/check-runs"))

(def head-sha (str/trim (:out (sh/sh "git" "rev-parse" "HEAD"))))

;;;;;;;;;;

(without-ion-dep run-nvd)
(let [vulns (mapcat dependency-vulns (:dependencies (read-report)))
      new-vulns (check-new-vulns (read-nvd-lock) vulns)]
  #_(post-annotations annotation-url head-sha vulns new-vulns)
  (when (seq new-vulns)
    (println (count new-vulns) " NEW vulnerabilities: " (str/join ", " new-vulns))
    (System/exit 1)))
