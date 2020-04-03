(ns teet.file.filename-metadata
  "Filename metadata.
  Extract metadata from filenames and generate
  filenames based on metadata.

  Filename naming pattern:
  <MA|MNT><THK object code>_<phase>_<activity>_<task>_<zone?>_<group>_<name and suffix>"

  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))


(def ^{:private true
       :doc "Parser functions for filename parts. Functions return a map of parsed values."}
  filename-part-parser
  {:prefix-and-object #(when-let [[_ pfx object] (re-matches #"(MA|MNT)(\d+)" %)]
                         {:prefix pfx
                          :thk.project/id object})

   ;; PENDING: 3.4.2020 these are placeholders until metadata model finalized
   ;; all code values should have mappings to actual codes (add filename mappings
   ;; to the database as attributes of the enum values?)

   :phase (fn [string]
            {:thk.lifecycle/type string})
   :activity (fn [string]
               {:activity/name string})
   :task (fn [string]
           {:task/type string})
   :zone (fn [string]
           {:file/zone string})
   :group (fn [string]
            {:file/group
             #?(:clj (Long/parseLong string)
                :cljs (js/parseInt string))})
   :name (fn [strings]
           {:file/name (str/join "_" strings)})})

(s/def ::zone (s/and string? #(str/starts-with? % "Z")))
(s/def ::group (s/and string? #(re-matches #"^\d+$" %)))

(s/def ::filename-parts (s/cat :prefix-and-object string?
                               :phase string?
                               :activity string?
                               :task string?
                               :zone (s/? ::zone)
                               :group ::group
                               :name (s/* string?)))

(defn filename->metadata [filename]
  (into {}
        (map (fn [[k v]]
               ((filename-part-parser k) v)))
        (s/conform ::filename-parts (str/split filename #"_"))))

(def test-filename "MA33333_P_ES_5TL_100_report.pdf")
(def test-filename-with-zone "MA12345_P_EP_5TL_Z01_400_my_fine_drawing.dwg")

(defn filename-parts->filename [filename-parts]
  (str/join "_"
            (->> ::filename-parts s/describe
                 (drop 1)
                 (partition 2)
                 (keep (comp filename-parts first)))))
