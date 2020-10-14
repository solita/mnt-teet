(ns teet.util.string
  "String utilities"
  (:require [clojure.string :as str]))

(defn- interpolate-get [parameters param-name]
  (str
   (get parameters (keyword param-name)
        (str "[MISSING PARAMETER " param-name "]"))))

(defn interpolate
  "Replace parameter references in curly braces in the input string with values from parameters.
  Example: \"Hello, {name}!\" would replace \"{name}\" with the :name parameter."
  ([string parameters] (interpolate string parameters {}))
  ([string parameters special-fns]
   (str/replace string
                #"\{([^\}]+)\}"
                (fn [[_ param-name]]
                  (if (str/includes? param-name ":")
                    (let [[fn-name & args] (str/split param-name #":")]
                      (if-let [special-fn (get special-fns (keyword fn-name))]
                        (apply special-fn args)
                        (interpolate-get parameters param-name)))
                    (interpolate-get parameters param-name))))))

(defn words
  "Split text into sequence of words."
  [text]
  (re-seq #?(:clj #"[\p{IsLatin}\d]+"
             :cljs #"[^\s\d-_*]+") text))

(defn unique-words
  "Return unique words in input text."
  [text]
  (into #{} (words text)))

(defn contains-words?
  "Check that candidate text contains all the words (or parts of words)
  in search-words string.

  Both are split into words and each element in search-text must be
  a substring of some element in cadidate-text.

  Comparison is case insensitive."
  [candidate-text search-text]
  (let [candidate-words (unique-words (str/lower-case candidate-text))
        search-words (unique-words (str/lower-case search-text))]
    (every? (fn [search-word]
              (or
               ;; Candidate words includes this word fully
               (candidate-words search-word)

               ;; or this word is a substring of some candidate word
               (some (fn [candidate-word]
                       (str/includes? candidate-word search-word))
                     candidate-words)))
            search-words)))

(defn strip-leading-zeroes
  "Strip leading zeroes from numbers in string."
  [string]
  (str/replace string
               #"([^\d])0+(\d+)"
               (fn [[_ before after]]
                 (str before after))))
