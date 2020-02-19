(ns teet.document.document-model
  (:require [clojure.string :as str]))

(def ^:const upload-max-file-size (* 1024 1024 100))
(def ^:const upload-allowed-file-types #{"image/png"
                                         "image/jpeg"
                                         "application/pdf"
                                         "application/zip"
                                         "text/ags"
                                         "application/vnd.openxmlformats-officedocument.wordprocessingml.document"})

(def ^:const upload-file-suffix-type {"ags" "text/ags"})

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
