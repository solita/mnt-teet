#!/usr/bin/env bb
(require '[cheshire.core :as cheshire])
(require '[babashka.curl :as curl])
(require '[clojure.java.shell :as sh])
(require '[clojure.string :as str])


(defn without-ion-dep [func]
  (let [orig-deps (slurp "app/backend/deps.edn")]
    (spit "app/backend/deps.edn"
          (str/replace orig-deps
                       "com.datomic/ion {:mvn/version \"0.9.35\"}"
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

(defn post-annotations [url head-sha vulns]
  (println "Patching " (count vulns) " annotations to " url " for HEAD SHA " head-sha)
  (curl/patch
   url
   {:headers {"Content-Type" "application/json"
              "Accept" "application/vnd.github.antiope-preview+json"
              "Authorization" (str "Bearer " (System/getenv "GITHUB_TOKEN"))}
    :body (cheshire/encode
           {:name "nvd"
            :head_sha head-sha
            :output {:title "NVD scan output"
                     :summary (str (count vulns) " vulnerabilities")
                     :annotations (mapv
                                   (fn [{:keys [name description source severity]}]
                                     {:path "app/backend/deps.edn"
                                      ;; FIXME: should track the line?
                                      :start_line 1 :end_line 1
                                      :annotation_level "warning"
                                      :message (str "[" severity "] " name ": " description)})
                                   vulns)}})}))

(when (nil? (System/getenv "GITHUB_REPOSITORY"))
  (println "Not running in github actions")
  (System/exit 1))

(def annotation-url (str "https://api.github.com/repos/"
                         (System/getenv "GITHUB_REPOSITORY")
                         "/check-runs/"
                         (System/getenv "GITHUB_RUN_ID")))

(def head-sha (str/trim (:out (sh/sh "git" "rev-parse" "HEAD"))))

;;;;;;;;;;

(without-ion-dep run-nvd)
(let [vulns (mapcat dependency-vulns (:dependencies (read-report)))]
  (post-annotations annotation-url head-sha vulns))
