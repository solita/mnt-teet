(ns teet.road.road-model
  "Common utilities for manipulating road addresses")

(defn km->m [km]
  (long (* km 1000)))

(defn m->km [m]
  (/ m 1000.0))

(defn format-distance [m]
  (str #?(:cljs (.toFixed (m->km m) 3)
          :clj (printf "%.3f" (m->km m)))
       " km"))

(defn parse-km [km]
  #?(:cljs (js/parseFloat km)
     :clj (Double/parseDouble km)))

(def default-road-buffer-meters "200")
