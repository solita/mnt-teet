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
  (str "LINESTRING "
       (if (seq line-string)
         (str "("
              (str/join ", "
                        (map (fn [[x y]]
                               (str x " " y)) line-string))
              ")")
         "EMPTY")))

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
  (and (coll? x)
       (= (count x) 2)
       (every? number? x)))

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


(defn angle
  "Returns angle of line segment in radians."
  [[[x1 y1 :as p1] [x2 y2 :as p2]]]
  (let [dx (- x2 x1)
        dy (- y2 y1)]
    (Math/atan2 dy dx)))

(defn points->line-eq
  "Calculate line equation y=mx+b for line that passes
  between two given points.

  Returns [m b]. Returns nil for vertical lines."
  [[x1 y1] [x2 y2]]
  (when (not= x1 x2)
    (let [m (/ (- y2 y1) (- x2 x1))
          b (- y1 (* m x1))]
      [m b])))

(defn- start-or-end-segment
  "Returns the start or end segment of a line string.
  And the start or end point of that segment."
  [line-string start-or-end]
  (let [line-segments (partition 2 1 line-string)
        [p1 p2 :as segment] (case start-or-end
                              :start (first line-segments)
                              :end (last line-segments))
        point (case start-or-end
                :start p1
                :end p2)]
    [segment point]))

(defn line-string-point-offset
  "Return offset how far the point is from linestring start or end point.
  Measures the offset from the `:start` or `:end` line segment depending
  on `start-or-end` parameter.

  Returns positive number if offset is on the right side of the line and
  negative if the offset is on the left side."
  [line-string [px py :as point] start-or-end]
  (let [[[p1 p2] distance-point] (start-or-end-segment line-string start-or-end)
        d (distance distance-point point)
        [m b] (points->line-eq p1 p2)]
    (* d
       (if (>= m 0)
         (if (> py (+ (* m px) b))
           -1 ; left
           1) ; right

         (if (< py (+ (* m px) b))
           -1
           1)))))

(defn line-string-offset-point
  "Return a point that is given offset meters to the side of the
  `:start` or `:end` line segment. If the given offset is positive
  the offset point is to the right side of the line, otherwise
  the point is to the left side of the line."
  [line-string offset start-or-end]
  (let [[line-seq [px py]] (start-or-end-segment line-string start-or-end)
        angle (angle line-seq)
        point-angle (+ angle
                       (if (pos? offset)
                         (- (/ Math/PI 2))
                         (/ Math/PI 2)))
        offset (Math/abs offset)
        x (+ px (* offset (Math/cos point-angle)))
        y (+ py (* offset (Math/sin point-angle)))]
    [x y]))
