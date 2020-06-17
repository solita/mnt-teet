(ns teet.localization
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [dk.ative.docjure.spreadsheet :as sheet]))

;;
;; Helpers for handling edn <-> Excel
;;

(defn- list-key-paths
  ([lang-msgs] (list-key-paths [] lang-msgs))
  ([prefix lang-msgs]
   (mapcat (fn [[key val]]
             (if (map? val)
               (list-key-paths (conj prefix key) val)
               [[(conj prefix key) val]]))
           lang-msgs)))

(defn- lang-resource-path [lang]
  (str "resources/public/language/" lang ".edn"))

(defn get-translation-file [lang]
  (let [file-path (lang-resource-path lang)]
    (try
      (with-open [r (io/reader file-path)]
       (edn/read (java.io.PushbackReader. r)))
     (catch java.io.IOException e
       (println (str "Couldn't open '" file-path "': " (.getMessage e))))
     (catch RuntimeException e
       (println (str "Error parsing edn file '" file-path "': " (.getMessage e)))))))

(defn merge-translation-maps [translation-maps]
  (apply merge-with
         vector
         (map (comp (partial into {})
                    list-key-paths)
              translation-maps)))

(defn get-localizations-from-sheet [sheet-path]
  (->> (sheet/load-workbook sheet-path)
       (sheet/select-sheet "Localizations")
       (sheet/select-columns {:A :key
                              :B :et
                              :C :en})))

(defn localization-edn-for-language [localizations lang]
  (reduce (fn [acc localization-entry]
              (try (assoc-in acc
                             (edn/read-string (:key localization-entry))
                             (lang localization-entry))
                   (catch Exception e
                     (println "error reading line " (pr-str localization-entry))
                     (throw e))))
          ;; Order keys alphabetically
          ;; TODO nested order, not only main level
          (sorted-map)
          ;; Drop header row
          (rest localizations)))

(defn pretty-print [x]
  (with-out-str (pp/pprint x)))

(defn localization-edns [localizations]
  {:en (pretty-print (localization-edn-for-language localizations :en))
   :et (pretty-print (localization-edn-for-language localizations :et))})

(defn write-localization-edn! [localization-edn-str lang]
  (spit (str "../frontend/resources/public/language/" (name lang) ".edn")
        localization-edn-str))

(defn write-localization-edns! [localization-edns-map]
  (doseq [[lang localization-edn-str] localization-edns-map]
    (write-localization-edn! localization-edn-str lang)))

(defn write-localization-edns-from-sheet! [sheet-path]
  (-> (get-localizations-from-sheet sheet-path)
      localization-edns
      write-localization-edns!))

(defn -main [& [sheet-path]]
  (if sheet-path
    (do (write-localization-edns-from-sheet! sheet-path)
        (println "Localization edn files successfully written!"))
    (println "Please provide localization sheet path as argument.")))
