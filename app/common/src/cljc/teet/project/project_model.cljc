(ns teet.project.project-model
  "Common project datamodel metadata.")

(def project-listing-columns
  [:thk.project/id
   :thk.project/name
   :thk.project/road-nr
   :thk.project/start-m
   :thk.project/end-m
   :thk.project/carriageway
   :thk.project/estimated-start-date
   :thk.project/estimated-end-date])

(def project-listing-display-columns
  [:thk.project/id
   :thk.project/name
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
