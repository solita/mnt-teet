(ns teet.file.file-model
  (:require [clojure.string :as str]
            #?(:cljs [teet.file.file-spec :as file-spec])))

;; "In THK module is allowed to upload file extensions: gif, jpg, jpeg, png, pdf, csv, txt, xlsx, docx, xls, doc, dwg, ppt, pptx.""

(def ^:const upload-max-file-size (* 1024 1024 3000))

(def ^:const image-thumbnail-size-threshold (* 1024 250))

;; See confluence page: TEET File format list
(def ^:const upload-allowed-file-suffixes
  #{;; Generic office files
    "doc"
    "docx"
    "xlsx"
    "xls"
    "ppt"
    "pptx"
    "rtf"
    "odf"

    ;; Portable documents
    "pdf"
    "dwf"

    ;; Design/drawing/GIS files
    "dwg"
    "dgn"
    "dxf"
    "shp"
    "dbf"
    "kml"
    "kmz"

    ;; Design model files, Building Information Models
    "ifc"
    "xml"
    "bcf"
    "rvt"
    "skp"
    "3dm"

    ;; Speficid data files
    "ags"
    "gpx"

    ;; Images
    "png"
    "jpg"
    "jpeg"
    "tif"
    "tiff"
    "ecw"

    ;; Other supporting files
    "shx"
    "lin"

    ;; Audio files
    "wav"
    "mp3"
    "ogg"
    "aac"

    ;; Video files
    "mov"
    "mp4"
    "m4v"})

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
  (-> filename filename->suffix upload-allowed-file-suffixes boolean))

(defn validate-file
  "Validate allowed file type and max size. Returns map with error description
  or nil if there are no problems."
  [{:file/keys [size name]}]
  (cond
    (> size upload-max-file-size)
    {:error :file-too-large :max-allowed-size upload-max-file-size}

    (not (valid-suffix? name))
    {:error :file-type-not-allowed :allowed-suffixes upload-allowed-file-suffixes}

    ;; No problems, upload can proceed
    :else
    nil))

(defn validate-file-metadata
  "Validate the metadata extracted from filename against a task. Returns map with
  error description or nil if there are no problems."
  [task metadata]
  (when (and (seq metadata)
             (not= (:db/id task) (:task-id metadata)))
    {:error :wrong-task}))

(defn image-suffix? [filename]
  (-> filename filename->suffix image-suffixes boolean))

(defn image? [{:file/keys [name]}]
  (image-suffix? name))


#?(:cljs
   (def file-info file-spec/file-info))
