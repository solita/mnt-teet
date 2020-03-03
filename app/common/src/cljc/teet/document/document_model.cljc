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

(def ^:const upload-file-suffix-type {"ags" "text/ags"
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
