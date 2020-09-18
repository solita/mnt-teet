(ns teet.file.file-model
  (:require [clojure.string :as str]
            #?(:cljs [teet.file.file-spec :as file-spec])))

;; "In THK module is allowed to upload file extensions: gif, jpg, jpeg, png, pdf, csv, txt, xlsx, docx, xls, doc, dwg, ppt, pptx.""

(def ^:const upload-max-file-size (* 1024 1024 3000))

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
    "lin"})

(def ^:const image-suffixes
  #{"png"
    "jpg"
    "jpeg"
    "tif"
    "tiff"
    "ecw"})

(defn filename->suffix [filename]
  (-> filename (str/split #"\.") last))

(defn valid-suffix? [filename]
  (-> filename filename->suffix upload-allowed-file-suffixes boolean))

(defn validate-file [{:file/keys [size name]}]
  (cond
    (> size upload-max-file-size)
    {:error :file-too-large :max-allowed-size upload-max-file-size}

    (not (valid-suffix? name))
    {:error :file-type-not-allowed :allowed-suffixes upload-allowed-file-suffixes}

    ;; No problems, upload can proceed
    :else
    nil))

(defn image-suffix? [filename]
  (-> filename filename->suffix image-suffixes boolean))

(defn image? [{:file/keys [name]}]
  (image-suffix? name))


#?(:cljs
   (def file-info file-spec/file-info))
