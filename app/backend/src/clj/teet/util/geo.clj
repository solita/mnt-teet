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
                 (map (fn [[x y]]
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

(defn line-string? [x]
  (boolean (seq (rest x))))

(defn point? [x]
  (= (count x) 2))

(defn line-string-interpolate-point
  "Calculate a point in line string between 0 (start point) and 1 (end point)."
  [line-string fraction]
  {:pre [(line-string? line-string)
         (<= 0 fraction 1)]}
  (cond
    (zero? fraction) (first line-string)
    (== 1.0 fraction) (last line-string)
    :else
    (let [length (line-string-length line-string)
          wanted (* fraction length)]
      (reduce
       (fn [[traveled last-point] point]
         (let [dist-to-point (distance last-point point)
               new-traveled (+ traveled dist-to-point)]
           (if (> new-traveled wanted)
             (reduced (interpolate-point last-point point
                                         (/ (- wanted traveled) dist-to-point)))
             [new-traveled point])))
       [0 (first line-string)]
       (rest line-string)))))
