(ns teet.file.filename-metadata
  "Filename metadata.
  Extract metadata from filenames and generate
  filenames based on metadata.

  Filename naming pattern:
  MA<THK object code>_<activity>_TL<-part?>_<task>_<group>_<name and suffix>"

  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))


(def ^{:private true
       :doc "Parser functions for filename parts. Functions return a map of parsed values."}
  filename-part-parser
  {:prefix-and-object #(when-let [[_ object] (re-matches #"MA(\d+)" %)]
                         {:thk.project/id object})

   ;; PENDING: 3.4.2020 these are placeholders until metadata model finalized
   ;; all code values should have mappings to actual codes (add filename mappings
   ;; to the database as attributes of the enum values?)

   :activity (fn [string]
               {:activity string})
   :part (fn [string]
           {:part string})
   :task (fn [string]
           {:task string})
   :group (fn [string]
            {:group
             #?(:clj (Long/parseLong string)
                :cljs (js/parseInt string))})
   :name (fn [strings]
           {:name (str/join "_" strings)})})

(s/def ::prefix-and-object #(re-matches #"^MA\d+$" %))
(s/def ::part (s/and string? #(re-matches #"^TL(-\d+)?$" %)))
(s/def ::group (s/and string? #(re-matches #"^\d+$" %)))

(s/def ::filename-parts (s/cat :prefix-and-object ::prefix-and-object
                               :activity string?
                               :part ::part
                               :task string?
                               :group ::group
                               :name (s/* string?)))

(defn filename->metadata [filename]
  (let [parts (s/conform ::filename-parts (str/split filename #"_"))]
    (if (= ::s/invalid parts)
      (throw (ex-info "Unable to parse filename metadata"
                      {:filename filename}))
      (into {}
            (map (fn [[k v]]
                   ((filename-part-parser k) v)))
            parts))))

(defn metadata->filename [{object :thk.project/id
                           :keys [activity part task group name]}]
  (str/join "_"
            [(str "MA" object)
             activity
             (or part "TL")
             task
             group
             name]))



(defn filename-parts->filename [filename-parts]
  (str/join "_"
            (->> ::filename-parts s/describe
                 (drop 1)
                 (partition 2)
                 (keep (comp filename-parts first)))))
