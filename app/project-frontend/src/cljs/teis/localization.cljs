(ns teis.localization
  "Message localization.
  The interface to translate messages is the `tr` function which takes a message
  and possible parameters.

  On the frontend a global `selected-language` atom is used."
  (:require [reagent.core :as r]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [cljs.reader :as reader]
            [postgrest-ui.display :as postgrest-display]))

(def supported-languages #{"en" "et"})

(defonce loaded-languages (atom {}))

;; FIXME: read language from user cookie
(defonce selected-language (r/atom :et))

(defn load-language!
  "Load the given language translation file, if it has not been loaded yet, and adds the language
  to the `loaded-languages` atom.
  Calls `on-load` callback, when loading is done."
  [language on-load]
  (if-let [translations (get @loaded-languages language)]
    (on-load language translations)
    (-> (str "language/" (name language) ".edn")
        js/fetch
        (.then #(.text %))
        (.then #(reader/read-string %))
        (.then (fn [translations]
                 (swap! loaded-languages assoc language translations)
                 (on-load language translations))))))

(defn load-initial-language! [callback]
  (load-language! @selected-language callback))

(defn translations
  "(Re)loads the given language translation file and returns the translations."
  [language]
  (swap! loaded-languages dissoc language)
  (load-language! language (constantly nil))
  (get @loaded-languages language))



(defn set-language! [language]
  (load-language! language #(reset! selected-language %1)))


(declare message)
(defmulti evaluate-list (fn [[operator & args] parameters] operator))

(defmethod evaluate-list :plural [[_ param-name zero one many] parameters]
  (let [count (get parameters param-name)]
    (cond
      (nil? count) (str "{{missing count parameter " param-name "}}")
      (zero? count) (message zero parameters)
      (= 1 count) (message one parameters)
      :else (message many parameters))))


(defmethod evaluate-list :default [[op & _] _]
  (str "{{unknown translation operation " op "}}"))

(defn- message-part [part parameters]
  (cond
    (keyword? part)
    (message-part (get parameters part) parameters)

    (list? part)
    (evaluate-list part parameters)

    :default
    (str part)))

(defn- message [message-definition parameters]
  (cond
    (string? message-definition)
    message-definition

    (list? message-definition)
    (evaluate-list message-definition parameters)

    :default
    (reduce (fn [acc part]
              (str acc (message-part part parameters)))
            ""
            message-definition)))

(defn tr
  "Returns translation for the given message.
  If `language` is provided, use that language, otherwise use the default.

  `message-path` is a vector of keywords path to the translation map.

  Optional `parameters` give values to replaceable parts in the message."
  ([message-path]
   (tr @selected-language message-path {}))
  ([message-path parameters]
   (tr @selected-language message-path parameters))
  ([language message-path parameters]
   (let [language (get @loaded-languages language)]
     (assert language (str "Language " language " has not been loaded."))
     (message (get-in language message-path) parameters))))

(s/fdef tr-key
        :args (s/cat :path (s/coll-of keyword?))
        :ret fn?)

(defn tr-key
  "Returns a function that translates a keyword under the given `path`.
  This is useful for creating a formatting function for keyword enumerations.
  Multiple paths may be provided and they are tried in the given order. First
  path that has a translation for the requested key, is used."
  [& paths]
  (fn [key]
    (or
      (some #(let [message (tr (conj % key))]
               #_(log/info "path: " (pr-str (conj % key)) " => " message ", blank? " (str/blank? message))
               (when-not (str/blank? message)
                 message))
            paths)
      "")))

(defn tr-or
  "Utility for returning a default when a translation is not found."
  [tr-result default-value]
  (if (= "" tr-result)
    default-value
    tr-result))

(defn tr-tree
  ([tree-path]
   (tr-tree @selected-language tree-path))
  ([language tree-path]
   (get-in (get @loaded-languages language) tree-path)))


;;;;;; Translate database field columns and localized text values


(defn- column-key [column]
  (cond

    ;; Direct column name
    (string? column) column

    ;; Link to a localized text code, like {:table "phase" :select ["name"]}
    ;; return table name
    (and (map? column)
         (= ["name"] (:select column))) (:table column)))

(defn label-for-field [table column]
  (let [column (column-key column)
        v (tr [:fields table column])]
    (if (str/blank? v)
      (let [v (tr [:fields :common column])]
        (if (str/blank? v)
          ;; Fallback to column name
          column

          ;; Use common field name
          v))

      ;; Field name has table specific name
      v)))

(defmethod postgrest-display/label :default [table column]
  (label-for-field table column))

(defn text-for-lang [localized-text used-lang]
  (some (fn [{:strs [lang text]}]
          (when (= lang used-lang)
            text))
        localized-text))

(defn localized-text
  "Show localized text value for the current language"
  ([lt]
   (localized-text lt nil))
  ([lt fallback-lang]
   (or (text-for-lang lt (name @selected-language))
       (text-for-lang lt fallback-lang)
       "")))
