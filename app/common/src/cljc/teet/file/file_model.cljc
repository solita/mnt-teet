(ns teet.file.file-model
  (:require [clojure.string :as str]
            [teet.log :as log]
            #?(:cljs [teet.file.file-spec :as file-spec])))

(def ^:const upload-max-file-size (* 1024 1024 3000))

(def ^:const image-thumbnail-size-threshold (* 1024 250))


(defonce upload-allowed-file-suffixes (atom nil))

(defn apply-attachment-policy! [ssm-params-description]
  (let [ap-prefix "/teet/attachment-policy/suffix/"
        ap-suffix "/allowed"]
    (reset! upload-allowed-file-suffixes
            (into #{}
                  (for [p-info ssm-params-description
                        :let [name (:Name p-info)]
                        :when (and
                               (str/starts-with? name ap-prefix)
                               (str/ends-with? name ap-suffix))]   
                    (-> name
                        (str/replace-first ap-suffix "")
                        (str/replace-first ap-prefix "")))))))

(def ^:const image-suffixes
  #{"png"
    "jpg"
    "jpeg"
    "tif"
    "tiff"
    "ecw"})

(defn filename->suffix [filename]
  (-> filename (str/split #"\.") last str/lower-case))

(defn valid-suffix? [filename]
  (if-let [whitelist @upload-allowed-file-suffixes]
    (-> filename filename->suffix whitelist boolean)
    ;; else
    (do
      (log/debug "valid-suffix? called before whitelist initialized")
      false)))

(defn validate-file
  "Validate allowed file type and max size. Returns map with error description
  or nil if there are no problems."
  [{:file/keys [size name]}]
  (cond
    (> size upload-max-file-size)
    {:error :file-too-large :max-allowed-size upload-max-file-size}

    (not (valid-suffix? name))
    {:error :file-type-not-allowed :allowed-suffixes @upload-allowed-file-suffixes}

    ;; No problems, upload can proceed
    :else
    nil))

(defn validate-file-metadata
  "Validate the metadata extracted from filename against project and task.
  Returns map with error description or nil if there are no problems."
  [project-id task metadata]
  (when (seq metadata)
    (cond
      (not= [:thk.project/id project-id] (:project-eid metadata))
      {:error :wrong-project}

      (not= (:db/id task) (:task-id metadata))
      {:error :wrong-task})))

(defn image-suffix? [filename]
  (-> filename filename->suffix image-suffixes boolean))

(defn image? [{:file/keys [name]}]
  (image-suffix? name))


#?(:cljs
   (def file-info file-spec/file-info))
