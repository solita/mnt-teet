(ns teet.util.euro
  "Formatting and parsing of euro amounts."
  (:require [clojure.string :as str])
  #?(:clj (:import (java.util Locale)
                   (java.text NumberFormat))))

#?(:clj (def locale (Locale. "et" "EE")))
#?(:clj (def euro-format (NumberFormat/getCurrencyInstance locale)))
#?(:clj (def euro-no-sign-format (doto (NumberFormat/getNumberInstance locale)
                                   (.setMinimumFractionDigits 2)
                                   (.setMaximumFractionDigits 2))))
(defn format
  "Format given number as EUR with € sign."
  [n]
  #?(:clj (.format euro-format n)
     :cljs (str (.toLocaleString n) "\u00a0€")))

(defn format-no-sign
  "Format given number as money (2 digits) without currency sign."
  [n]
  #?(:clj (.format euro-no-sign-format n)
     :cljs (.toLocaleString n js/undefined #js {:minimumFractionDigits 2
                                                :maximumFractionDigits 2})))

(defn parse
  "Parse string as euro number, string can contain whitespace and end with € symbol."
  [s]
  (-> s
      (str/replace #"\h" "")
      (str/replace "€" "")
      (str/replace "," ".")
      #?(:clj bigdec :cljs js/parseFloat)))
