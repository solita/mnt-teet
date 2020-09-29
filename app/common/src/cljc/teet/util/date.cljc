(ns teet.util.date
  (:require #?@(:cljs ([cljs-time.core :as t])
                :clj ([clojure.string :as str])))
  #?(:clj (:import (java.util Date Calendar)
                   (java.time ZoneId))))

#?(:clj
   (defn to-local-date
     [date]
     (-> date
         .toInstant
         (.atZone (ZoneId/systemDefault))
         .toLocalDate)))

#?(:cljs
   (defn now []
     (js/Date.)))
#?(:clj
   (defn now []
     (java.util.Date.)))



(defn date-after? [a b]
  #?(:clj (.after a b)
     :cljs (> a b)))

(defn date-in-past?
  [date]
  #?(:clj (neg? (.compareTo (to-local-date date) (to-local-date (Date.))))
     :cljs (date-after? (now) date)))

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


(defn start-of-today
  []
  #?(:clj (.getTime (doto (Calendar/getInstance)
                      (.setTime (Date.))
                      (.set Calendar/HOUR_OF_DAY 0)
                      (.set Calendar/MINUTE 0)
                      (.set Calendar/SECOND 0)
                      (.set Calendar/MILLISECOND 0)))
     :cljs (.setHours (js/Date.) 0 0 0 0)))
