(ns teet.ui.progress
  (:require [teet.theme.theme-colors :as theme-colors]))

(defn circle
  [{:keys [radius stroke] :as _circle}
   {:keys [total success fail] :as _data}]
  (let [norm-radius (- radius (* stroke 2))
        circumference (* norm-radius Math/PI 2)
        success-offset (- circumference (* (/ success total) circumference))
        success-angle (* (/ success total) 360)
        failure-offset (- circumference (* (/ fail total) circumference))]
    [:svg {:height (* radius 2) :width (* radius 2)
           :style {:transform "rotate(-90deg)"}}
     [:circle {:r norm-radius
               :cx radius
               :cy radius
               :fill "none"
               :stroke theme-colors/gray100
               :stroke-dasharray (str circumference " " circumference)
               :stroke-dashoffset 0
               :strokeWidth stroke
               :stroke-width stroke}]
     [:circle {:r norm-radius
               :cx radius
               :cy radius
               :fill "none"
               :transform (str "rotate(" success-angle " " radius " " radius ")")
               :stroke "red"
               :stroke-dasharray (str circumference " " circumference)
               :stroke-dashoffset failure-offset
               :strokeWidth stroke
               :stroke-width stroke}]
     [:circle {:r norm-radius
               :cx radius
               :cy radius
               :fill "none"
               :stroke theme-colors/success
               :stroke-dasharray (str circumference " " circumference)
               :stroke-dashoffset success-offset
               :strokeWidth stroke
               :stroke-width stroke}]]))
