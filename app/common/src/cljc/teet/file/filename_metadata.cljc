(ns teet.file.filename-metadata
  "Filename metadata.
  Extract metadata from filenames and generate
  filenames based on metadata.

  Filename naming pattern:
  MA<THK object code>_<activity>_TL_<task>_<task-part>_<document-group>-<sequence#>_<name and suffix>"

  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))


(defn name->description-and-extension [string]
  (let [string (str/trim string)
        ext-pos (str/last-index-of string ".")]
    {:description (subs string 0 ext-pos)
     :extension (subs string (inc ext-pos))}))

(def filename-part-parser
  "Parser functions for filename parts. Functions return a map of parsed values."
  {:prefix-and-object #(when-let [[_ object] (re-matches #"MA(\d+)" %)]
                         {:thk.project/id object})

   :activity (fn [string]
               {:activity string})
   :part (fn [string]
           {:part string})
   :field (fn [string]
            {:field string})
   :task (fn [string]
           {:task string})
   :group-and-sequence (fn [string]
                         (when-not (str/blank? string)
                           (let [[group sequence] (str/split string #"-")]
                             {:document-group group
                              :sequence-number (when sequence
                                                 (#?(:cljs js/parseInt
                                                     :clj Long/parseLong) sequence))})))
   :description-and-extension (fn [filename-parts]
                                (name->description-and-extension
                                 (str/join "_" filename-parts)))})

(defn number-string? [s]
  (and (string? s)
       (re-matches #"^\d+$" s)))

(s/def ::prefix-and-object #(re-matches #"^MA\d+$" %))
(s/def ::field (s/and string? #(= "TL" %)))
(s/def ::group-and-sequence (s/and string? #(re-matches #"^(\d+)(-(\d+))$" %)))
(s/def ::part (s/and string? #(re-matches #"^\d{1,2}$" %)))
(s/def ::description-and-extension (s/and string? #(re-matches #"^.+\.[A-Za-z0-9]+" %)))

(s/def ::filename-parts (s/cat :prefix-and-object ::prefix-and-object
                               :activity string?
                               :field #(= "TL" %)
                               :task string?
                               :part ::part
                               :group-and-sequence (s/? ::group-and-sequence)
                               :description-and-extension (s/* string?)))

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
                           :keys [activity task part
                                  document-group
                                  sequence-number
                                  description extension]}]
  (str/join
   "_"
   (remove nil?
           [(str "MA" object)
            activity
            "TL"
            task
            part
            (when document-group
              (str document-group
                   (when sequence-number
                     (str "-" sequence-number))))
            (str description "." extension)])))



(defn filename-parts->filename [filename-parts]
  (str/join "_"
            (->> ::filename-parts s/describe
                 (drop 1)
                 (partition 2)
                 (keep (comp filename-parts first)))))
