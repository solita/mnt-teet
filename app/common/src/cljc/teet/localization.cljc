(ns teet.localization
  "Message localization.
  The interface to translate messages is the `tr` function which takes a message
  and possible parameters.

  On the frontend a global `selected-language` atom is used.
  On the backend the `*language*` dynamic binding is used."
  (:require #?(:cljs [reagent.core :as r])
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            #?(:cljs [cljs.reader :as reader])
            #?(:cljs [postgrest-ui.display :as postgrest-display])
            #?(:cljs [alandipert.storage-atom :refer [local-storage]])
            [teet.util.string :as string]
            [teet.log :as log]))

(defn dev-mode? []
  #?(:clj false
     :cljs (when-let [host (-> js/window .-location .-host)]
             (boolean (re-find #"localhost"
                               host)))))

(def supported-languages #{"en" "et"})

(def language-names {"en" "ENG"
                     "et" "ET"})

(defonce loaded-languages (atom {}))

#?(:cljs (defonce selected-language (local-storage (r/atom :et) :selected-language))
   :clj (def ^:dynamic *language* "The current language to use" :et))

(def calendar-locale {:et {:months ["jaan" "veebr" "märts" "apr" "mai" "juuni" "juuli" "aug" "sept" "okt" "nov" "dets"]
                           :days ["E" "T" "K" "N" "R" "L" "P"]
                           :today "Täna"}
                      :en {:months ["jan" "feb" "mar" "apr" "may" "jun" "jul" "aug" "sep" "oct" "nov" "dec"]
                           :days ["mon" "tue" "wed" "thu" "fri" "sat" "sun"]
                           :today "Today"}})

(defn- load-language* [language callback]
  #?(:cljs
     (-> (str "/language/" (name language) ".edn")
         js/fetch
         (.then #(.text %))
         (.then #(reader/read-string %))
         (.then callback))
     :clj
     (-> (str "../frontend/resources/public/language/" (name language) ".edn")
         slurp read-string
         callback)))

(defn load-language!
  "Load the given language translation file, if it has not been loaded yet, and adds the language
  to the `loaded-languages` atom.
  Calls `on-load` callback, when loading is done."
  [language on-load]
  (if-let [translations (get @loaded-languages language)]
    (on-load language translations)
    (load-language*
     language
     (fn [translations]
       (swap! loaded-languages assoc language
              (assoc translations :calendar (calendar-locale language)))
       (on-load language translations)))))

#?(:cljs (defn load-initial-language! [callback]
           (load-language! @selected-language callback)))

(defn translations
  "(Re)loads the given language translation file and returns the translations."
  [language]
  (swap! loaded-languages dissoc language)
  (load-language! language (constantly nil))
  (get @loaded-languages language))



#?(:cljs (defn set-language! [language]
           (load-language! language #(reset! selected-language %1))))


(declare message)
(defmulti evaluate-list (fn [[operator & _] _] operator))

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

    :else
    (str part)))



(defn- message [message-definition parameters]
  (cond
    (string? message-definition)
    (string/interpolate message-definition parameters)

    (list? message-definition)
    (evaluate-list message-definition parameters)

    :else
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
   (tr #?(:cljs @selected-language :clj *language*) message-path {}))
  ([message-path parameters]
   (tr #?(:cljs @selected-language :clj *language*) message-path parameters))
  ([language message-path parameters]
   (let [language (get @loaded-languages language)]
     (assert language (str "Language " language " has not been loaded."))
     (let [msg (message (get-in language message-path) parameters)]
       (if (or (not-empty msg)
               (not (dev-mode?)))
         msg
         (str message-path))))))

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
  "Utility for returning the first found translation path, or a fallback string (last parameter)"
  [& paths-and-fallback]
  (or (some #(let [result (tr %)]
               (println result)
               (when-not (or (str/blank? result)
                             (when (dev-mode?)
                               (= \[ (first result))))
                 result))
            (butlast paths-and-fallback))
      (last paths-and-fallback)))

(defn tr-tree
  ([tree-path]
   (tr-tree #?(:cljs @selected-language :clj *language*) tree-path))
  ([language tree-path]
   (get-in (get @loaded-languages language) tree-path)))

(defn tr-enum [kw-or-map-with-db-ident]
  (let [kw (if (keyword? kw-or-map-with-db-ident)
             kw-or-map-with-db-ident
             (:db/ident kw-or-map-with-db-ident))]
    (tr [:enum kw])))

(let [warn (memoize (fn [msg]
                      (log/warn "UNTRANSLATED MESSAGE: " msg)))]

  (defn tr-fixme
    "Indicate a message that hasn't been translated yet."
    ([msg]
     (warn msg)
     msg)
    ([msg parameters]
     (warn msg)
     (str msg " " (pr-str parameters)))))

;;;;;; Translate database field columns and localized text values


(defn- column-key [column]
  (cond

    ;; Direct column name
    (string? column) column

    ;; Link to a localized text code, like {:table "activity" :select ["name"]}
    ;; return table name
    (and (map? column)
         (= ["name"] (:select column))) (:table column)))

(defn label-for-field [table column]
  (let [column (column-key column)]
    (tr-or [:fields table column]   ;; Specific field name
           [:fields :common column] ;; Common field name
           column)))                ;; Fallback to column name

#?(:cljs (defmethod postgrest-display/label :default [table column]
           (label-for-field table column)))

(defmacro with-language [lang & body]
  `(binding [*language* ~lang]
     (load-language! *language* (fn [_# _#] ~@body))))
