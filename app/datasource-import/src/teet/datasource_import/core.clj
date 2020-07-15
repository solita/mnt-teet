(ns teet.datasource-import.core
  "Main for TEET datasource import."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [teet.datasource-import.shp :as shp]
            [teet.util.string :as string]
            [teet.auth.jwt-token :as jwt-token])
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)
           (java.util.zip ZipEntry ZipInputStream)
           (org.apache.commons.io FileUtils)))

(defn valid-api? [{:keys [api-url api-secret]}]
  (and (not (str/blank? api-url))
       (not (str/blank? api-secret))))

(defn valid-datasource? [{:keys [datasource-id]}]
  (integer? datasource-id))

(defn auth-headers [{api-secret :api-secret}]
  {"Authorization" (str "Bearer "
                        (jwt-token/create-backend-token api-secret))})

(defn fetch-datasource-config [{:keys [api-url datasource-id] :as ctx}]
  (let [response (client/get (str api-url "/datasource?id=eq." datasource-id)
                             {:as :json
                              :headers (auth-headers ctx)})]
    (println "fetched datasource config")
    (flush)
    (assoc ctx :datasource (-> response :body first))))

(defn download-datasource [{{:keys [url]} :datasource :as ctx}]
  (let [download-path (Files/createTempDirectory
                       "datasource-download"
                       (into-array FileAttribute []))
        download-file (io/file (.toFile download-path)
                               "datasource")]
    (println "Downloading" url "to" (.getAbsolutePath download-file))
    (with-open [out (io/output-stream download-file)]
      (let [{in :body
             headers :headers} (client/get url {:as :stream})]
        (io/copy in out)
        (assoc ctx
               :downloaded-datasource {:path download-path
                                       :file download-file
                                       :content-type (get headers :content-type)})))))


(defn extract-datasource [{dds :downloaded-datasource :as ctx}]
  (when (= "application/zip" (:content-type dds))
    (with-open [in (ZipInputStream. (io/input-stream (:file dds)))]
      (loop []
        (when-let [entry (.getNextEntry in)]
          (let [file (io/file (.toFile (:path dds))
                              (.getName entry))]
            (println "Extract" (.getName entry) "to"
                     (.getAbsolutePath file))
            (with-open [out (io/output-stream file)]
              (io/copy in out)))
          (recur)))))
  ctx)

(defn existing-features-ids [{:keys [api-url api-secret] :as ctx}]
  ;; memory usage note:
  ;; (clj-memory-meter.core/measure (existing-features-ids (make-config ["example-config.edn"])))
  ;; gives 97 kB/1000 rows and 183 MB for the 2M rows in current db as of this writing, leaving
  ;; a lot of headroom, but conceivably could outgrow the smallest 3GB CodeBuild VM some day.
  (println "getting existing features")
  (flush)
  (let [get-url (str api-url "/feature?select=id,datasource_id")
        resp (client/get get-url
                         {:headers (merge (auth-headers {:api-secret api-secret})
                                          {"Content-Type" "application/json"})})
        rows (cheshire/decode (:body resp))
        id-to-datasource (into {} (for [m rows] [(get m "id") (get m "datasource_id")]))]
    id-to-datasource))

