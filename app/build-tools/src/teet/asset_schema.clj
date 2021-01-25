(ns teet.asset-schema
  (:require [dk.ative.docjure.spreadsheet :as sheet]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]))

(defn generate-asset-schema [sheet-file])

(defn -main [& [sheet-path]]
  (let [sheet-file (some-> sheet-path io/file)]
    (if (some-> sheet-file .canRead)
      (generate-asset-schema sheet-file)
      (println "Specify location of TEET_ROTL.xlsx as argument."))))
