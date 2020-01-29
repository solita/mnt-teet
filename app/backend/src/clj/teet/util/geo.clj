(ns teet.util.geo
  "Geometrical calculation utilities"
  (:require [clojure.string :as str]))

(defn distance
  "Calculates the distance between two points."
  [[x1 y1] [x2 y2]]
  (let [dx (- x1 x2)
        dy (- y1 y2)]
    (Math/sqrt (+ (* dx dx) (* dy dy)))))

(defn line-string-length
  "Calculates the total length of the given line string."
  [line-string]
  (reduce + (map (fn [[p1 p2]] (distance p1 p2))
                 (partition 2 1 line-string))))

(defn line-string-to-wkt
  "Convert line string to WKT string.
  For QGIS testing with QuickWKT plugin"
  [line-string]
  (str "LINESTRING ("
       (str/join ", "
                 (map (fn [[y x]]
                        (str x " " y)) line-string))
       ")"))

(defn interpolate-point
  "Calculate a point between p1 and p2 as a fraction of p1 to p2.
  Fraction of zero return p1 and fraction of 1 return p2."
  [[x1 y1 :as _p1] [x2 y2 :as _p2] fraction]
  {:pre [(<= 0 fraction 1)]}
  (let [dx (- x2 x1)
        dy (- y2 y1)]
    [(+ x1 (* fraction dx))
     (+ y1 (* fraction dy))]))
