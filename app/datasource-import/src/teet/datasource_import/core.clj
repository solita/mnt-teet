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
           (java.util.zip ZipEntry ZipInputStream)))

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

(defn read-features [{dds :downloaded-datasource
                      ds :datasource
                      :as ctx}]
  (assoc
   ctx :features
   (case (:content_type ds)
     "SHP" (shp/read-features-from-path (:path dds)))))

(defn property-pattern-fn [pattern]
  (fn [{attrs :attributes}]
    (string/interpolate pattern attrs)))

(defn upsert-features [{:keys [features api-url datasource-id datasource] :as ctx}]
  (let [->label (property-pattern-fn (:label_pattern datasource))
        ->id (property-pattern-fn (:id_pattern datasource))]
    (doseq [features (partition-all 100 features)]
      (print ".") (flush)
      (client/post
       (str api-url "/feature")
       {:headers (merge
                  (auth-headers ctx)
                  {"Prefer" "resolution=merge-duplicates"
                   "Content-Type" "application/json"})
        :body (cheshire/encode
               (for [{:keys [geometry attributes] :as f} features]
                 ;; Feature as JSON
                 {:datasource_id datasource-id
                  :id (->id f)
                  :label (->label f)
                  :geometry geometry
                  :properties attributes}))}))
    ctx))

(defn dump-ctx [ctx]
  (spit "debug-ctx" (pr-str ctx)))

(defn -main [& args]
  (let [[config-file] args]
    (assert (and config-file
                 (.canRead (io/file config-file)))
            "Specify config file to read")
    (let [config (-> config-file slurp read-string)]
      (assert (valid-api? config)
              "Specify :api-url and :api-secret")
      (assert (valid-datasource? config)
              "Specify :datasource-id")
      (-> config
          fetch-datasource-config
          download-datasource
          extract-datasource
          read-features
          upsert-features
          dump-ctx))))
