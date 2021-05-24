(ns teet.file.file-model
  (:require [clojure.string :as str]
            [teet.environment :as environment]
            [teet.util.datomic :as du]
            [teet.file.filename-metadata :as filename-metadata]
            [clojure.set :as set]
            #?(:cljs [teet.file.file-spec :as file-spec])
            [taoensso.timbre :as log]))

;; "In THK module is allowed to upload file extensions: gif, jpg, jpeg, png, pdf, csv, txt, xlsx, docx, xls, doc, dwg, ppt, pptx.""

(def ^:const upload-max-file-size (* 1024 1024 3000))

(def ^:const image-thumbnail-size-threshold (* 1024 250))

(def ^:const allowed-chars-string "0123456789AaBbCcDdEeFfGgHhIiJjKkLlMmNnOoPpQqRrSsTtUuVvWwXxYyZz -_+()")

(def ^:const allowed-chars (set allowed-chars-string))

(def ^:const max-description-length 200)

;; See confluence page: TEET File format list
(defn upload-allowed-file-suffixes []
  (environment/config-value :file :allowed-suffixes))

(defn image-suffixes []
  (environment/config-value :file :image-suffixes))

(defn filename->suffix [filename]
  (-> filename (str/split #"\.") last str/lower-case))

(defn- has-suffix? [allowed-suffixes-fn filename]
  (let [allowed-suffixes (allowed-suffixes-fn)]
    (-> filename filename->suffix allowed-suffixes boolean)))

(def valid-suffix? (partial has-suffix? upload-allowed-file-suffixes))

(defn valid-chars-in-description?
  [description]
  (every? allowed-chars description))

(defn valid-description-length?
  [description]
  (>= max-description-length (count description)))

(defn validate-file
  "Validate allowed file type, legal chars + max length in description and max size. Returns map with error description
  or nil if there are no problems."
  [{:file/keys [size name description]}]
  (let [description (or description (some-> name
                                            filename-metadata/name->description-and-extension
                                            :description))]
    (cond
      (> size upload-max-file-size)
      {:error :file-too-large :max-allowed-size upload-max-file-size}

      (not (valid-chars-in-description? description))
      {:error :invalid-chars-in-description}

      (not (valid-description-length? description))
      {:error :description-too-long}

      (<= size 0)
      {:error :file-empty}

      (not (valid-suffix? name))
      {:error :file-type-not-allowed :allowed-suffixes (upload-allowed-file-suffixes)}

      ;; No problems, upload can proceed
      :else
      nil)))

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

(def file-listing-attributes
  "Attributes to fetch for file listing"
  [:db/id :file/name :meta/deleted? :file/version :file/size :file/id
   :file/status :file/part :file/group-number
   :file/original-name
   {:task/_files [:db/id :activity/_tasks]}
   :file/document-group
   :file/sequence-number
   {:file/previous-version [:db/id]}
   :meta/created-at
   :meta/modified-at
   {:meta/modifier [:user/id :user/family-name :user/given-name]}
   {:meta/creator [:user/id :user/family-name :user/given-name]}])

(defn editable?
  "Check if file edit should be allowed based on status."
  [{status :file/status}]
  (or
    (du/enum= status :file.status/draft)
    (du/enum= status :file.status/returned)))
