(ns teet.util.date
  (:require #?@(:cljs ([cljs-time.core :as t])
                :clj ([clojure.string :as str])))
  #?(:clj (:import (java.util Date Calendar))))

(defn date-in-past?
  [date]
  #?(:clj (.before date (Date.))
     :cljs (t/before? date (js/Date.))))

(defn days-until-date
  [date]
  #?(:clj
     (Math/floor (/ (- (.getTime date) (.getTime (Date.))) (* 1000 60 60 24)))
     :cljs
     (js/Math.floor (/ (- (.getTime date) (.getTime (js/Date.))) (* 1000 60 60 24)))))

(defn ->date [year month day]
  #?(:clj (.getTime
           (doto (Calendar/getInstance)
             (.set Calendar/YEAR year)
             (.set Calendar/MONTH (dec month))
             (.set Calendar/DATE day)))
     :cljs (js/Date. year (dec month) day)))
