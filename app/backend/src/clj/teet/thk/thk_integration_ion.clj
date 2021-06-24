(ns teet.thk.thk-integration-ion
  "THK integration lambdas.

  Examining CSV output can be done with csvq and jq tools:
  csvq -d \\; -f json \"select * from csvfile\" | jq"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [cheshire.core :as cheshire]
            [teet.log :as log]
            [teet.thk.thk-import :as thk-import]
            [teet.thk.thk-export :as thk-export]
            [teet.environment :as environment]
            [teet.integration.integration-context :as integration-context :refer [ctx->]]
            [teet.integration.integration-s3 :as integration-s3]
            [datomic.client.api :as d]
            [clojure.data.csv :as csv]
            [teet.project.project-geometry :as project-geometry]
            [teet.thk.thk-mapping :as thk-mapping])
  (:import (java.io File)))

(def test-event
  {:input "{\"Records\":[{\"s3\":{\"bucket\":{\"name\":\"teet-dev-csv-import\"},\"object\":{\"key\":\"thk/unprocessed/not_hep.csv\"}}}]}"})

(def processed-directory "thk/processed/")
(def error-directory "thk/error/")
(def log-directory "thk/log/")


(defn- write-file-to-s3 [{{:keys [bucket-name file-key]} :s3
                          file :file
                          :as ctx}]
  (assoc ctx file-key
         (integration-s3/put-object bucket-name
                                    file-key
                                    (io/input-stream file))))

