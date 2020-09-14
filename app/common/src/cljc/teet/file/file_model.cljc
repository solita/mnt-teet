(ns teet.file.file-model
  (:require [clojure.string :as str]
            #?(:cljs [teet.file.file-spec :as file-spec])))

;; "In THK module is allowed to upload file extensions: gif, jpg, jpeg, png, pdf, csv, txt, xlsx, docx, xls, doc, dwg, ppt, pptx.""

(def ^:const upload-max-file-size (* 1024 1024 3000))

;; See confluence page: TEET File format list
(def ^:const upload-allowed-file-types
  #{;; Office files
    "application/msword"
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "application/msexcel"
    "application/vnd.ms-excel"
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    "application/mspowerpoint"
    "application/rtf"
    "application/odf"

    ;; Portable documents
    "application/pdf"
    "application/dwf"

    ;; Design/drawing/GIS files
    "application/dwg"
    "application/dgn"
    "application/dxf"
    "application/shp"
    "application/dbf"
    "application/kml"
    "application/kmz"

    ;; Design model files, Building Information Models
    "application/ifc"
    "application/xml"
    "text/xml"
    "application/bcf"
    "application/rvt"
    "application/skp"
    "application/3dm"

    ;; Speficic data files
    "application/ags"
    "application/gpx"

    ;; Images
    "image/png"
    "image/jpeg"
    "image/tiff"
    "image/ecw"

    ;; Other supporting files
    "application/shx"
    "application/lin"})

(def ^:const upload-file-suffix-type
  {;; Generic office files
   "doc" "application/msword"
   "docx" "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
   "xlsx" "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
   "xls" "application/msexcel"
   "ppt" "application/mspowerpoint"
   "pptx" "application/vnd.openxmlformats-officedocument.presentationml.presentation"
   "rtf" "application/rtf"
   "odf" "application/odf"

   ;; Portable documents
   "pdf" "application/pdf"
   "dwf" "application/dwf"

   ;; Digitally signed containers
   "ddoc" "application/ddoc"
   "bdoc" "application/bdoc"
   "asice" "application/asice"

   ;; Design/drawing/GIS files
   "dwg" "application/dwg"
   "dgn" "application/dgn"
   "dxf" "application/dxf"
   "shp" "application/shp"
   "dbf" "application/dbf"
   "kml" "application/kml"
   "kmz" "application/kmz"

   ;; Design model files, Building Information Models
   "ifc" "application/ifc"
   "xml" "application/xml"
   "bcf" "application/bcf"
   "rvt" "application/rvt"
   "skp" "application/skp"
   "3dm" "application/3dm"

   ;; Speficid data files
   "ags" "application/ags"
   "gpx" "application/gpx"

   ;; Images
   "png" "image/png"
   "jpg" "image/jpeg"
   "jpeg" "image/jpeg"
   "tif" "image/tiff"
   "tiff" "image/tiff"
   "ecw" "image/ecw"

   ;; Other supporting files
   "shx" "application/shx"
   "lin" "application/lin"})

(defn filename->suffix [filename]
  (-> filename (str/split #"\.") last))

(defn type-by-suffix [{:file/keys [name] :as file}]
  (if-let [type-by-suffix (-> filename->suffix upload-file-suffix-type)]
    (assoc file :file/type type-by-suffix)
    file))

(defn validate-file [{:file/keys [type size]}]
  (cond
    (> size upload-max-file-size)
    {:error :file-too-large :max-allowed-size upload-max-file-size}

    (not (upload-allowed-file-types type))
    {:error :file-type-not-allowed :allowed-types upload-allowed-file-types}

    ;; No problems, upload can proceed
    :else
    nil))

#?(:cljs
   (def file-info file-spec/file-info))
