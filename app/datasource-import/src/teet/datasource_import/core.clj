(ns teet.datasource-import.core
  "Main for TEET datasource import."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [teet.datasource-import.shp :as shp]
            [teet.util.string :as string]
            [teet.auth.jwt-token :as jwt-token]
            [clojure.set :as set])
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
  (println "getting existing features from data source id #" (:datasource-id ctx))
  (flush)
  (assoc ctx :old-features
         (future
           (let [get-url (str api-url "/feature?select=id"
                              "&datasource_id=eq." (:datasource-id ctx)
                              "&deleted=is.false")
                 resp (client/get get-url
                                  {:headers (merge (auth-headers {:api-secret api-secret})
                                                   {"Content-Type" "application/json"})})
                 rows (cheshire/decode (:body resp))]
             (into #{}
                   (map #(get % "id"))
                   rows)))))

(defn read-features
  "Read features from downloaded source. Assocs :features
  function to context to avoid retaining the head."
  [{dds :downloaded-datasource
    ds :datasource
    :as ctx}]
  (assoc ctx :features (case (:content_type ds)
                         "SHP" #(shp/read-features-from-path (:path dds)))))

(defn- hex [bytes]
  (str/join ""
            (map #(format "%02x" %) bytes)))

(defn- sha256 [bytes]
  (let [d (java.security.MessageDigest/getInstance "SHA-256")]
    (hex (.digest d bytes))))

(defn property-pattern-fn [pattern]
  (fn [{attrs :attributes :as f}]
    (let [all-attrs (merge (dissoc f :attributes)
                           attrs)]
      (string/interpolate pattern attrs
                          {:sha256 (fn [attr-name]
                                     (sha256 (.getBytes (str (all-attrs (keyword attr-name))))))}))))

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
        new-feature-ids (volatile! #{})
        chunk-size 50]
    (println "POSTing to " (str api-url "/feature"))
    (loop [upserted 0
           [chunk & chunks] (partition-all chunk-size
                                    ;; Add :i attribute that can be used as fallback id
                                    (map-indexed
                                     (fn [i feature]
                                       (update feature :attributes
                                               assoc :i i))
                                     (features)))]
      (when (seq chunk)
        (if (zero? (rem upserted (* 40 chunk-size)))
          (println "\n" upserted "features upserted")
          (do
            (print ".")
            (flush)))
        (patient-post
         (str api-url "/feature")
         {:headers (merge
                    (auth-headers ctx)
                    {"Prefer" "resolution=merge-duplicates"
                     "Content-Type" "application/json"})
          :body (cheshire/encode
                 (for [{:keys [geometry attributes] :as f} chunk
                       :let [id (->id f)
                             _ (vswap! new-feature-ids conj id)]]
                   ;; Feature as JSON
                   {:datasource_id datasource-id
                    :id id
                    :label (->label f)
                    :geometry geometry
                    :deleted false
                    :properties attributes}))})
        (recur (+ upserted chunk-size) chunks)))
    (let [deleted-feature-id-set (set/difference @old-features @new-feature-ids)]
      (when (seq deleted-feature-id-set)
        (print "\nMarking " (count deleted-feature-id-set) " features absent from import file as deleted.\n")
        (doseq [chunk (partition-all 50 deleted-feature-id-set)]
          (print ".") (flush)
          (patient-post
           (str api-url "/feature")
           {:headers (merge
                      (auth-headers ctx)
                      {"Prefer" "resolution=merge-duplicates"
                       "Content-Type" "application/json"})
            :body (cheshire/encode
                   (for [deleted-feature-id chunk]
                     {:datasource_id datasource-id
                      :id deleted-feature-id
                      :deleted true}))}))))
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

(defn import-datasource [config datasource-id]
  (-> config
      (assoc :datasource-id datasource-id)
      existing-features-ids
      fetch-datasource-config
      download-datasource
      extract-datasource
      read-features
      upsert-features
      delete-working-files))

(defn -main [& args]
  (let [config (make-config args)]
    (println "Starting import")
    (doseq [datasource-id (:datasources config)]
      (import-datasource config datasource-id))))

;; -- vesi restrictions STILL have duplicate ids
