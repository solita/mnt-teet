(ns teet.document.document-model
  (:require [clojure.string :as str]))

;; "In THK module is allowed to upload file extensions: gif, jpg, jpeg, png, pdf, csv, txt, xlsx, docx, xls, doc, dwg, ppt, pptx.""

(def ^:const upload-max-file-size (* 1024 1024 3000))
(def ^:const upload-allowed-file-types #{;; Word
                                         "application/msword"
                                         "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                         ;; Excel
                                         "application/msexcel"
                                         "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet

"
                                         ;; Powerpoint
                                         "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                         "application/mspowerpoint"

                                         ;; PDF
                                         "application/pdf"

                                         ;; images
                                         "image/gif"
                                         "image/jpeg"
                                         "image/png"

                                         ;; video
                                         "video/mp4"
                                         "video/x-ms-wmv"
                                         "video/x-msvideo"

                                         ;; archive
                                         "application/zip"

                                         ;; Domain tools
                                         "text/ags"
                                         "image/vnd.dwg"

                                         ;; text
                                         "text/plain"
                                         "text/csv"})

(def ^:const upload-file-suffix-type
  {"ags" "text/ags"
   "doc" "application/msword"
   "docx" "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
   "xlsx" "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
   "xls" "application/msexcel"
   "ppt" "application/mspowerpoint"
   "pptx" "application/vnd.openxmlformats-officedocument.presentationml.presentation"
   "dwg" "image/vnd.dwg"
   "pdf" "application/pdf"
   "zip" "application/zip"
   "csv" "text/csv"
   "txt" "text/plain"
   "gif" "image/gif"
   "png" "image/png"
   "jpg" "image/jpeg"
   "jpeg" "image/jpeg"})

(def file-type-categories
  [{:category "Generic office files"
    :typess [{:type "Microsoft Word document"
              :suffixes #{"doc" "docx"}}
             {:type "Microsoft Excel spreadsheet"
              :suffixes #{"xls" "xlsx"}}
             {:type "Microsoft PowerPoint presentation"
              :suffixes #{"ppt" "pptx"}}
             {:type "Open document formats"
              :suffixes #{"rtf" "odf"}}]}
   {:category "Portable documents"
    :types [{:type "Adobe Portable Document Format"
             :suffixes #{"pdf"}}
            {:type "Autodesk Web Format, Design Review"
             :suffxies #{"dwf"}}]}
   {:category "Digitally signed containers"
    :types [{:type "Digitally signed containers"
             :suffixes #{"ddoc" "bdoc" "asice"}}]}
   {:category "Design/drawing/GIS"
    :types [{:type "AutoCAD"
             :suffixes #{"dwg"}}
            {:type "Microstation drawing/model"
             :suffixes #{"dgn"}}
            {:type "Autodesk drawing exchange"
             :suffixes #{"dxf"}}
            {:type "ESRI Shapefile"
             :suffixes #{"shp"}}
            {:type "ESRI Database"
             :suffixes #{"dbf"}}
            {:type "Keyhole markup"
             :suffixes #{"kml" "kmz"}}]}
   {:category "Design/building information models"
    :types [{:type "Industry Foundation Classes"
             :suffixes #{"ifc"}}
            {:type "LandXML, Inframodel"
             :suffixes #{"xml"}}
            {:type "BIM Collaboration Format"
             :suffixes #{"bcf"}}
            {:type "Autodesk Revit model"
             :suffixes #{"rvt"}}
            {:type "Sketchup"
             :suffixes #{"skp"}}
            {:type "Rhinocheros"
             :suffixes #{"3dm"}}]}
   {:category "Specific Data files"
    :types [{:type "AGS geotechnical data"
             :suffixes #{"ags"}}
            {:type "Gedetical interchange"
             :suffixes #{"gpx"}}]}
   {:category "Images"
    :types [{:type "Portable Network Graphics"
             :suffixes #{"png"}}
            {:type "JPEG"
             :suffixes #{"jpg" "jpeg"}}
            {:type "Tagged Image File Format"
             :suffixes #{"tif" "tiff"}}
            {:type "Enhanced Wavelet Compression"
             :suffixes #{"ecw"}}]}
   {:category "Supporting files"
    :types [{:type "AutoCAD support files"
             :suffixes #{"shx" "shp" "lin"}}]}])


(defn type-by-suffix [{:file/keys [name type] :as file}]
  (if-not (str/blank? type)
    file
    (if-let [type-by-suffix (-> name (str/split #"\.") last upload-file-suffix-type)]
      (assoc file :file/type type-by-suffix)
      file)))

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
   (defn file-info [^js/File f]
         {:file/name (.-name f)
          :file/size (.-size f)
          :file/type (.-type f)}))