(defn read-features
  "Read features from downloaded source. Assocs :features
  function to context to avoid retaining the head."
  [{dds :downloaded-datasource
    ds :datasource
    :as ctx}]
  (assoc ctx
         :old-features (existing-features-ids ctx) ;; pre-existing ids, to detect deletions
         :features (case (:content_type ds)
                     "SHP" #(shp/read-features-from-path (:path dds)))))

(defn property-pattern-fn [pattern]
  (fn [{attrs :attributes}]
    (string/interpolate pattern attrs)))

(def retry-delay-ms {5      0
                     4   5000
                     3  30000
                     2  60000
                     1 120000})

(def retry-statuses #{502 503 504}) ; bad gateway, serv unavailable, gw timeout

(defn patient-post [url params]
  (loop [tries 5]
    (let [last-exception (atom nil)
          result
          (try
            (assert (not (neg? tries)))
            (client/post url
                         params)
            'ok
            (catch clojure.lang.ExceptionInfo e
              (println "caught exception, status" (:status (ex-data e)))
              (if (retry-statuses (:status (ex-data e)))
                (do
                  (reset! last-exception e)
                  'retry)
                ;; else
                (throw e))))]
      (if (and (pos? tries) (= 'retry result))
        (let [delay (get retry-delay-ms tries 20000)]
          (println "retry #" (- 5 tries))          
          (println "retry delay" delay)
          ; (Thread/sleep delay)
          (recur (dec tries)))
        ;; else
        (if (= 'retry result)
          (throw (deref last-exception))
          result)))))

(defn upsert-features [{:keys [features api-url datasource-id datasource old-features] :as ctx}]  
  (let [->label (property-pattern-fn (:label_pattern datasource))
        ->id (property-pattern-fn (:id_pattern datasource))
        old-feature-id-set (into #{}
                                 (map first
                                      (filter #(= datasource-id (second %))
                                              old-features)))
        features-data (features)
        new-feature-id-set (into #{} (map ->id features-data))
        deleted? (clojure.set/difference old-feature-id-set new-feature-id-set)]
    (println "POSTing to " (str api-url "/feature"))
    (doseq [features-chunk (partition-all 50
                                    ;; Add :i attribute that can be used as fallback id
                                    (map-indexed
                                     (fn [i feature]
                                       (update feature :attributes
                                               assoc :i i))
                                     features-data))]
      (print ".") (flush)
      (patient-post
       (str api-url "/feature")
       {:headers (merge
                  (auth-headers ctx)
                  {"Prefer" "resolution=merge-duplicates"
                   "Content-Type" "application/json"})
        :body (cheshire/encode
               (for [{:keys [geometry attributes] :as f} features-chunk]
                 ;; Feature as JSON
                 {:datasource_id datasource-id
                  :id (->id f)
                  :label (->label f)
                  :geometry geometry
                  :deleted false
                  :properties attributes}))}))
    (when (not-empty deleted?)
      (print "\nMarking features absent from import file as deleted.\n")
      (patient-post
       (str api-url "/feature")
       {:headers (merge
                  (auth-headers ctx)
                  {"Prefer" "resolution=merge-duplicates"
                   "Content-Type" "application/json"})
        :body (cheshire/encode
               (for [deleted-feature-id deleted?]                 
                 {:datasource_id datasource-id
                  :id deleted-feature-id
                  :deleted true}))}))
    ctx))

(defn delete-working-files [{{path :path} :downloaded-datasource :as ctx}]
  (println "Delete path:" path)
  (FileUtils/deleteDirectory (.toFile path))
  ctx)

(defn dump-ctx [ctx]
  (spit "debug-ctx" (pr-str ctx)))

(defn get-all-datasource-ids [{:keys [api-url api-secret]}]
  (->> (client/get (str api-url "/datasource?select=id")
                   {:headers (merge (auth-headers {:api-secret api-secret})
                                    {"Prefer" "resolution=merge-duplicates"
                                     "Content-Type" "application/json"})})
       :body
       cheshire/decode
       (mapv #(get % "id"))))

(defn make-config [[config-file datasource-id]]
  {:post [(seq (:datasources %))]}
  (let [config (if (= "env" config-file)
                 {:api-url (System/getenv "TEET_API_URL")
                  :api-secret (System/getenv "TEET_API_SECRET")}
                 ;; else
                 (do
                   (assert (and config-file
                                (.canRead (io/file config-file)))
                           "Specify config file to read")
                   (-> config-file slurp read-string)))]
    (assert (valid-api? config)
            "Specify :api-url and :api-secret")
    (assoc config
           :datasources
           (or (some-> datasource-id
                       Long/parseLong
                       vector)
               (do (println "No datasource id provided, importing all of them.")
                   (get-all-datasource-ids config))))))

(defn -main [& args]
  (let [config (make-config args)]
    (println "Starting import")
    (doseq [datasource-id (:datasources config)]
      (-> (assoc config :datasource-id datasource-id)
          fetch-datasource-config
          download-datasource
          extract-datasource
          read-features
          upsert-features
          delete-working-files))))
