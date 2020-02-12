(ns teet.ui.progress
  (:require [teet.theme.theme-colors :as theme-colors]
            [teet.ui.util :as util]))


(defn circle
  [{:keys [radius stroke slices defs] :as _circle}]
  (let [total (reduce + (map first slices))
        _ (.log js/console "total: " total)
        norm-radius (- radius (* stroke 2))
        circumference (* norm-radius Math/PI 2)]
    [:svg {:height (* radius 2) :width (* radius 2)
           :style {:transform "rotate(-90deg)"}}
     (when defs
       [:defs
        (util/with-keys defs)])
     [:circle {:r norm-radius
               :cx radius
               :cy radius
               :fill "none"
               :stroke theme-colors/gray-lighter
               :stroke-dasharray (str circumference " " circumference)
               :stroke-dashoffset 0
               :strokeWidth stroke
               :stroke-width stroke}]
     (util/with-keys
       (loop [i 0
              running-item-count 0
              acc []
              [[item-count slice-stroke :as slice] & slices] slices]
         (if-not slice
           (seq acc)
           (if (zero? item-count)
             (recur (inc i) running-item-count acc slices)
             (let [unfilled (- circumference (* (/ running-item-count total) circumference))
                   angle (* (/ running-item-count total) 360)]
               (.log js/console
                     "running-count" running-item-count ", slice: " (pr-str slice)
                     ", unfilled " unfilled
                     ", angle " angle
                     )
               (recur
                (inc i)
                (+ running-item-count item-count)
                (conj acc
                      [:circle {:r norm-radius
                                :cx radius
                                :cy radius
                                :fill "none"
                                :transform (str "rotate(" angle " " radius " " radius ")")
                                :stroke slice-stroke
                                :stroke-dasharray (str circumference " " circumference)
                                :stroke-dashoffset (- circumference unfilled)
                                :stroke-width stroke}])
                slices))))))]))