(defn- file->project-rows [{:keys [file] :as ctx}]
  (assoc ctx :project-rows
             (thk-import/parse-thk-export-csv {:input file
                                               :column-mapping thk-mapping/thk->teet-project
                                               :group-by-fn #(get % :thk.project/id)})))


(defn- file->contract-rows [{:keys [file] :as ctx}]
  (if (environment/feature-enabled? :contracts)
    (assoc ctx :contract-rows
               (thk-import/parse-thk-export-csv {:input file
                                                 :column-mapping thk-mapping/thk->teet-contract
                                                 :group-by-fn (fn [val]
                                                                (select-keys val [:thk.contract/procurement-part-id
                                                                                  :thk.contract/procurement-id]))}))
    (do
      (log/info "Skipping contract parsing, feature not enabled")
      ctx)))

(defn csv->file [{csv :csv :as ctx}]
  (with-open [baos (java.io.ByteArrayOutputStream.)
              writer (java.io.OutputStreamWriter. baos "UTF-8")]
    ;; Write UTF-8 byte order mark
    (.write baos (int 0xEF))
    (.write baos (int 0xBB))
    (.write baos (int 0xBF))
    (csv/write-csv writer csv :separator \;)
    (.flush writer)
    (assoc ctx :file (.toByteArray baos))))

(defn- integration-uri
  "Determine S3 integration uri for tx metadata use from context."
  [{{:keys [bucket file-key]} :s3}]
  (str "s3://" bucket "/" file-key))

(defn- upsert-projects [{:keys [project-rows connection] :as ctx}]
  (let [import-tx-result (thk-import/import-thk-projects! connection
                                                          (integration-uri ctx)
                                                          project-rows)]
    (assoc ctx
           :db (:db-after import-tx-result))))

(defn- upsert-tasks [{:keys [project-rows connection] :as ctx}]
  (if (environment/feature-enabled? :contracts)             ;; Added because these tasks are related to contracts
    (let [import-tx-result (thk-import/import-thk-tasks! connection
                                                         (integration-uri ctx)
                                                         project-rows)]
      (assoc ctx
        :db (:db-after import-tx-result)))
    (do
      (log/info "Skipping upserting of tasks because feature flag contracts is not enabled")
      ctx)))

(defn- upsert-contracts [{:keys [contract-rows connection] :as ctx}]
  (if (environment/feature-enabled? :contracts)
    (let [import-tx-result (thk-import/import-thk-contracts! connection
                                                             (integration-uri ctx)
                                                             contract-rows)]
      (assoc ctx
        :db (:db-after import-tx-result)))
    (do
      (log/info "Skipping upserting contracts because feature not enabled")
      ctx)))

(defn- move-file [bucket old-key new-key]
  (log/info "Move file in bucket " bucket " from " old-key " => " new-key)
  (integration-s3/copy-object bucket old-key new-key)
  (integration-s3/delete-object bucket old-key))

(defn- change-directory [file-key directory]
  (str directory
       (-> file-key (str/split #"/") last)))

(defn- add-suffix [file-key suffix]
  (str file-key suffix))

(defn- move-file-to-processed [{{:keys [bucket file-key]} :s3 :as ctx}]
  (try
    (let [processed-file-key (-> file-key
                                 (change-directory processed-directory)
                                 (add-suffix (str "." (System/currentTimeMillis))))]
      (move-file bucket file-key processed-file-key))
    (catch Exception e
      (throw (integration-context/ctx-exception ctx "Failed to move file to processed directory" e)))))

(defn- move-file-to-error [{{:keys [bucket file-key]} :s3 :as _ctx}]
  (let [processed-file-key (-> file-key
                               (change-directory error-directory)
                               (add-suffix (str "." (System/currentTimeMillis))))]
    (move-file bucket file-key processed-file-key)))

(defn- write-error-to-log [{{:keys [bucket file-key]} :s3
                            {:keys [message stack-trace]}  :error}]
  (integration-s3/put-object
   bucket
   (-> file-key
       (change-directory log-directory)
       (add-suffix (str (System/currentTimeMillis)
                        ".error.txt")))
   (io/input-stream (.getBytes (str message
                                    "\n"
                                    stack-trace)))))

(defn- update-entity-info [{db :db :as ctx}]
  (let [projects (map first
                      (d/q '[:find (pull ?e [:db/id :integration/id
                                             :thk.project/project-name :thk.project/name
                                             :thk.project/road-nr
                                             :thk.project/carriageway
                                             :thk.project/start-m :thk.project/end-m
                                             :thk.project/custom-start-m :thk.project/custom-end-m])
                             :in $
                             :where [?e :thk.project/road-nr _]]
                           db))]
    (log/info "Update entity info for all" (count projects) "projects.")
    (project-geometry/update-project-geometries!
     (merge {:delete-stale-projects? true}
            (select-keys ctx [:wfs-url]))
     projects)
    ctx))

(defn- on-import-error [{:keys [error] :as ctx}]
  ;; TODO: Metrics?
  (log/error error)
  (move-file-to-error ctx)
  (write-error-to-log ctx)
  nil)

(defn read-to-temp-file
  "Updates the file key in context to a temp file"
  [ctx]
  (update ctx :file (fn [stream]
                      (let [file (File/createTempFile "thk" ".csv")]
                        (io/copy stream file)
                        file))))

(defn delete-temp-file-from-ctx
  "Deletes the temp file created in the earlier step"
  [ctx]
  (io/delete-file (:file ctx))
  (dissoc ctx :file))

(defn- process-thk-file* [event]
  (try
    (let [result (ctx-> {:event event
                         :connection (environment/datomic-connection)
                         :api-url (environment/config-value :api-url)
                         :api-secret (environment/config-value :auth :jwt-secret)
                         :wfs-url (environment/config-value :road-registry :wfs-url)}
                        integration-s3/read-trigger-event
                        integration-s3/load-file-from-s3
                        read-to-temp-file
                        file->project-rows
                        file->contract-rows
                        delete-temp-file-from-ctx
                        upsert-projects
                        upsert-tasks
                        upsert-contracts
                        update-entity-info
                        move-file-to-processed)]
      (log/event :thk-file-processed
                 {:input result}))
    (catch Exception e
      (log/error (.getCause e) "Exception in THK import")
      (on-import-error (ex-data e)))))

;; entrypoint for thk import lambda call (from s3 event)
(defn process-thk-file
  [event]
  (future
    (process-thk-file* event))
  "{\"success\": true}")

(defn import-thk-local-file
  [filepath]
  (try
    (ctx-> {:file (io/input-stream (io/file filepath))
            :connection (environment/datomic-connection)
            :api-url (environment/config-value :api-url)
            :api-secret (environment/config-value :auth :jwt-secret)
            :wfs-url (environment/config-value :road-registry :wfs-url)}
           read-to-temp-file
           file->project-rows
           file->contract-rows
           delete-temp-file-from-ctx
           upsert-projects
           upsert-tasks
           upsert-contracts
           update-entity-info)
    (catch Exception e
      (println e "Exception in import"))))

(defn update-all-project-entity-info []
  (ctx-> {:db (d/db (environment/datomic-connection))
          :api-url (environment/config-value :api-url)
          :api-secret (environment/config-value :auth :jwt-secret)
          :wfs-url (environment/config-value :road-registry :wfs-url)}
         update-entity-info))

(defn export-projects [{conn :connection :as ctx}]
  (assoc ctx :csv (thk-export/export-thk-projects conn)))

(defn- export-projects-to-thk* []
  (try
    (ctx-> {:connection (environment/datomic-connection)
            :s3 {:bucket-name (environment/config-value :thk :export-bucket-name)
                 :file-key (str (environment/config-value :thk :export-dir)
                                "/TEET_THK_"
                                (.format (java.text.SimpleDateFormat. "yyyyMMdd_HHmm") (java.util.Date.))
                                ".csv")}}
           export-projects
           csv->file
           write-file-to-s3)
    (catch Exception e
      (log/error "Error exporting projects CSV to S3: " (pr-str (ex-data e))))))

;; entrypoint for thk-export lambda call (from cloudwatch cron event)
(defn export-projects-to-thk
  [_event] ; ignore event (cron lambda trigger with no payload)
  (future
    (export-projects-to-thk*))
  "{\"success\": true}")

(defn export-projects-to-local-file [filepath]
  (let [dump (fn [{file :file}]
               (with-open [out (io/output-stream filepath)]
                 (.write out file)))]
    (ctx-> {:connection (environment/datomic-connection)}
           export-projects
           csv->file
           dump))
  :ok)


(def date-fixup-test-event
  {:input "{\"integration-id\":\"00000000-0000-0000-7030-a16828e2d9e5\",\"actual-start-date\":\"2012-12-12T11:11:11+00:00\",\"actual-end-date\":\"2012-12-22T12:12:12+00:00\"}"})

(defn activity-by-iid [db iid]
  (when (some? iid)
    (println "gettingfor" iid)
    (let [q-result
          (d/q '[:find ?e
                 :in $ ?iid
                 :where [?e :activity/status _]
                        [?e :integration/id ?iid]]
               db iid)]
      (if (= 1 (count q-result))
        (ffirst q-result)
        (log/warn "multiple activities matching" iid)))))

(defn str->inst [x]
  (when (and (string? x)
             (re-matches #"\d\d\d\d-\d\d-\d\dT\d\d:\d\d:\d\d[\+-]\d\d:\d\d" x))
    (binding [*read-eval* false]
      (read-string (str "#inst " (pr-str x))))))

(defn activity-date-fixup-ion [event]

  (let [decoded (cheshire/decode (:input event))
        {:strs [actual-start-date actual-end-date]} decoded
        integration-id-string (get decoded "integration-id")
        integration-uuid (when integration-id-string
                           (java.util.UUID/fromString integration-id-string))
        db (d/db (environment/datomic-connection))
        activity-eid (activity-by-iid db integration-uuid)
        ed-inst (str->inst actual-end-date)
        sd-inst (str->inst actual-start-date)
        transaction {:db/id activity-eid
                     :activity/actual-start-date sd-inst
                     :activity/actual-end-date ed-inst}]

    (log/info "activity date fixup ion invoked - input:" decoded)
    (cond
      (nil? integration-uuid)
      (cheshire/encode {"error" (str "uuid missing from input, got:" (pr-str decoded))})

      (nil? activity-eid)
      (cheshire/encode {"error" "activity not found"})

      (or (nil? ed-inst) (nil? sd-inst))
      (cheshire/encode {"error" "bad dates"
                        "start-parsed" (str sd-inst)
                        "end-parsed" (str ed-inst)})

      :else
      (do
        (log/info "lambda params ok and activity found, transacting:" transaction)
        (d/transact (environment/datomic-connection)
                    {:tx-data [transaction]})
        (cheshire/encode {"success" "yes"})))))
