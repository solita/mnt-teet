(ns teet.util.coerce
  (:require [clojure.string :as str]))

(defn normalize-number-chars
  "Normalize formatted number to parseable.
  Replaces comma with point and different minus character
  with the regular one.

  If `x` is blank or not a string, returns nil."
  [x]
  (when (string? x)
    (-> x str/trim
        (str/replace #"\s" "")
        (str/replace "," ".")
        (str/replace "−" "-"))))

(defn- to-number
  "Parse number from `x`.

  If `x` is nil or blank string, returns nil.
  If `x` is string, it is normalized and parsed.

  Any other value is (like other number type) is coerced."
  [parse-fn coerce-fn x]
  (if (nil? x)
    nil
    (if (string? x)
      (let [x (normalize-number-chars x)]
        (if (str/blank? x)
          nil
          (parse-fn x)))
      (coerce-fn x))))


(def ->bigdec
  "Coerce x to bigdec.
  x may be string or number
  returns nil for blank strings or nil

  in cljs retusn JS number"
  (partial to-number
           #?(:clj bigdec :cljs #(js/parseFloat %))
           #?(:clj bigdec :cljs #(js/parseFloat (str %)))))

(def ->long
  "Coerce x to long.
  x may be string or number
  return nil for blank strings or nil

  in cljs returns JS number"
  (partial to-number #?(:clj #(Long/parseLong %)
                        :cljs #(js/parseInt %)) long))
