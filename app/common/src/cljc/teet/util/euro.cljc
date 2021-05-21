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

(def ^:private euro-pattern #"^-?\d+(\.\d{0,2})?")

(defn parse
  "Parse string as euro number, string can contain whitespace and end with € symbol."
  [s]
  (let [euro-string (-> s
                        (str/replace #"\h" "")
                        (str/replace #"\s" "")
                        (str/replace "€" "")
                        (str/replace "," ".")
                        (str/replace "−" "-"))]
    (if (re-matches euro-pattern euro-string)
      (#?(:clj bigdec :cljs js/parseFloat) euro-string)
      ;; The behavior difference below mirrors that of `bigdec` vs `js/parseFloat`
      #?(:clj (throw (NumberFormatException. (str "not a valid euro value: " s)))
         :cljs nil))))

#?(:clj (def transit-type-handlers
          {java.math.BigDecimal format-no-sign}))
