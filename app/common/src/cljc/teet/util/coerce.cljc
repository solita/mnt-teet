(ns teet.util.coerce
  (:require [clojure.string :as str]))


#?(:clj
   (defn ->bigdec [x]
     (if (string? x)
       (when-not (str/blank? x)
         (-> x str/trim (str/replace "," ".") bigdec))
       (bigdec x))))

#?(:clj
   (defn ->long
     "Coerce x to long x may be string or number
     return nil for blank strings"
     [x]
     (if (string? x)
       (when-not (str/blank? x)
         (-> x str/trim Long/parseLong))
       (long x))))
