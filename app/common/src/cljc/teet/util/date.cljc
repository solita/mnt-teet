(ns teet.util.date
  (:require #?@(:cljs ([cljs-time.core :as t])
                :clj ([clojure.string :as str])))
  #?(:clj (:import (java.util Date Calendar)
                   (java.time ZoneId))))




#?(:clj
   (def default-localdate-timezone (ZoneId/of "Europe/Tallinn")))
#?(:clj
   (defn to-local-date
     [date]
     (-> date
         .toInstant
         (.atZone default-localdate-timezone)
         .toLocalDate)))
#?(:cljs
   (defn now []
     (js/Date.)))

#?(:clj
   (defn now []
     (java.util.Date.)))



(defn date-after?
  "compare dates as timestamps (NOT calendar days)"
  [a b]
  #?(:clj (.after a b)
     :cljs (> a b)))


;; date-in-past? rename to date-before-today? [x]
;; - and fix cljs version to work in day-based way [x]
;; - date-in-past? rename to in-past? [x]
;; - find out which timezone aws env / ion is in [x]
;; - fix LocalDate vs timezone problem (Ion is in UTC) [x]
;; - ensure dev env uses same default timezone as ion [ ]
;; - tests [x]
;; - ensure the original use case is fixed (meeting invitation start time) [x]


(defn date-before-today?
  "Converts parameter to a calendar day (lopping off time of day) and returns true if it's previous day or earlier compared to today, false if it's today or a day after today. Works according to configured local timezone of the JVM environment so yields different results in local dev env vs AWS usually."
  [date]
  #?(:clj (neg? (.compareTo (to-local-date date) (to-local-date (Date.))))
     ;; XX cljs ver untested so disabled for now - getDay is specified as local timezone already so should work
     :cljs (let [to-local-date (fn [d]
                                 [(.getFullYear d) (.getMonth d) (.getDay d)])]
             (neg? (compare (to-local-date date) (to-local-date (now)))))))


(defn in-past?  
  [date]
  #?(:clj (date-after? (now) date)
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

(defn test-past []
  (let [y2k-date (->date 2000 12 12)
        futu-date (->date 2080 1 1)]
    (assert (in-past? y2k-date))
    (assert (in-past? (start-of-today)))
    (assert (not (in-past? futu-date)))
    (assert (not (date-before-today? (now))))
    #?(:clj
       (assert (date-before-today? (Date. (- (.getTime (now)) (* 24 60 60 1000)))))
       :cljs
       (let [t (now)]
         (.setDate t (- (.getDate (now)) 1))
         (assert (date-before-today? t))))))

