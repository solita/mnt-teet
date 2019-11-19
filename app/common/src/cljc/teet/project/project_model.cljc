(ns teet.project.project-model
  "Common project datamodel metadata."
  (:require [teet.user.user-model :as user-model]
            #?@(:cljs ([teet.ui.format :as format]
                       [clojure.string :as str])
                :clj ([clojure.string :as str]))))

(def project-listing-attributes
  [:db/id
   :thk.project/id
   :thk.project/name
   :thk.project/custom-name
   :thk.project/road-nr
   :thk.project/start-m
   :thk.project/end-m
   :thk.project/carriageway
   :thk.project/estimated-start-date
   :thk.project/estimated-end-date
   {:thk.project/owner user-model/user-listing-attributes}])

(def project-info-attributes
  (into project-listing-attributes
        [:thk.project/procurement-nr
         :thk.project/owner]))

(def project-listing-display-columns
  [:thk.project/project-name
   :thk.project/road-nr
   :thk.project/km-range
   :thk.project/carriageway
   :thk.project/estimated-date-range
   :thk.project/owner-info])

(defmulti get-column (fn [_project column] column))

(defmethod get-column :default [project column]
  (get project column))

(defmethod get-column :thk.project/km-range [{:thk.project/keys [start-m end-m]} _]
  [(/ start-m 1000)
   (/ end-m 1000)])

(defmethod get-column :thk.project/estimated-date-range
  [{:thk.project/keys [estimated-start-date estimated-end-date]} _]
  [estimated-start-date estimated-end-date])

(defmethod get-column :thk.project/project-name
  [{:thk.project/keys [custom-name name]}]
  (if-not (str/blank? custom-name)
    custom-name
    name))

(defmethod get-column :thk.project/owner-info [project]
  (some-> project :thk.project/owner user-model/user-name))




(defn filtered-projects [projects filters]
  (filter (fn [project]
            (every? (fn [[filter-attribute filter-value]]
                      (let [v (get-column project filter-attribute)]
                        (cond
                          (string? filter-value)
                          (and v (str/includes? (str/lower-case v)
                                                (str/lower-case filter-value)))

                          (number? filter-value)
                          (= v filter-value)

                          :else true)))
                    filters))
          projects))
