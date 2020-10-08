(ns teet.ifc.parser
  (:require [instaparse.core :as insta]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def grammar (slurp (io/resource "ifc/grammar")))

(def step-header-parser
  "Parser for STEP (ISO 10303-21) text data.
  See: https://en.wikipedia.org/wiki/ISO_10303-21"
  (insta/parser grammar :start :STEP-FILE-HEADER))

(def data-entry-parser (insta/parser grammar :start :DATAENTRY))

(defn parse-header [step-file-content]
  (insta/parse step-header-parser step-file-content :partial true))

(defrecord Ref [id])

(defn parse-arg [[type-tag value :as arg]]
  (case type-tag
    :STRING value
    :UNSET nil
    :REF (->Ref value)
    :INTEGER (Long/parseLong value)
    :FLOAT (Double/parseDouble value)
    :LIST (map parse-arg (rest arg))
    :ENUM (keyword value)))

(defn- parse-entry [[entry-tag [name-tag name] [args-tag & args]]]
  (assert (= entry-tag :ENTRY))
  (assert (= name-tag :NAME))
  (assert (= args-tag :ARGS))
  {:name name
   :args (mapv parse-arg args)})

(defn- parse-data-entry [[dataentry-tag [id-tag id] entry :as de]]
  (assert (= dataentry-tag :DATAENTRY))
  (assert (= id-tag :ID))
  [id (parse-entry entry)])

(defn parse-data
  "Parse DATA section of STEP file, only returning instances where
  the (interesting-class-pred class-name) is truthy.

  This only checks the class name and does not spend time parsing
  objects that are not interesting to caller."
  [step-file-data interesting-class-pred]
  (with-open [in (java.io.BufferedReader.
                  (java.io.StringReader. step-file-data))]
    (into {}
          (doall
           (for [line (rest (drop-while #(not= % "DATA;") (line-seq in)))
                 :let [equal-pos (str/index-of line "=")
                       open-paren-pos (str/index-of line "(")
                       class-name (if (and equal-pos
                                           open-paren-pos)
                                    (subs line equal-pos open-paren-pos)
                                    "UNKNOWN")]
                 :when (interesting-class-pred class-name)]
             (parse-data-entry (insta/parse data-entry-parser line)))))))

(defn read-ifc-property-sets
  "Given an IFC STEP file content as string, return all property
  sets and their properties.

  Returns map of property sets by name. Each property set is a
  map from property name to the value."
  [step-file-content]
  (let [objects (parse-data step-file-content
                            #(str/includes? % "IFCPROPERTY"))]
    (into {}
          (for [[_ {:keys [name args]}] objects
                :when (= name "IFCPROPERTYSET")
                :let [property-set-name (nth args 2)
                      prop-refs (nth args 4)]]
            [property-set-name
             (into {}
                        (map (comp
                              (fn [{args :args}]
                                (let [[key _ val _] args]
                                  [key val]))
                              objects
                              :id))
                   prop-refs)]))))
