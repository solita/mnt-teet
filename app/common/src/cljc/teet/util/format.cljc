(ns teet.util.format
  "Common formatting utilities"
  (:require [clojure.string :as str])
  #?(:clj (:import (java.util Locale)
                   (java.text NumberFormat))))

#?(:clj (def locale (Locale. "et" "EE")))
#?(:clj (def euro-format (NumberFormat/getCurrencyInstance locale)))
#?(:clj (def euro-no-sign-format (doto (NumberFormat/getNumberInstance locale)
                                   (.setMinimumFractionDigits 2)
                                   (.setMaximumFractionDigits 2))))
(defn euro
  "Format given number as EUR with € sign."
  [n]
  #?(:clj (.format euro-format n)
     :cljs (str (.toLocaleString n) "\u00a0€")))

(defn euro-no-sign
  "Format given number as money (2 digits) without currency sign."
  [n]
  #?(:clj (.format euro-no-sign-format n)
     :cljs (.toLocaleString n js/undefined #js {:minimumFractionDigits 2
                                                :maximumFractionDigits 2})))
