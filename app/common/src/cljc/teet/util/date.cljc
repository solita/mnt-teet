(ns teet.util.date
  (:require #?@(:cljs ([teet.ui.format :as format]
                       [cljs-time.core :as t]
                       goog.math.Long)
                :clj ([clojure.string :as str])))
  #?(:clj (:import (java.util Date))))

(defn date-in-past?
  [date]
  #?(:clj (.before date (Date.))
     :cljs (t/before? date (js/Date.))))

(defn days-until-date
  [date]
  #?(:clj (Math/floor (/ (- (.getTime date) (.getTime (Date.))) (* 1000 60 60 24)))))
