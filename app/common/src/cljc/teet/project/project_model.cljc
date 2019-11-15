(ns teet.project.project-model
  "Common project datamodel metadata."
  #?(:cljs (:require [teet.ui.format :as format]
                     [clojure.string :as str])
     :clj (:require [clojure.string :as str])))

(def project-listing-columns
  [:thk.project/id
   :thk.project/name
   :thk.project/road-nr
   :thk.project/start-m
   :thk.project/end-m
   :thk.project/carriageway
   :thk.project/estimated-start-date
   :thk.project/estimated-end-date])

(def project-info-columns
  (into project-listing-columns
        [:db/id
         :thk.project/procurement-nr]))

(def project-listing-display-columns
  [:thk.project/name
   :thk.project/road-nr
   :thk.project/km-range
   :thk.project/carriageway
   :thk.project/estimated-date-range])

(defmulti get-column (fn [_project column] column))

(defmethod get-column :default [project column]
  (get project column))

(defmethod get-column :thk.project/km-range [{:thk.project/keys [start-m end-m]} _]
  [(/ start-m 1000)
   (/ end-m 1000)])

(defmethod get-column :thk.project/estimated-date-range
  [{:thk.project/keys [estimated-start-date estimated-end-date]} _]
  [estimated-start-date estimated-end-date])

#?(:cljs
   (defmulti format-column-value (fn [column _value]
                                   column)))

#?(:cljs
   (defmethod format-column-value :thk.project/km-range [_ [start end]]
     (str (.toFixed start 3)
          " \u2013 "
          (.toFixed end 3))))

#?(:cljs
   (defmethod format-column-value :thk.project/estimated-date-range [_ [start end]]
     (str (format/date start) " \u2013 " (format/date end))))
#?(:cljs
   (defmethod format-column-value :default [_ v] (str v)))

(defn filtered-projects [projects filters]
  (filter (fn [project]
            (every? (fn [[filter-attribute filter-value]]
                      (let [v (get project filter-attribute)]
                        (cond
                          (string? filter-value)
                          (and v (str/includes? (str/lower-case v)
                                                (str/lower-case filter-value)))

                          (number? filter-value)
                          (= v filter-value)

                          :else true)))
                    filters))
          projects))
