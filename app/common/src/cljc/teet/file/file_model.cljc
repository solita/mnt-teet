(ns teet.file.file-model
  (:require [clojure.string :as str]
            [teet.environment :as environment]
            #?(:cljs [teet.file.file-spec :as file-spec])
            #?(:cljs [teet.app-state :as app-state])))

;; "In THK module is allowed to upload file extensions: gif, jpg, jpeg, png, pdf, csv, txt, xlsx, docx, xls, doc, dwg, ppt, pptx.""

(def ^:const upload-max-file-size (* 1024 1024 3000))

(def ^:const image-thumbnail-size-threshold (* 1024 250))

;; See confluence page: TEET File format list
(defn upload-allowed-file-suffixes []
  (environment/config-value :file :allowed-suffixes))

(defn image-suffixes []
  (#?(:clj environment/config-value
      :cljs app-state/config-value) :file :image-suffixes))

(defn filename->suffix [filename]
  (-> filename (str/split #"\.") last str/lower-case))

(defn- has-suffix? [allowed-suffixes-fn filename]
  (let [allowed-suffixes (allowed-suffixes-fn)]
    (-> filename filename->suffix allowed-suffixes boolean)))

(def valid-suffix? (partial has-suffix? upload-allowed-file-suffixes))

(defn validate-file
  "Validate allowed file type and max size. Returns map with error description
  or nil if there are no problems."
  [{:file/keys [size name]}]
  (cond
    (> size upload-max-file-size)
    {:error :file-too-large :max-allowed-size upload-max-file-size}

    (not (valid-suffix? name))
    {:error :file-type-not-allowed :allowed-suffixes (upload-allowed-file-suffixes)}

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

(def image-suffix? (partial has-suffix? image-suffixes))

(defn image? [{:file/keys [name]}]
  (image-suffix? name))


#?(:cljs
   (def file-info file-spec/file-info))
