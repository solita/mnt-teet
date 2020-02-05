(ns teet.project.project-model
  "Common project datamodel metadata."
  (:require [teet.user.user-model :as user-model]
            #?@(:cljs ([teet.ui.format :as format]
                       [clojure.string :as str]
                       goog.math.Long)
                :clj ([clojure.string :as str]))
            [teet.log :as log]
            [teet.util.datomic :refer [id=]]
            [clojure.spec.alpha :as s]))

;; A valid project id is either the :db/id in datomic or
;; a lookup ref specifying the project id in THK
(s/def ::id
  (s/or :db-id integer?
        :thk-lookup-ref (s/and vector?
                               (s/cat :ref-kw #(= :thk.project/id %)
                                      :ref-id string?))))

(def project-listing-attributes
  [:db/id
   :thk.project/id
   :thk.project/name
   :thk.project/project-name
   :thk.project/road-nr
   :thk.project/start-m
   :thk.project/end-m
   ;; FIXME: Also handle in project listing
   :thk.project/custom-start-m
   :thk.project/custom-end-m
   :thk.project/carriageway
   :thk.project/estimated-start-date
   :thk.project/estimated-end-date
   :thk.project/region-name
   {:thk.project/owner user-model/user-info-attributes}
   {:thk.project/manager user-model/user-info-attributes}])

(def project-info-attributes
  (into project-listing-attributes
        [:thk.project/procurement-nr
         :thk.project/repair-method
         :thk.project/setup-skipped?
         :thk.project/related-restrictions
         :thk.project/related-cadastral-units]))

(def project-listing-display-columns
  [:thk.project/project-name
   :thk.project/road-nr
   :thk.project/region-name
   :thk.project/effective-km-range
   :thk.project/carriageway
   :thk.project/estimated-date-range
   :thk.project/owner-info])

(defmulti get-column (fn [_project column] column))

(defmethod get-column :default [project column]
  (get project column))

(defmethod get-column :thk.project/km-range [{:thk.project/keys [start-m end-m]} _]
  [(/ start-m 1000)
   (/ end-m 1000)])

(defmethod get-column :thk.project/effective-km-range [{:thk.project/keys [start-m end-m custom-start-m custom-end-m]} _]
  [(/ (or custom-start-m start-m) 1000)
   (/ (or custom-end-m end-m) 1000)])

(defmethod get-column :thk.project/estimated-date-range
  [{:thk.project/keys [estimated-start-date estimated-end-date]} _]
  [estimated-start-date estimated-end-date])

(defmethod get-column :thk.project/project-name
  [{:thk.project/keys [project-name name]}]
  (if-not (str/blank? project-name)
    project-name
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

(defn lifecycle-by-id [{lifecycles :thk.project/lifecycles} lifecycle-id]
  (some #(when (id= lifecycle-id (:db/id %)) %) lifecycles))

(defn activity-by-id [{lifecycles :thk.project/lifecycles} activity-id]
  (some
    (fn [{activities :thk.lifecycle/activities}]
      (some #(when (id= activity-id (:db/id %)) %) activities))
    lifecycles))

(defn initialized?
  [project]
  (contains? project :thk.project/owner))

(def project-files-xf
  (comp
   (mapcat :thk.project/lifecycles)
   (mapcat :thk.lifecycle/activities)
   (mapcat :activity/tasks)
   (mapcat :task/documents)
   (mapcat :document/files)))

(defn project-files [project]
  (into [] project-files-xf [project]))

(def lifecycle-order
  {:thk.lifecycle-type/design 1
   :thk.lifecycle-type/construction 2})

(defn users-with-permission
  [permissions]
  (mapv
    (fn [perm]
      (merge
        {:permission/role (:permission/role perm)
         :user            (first (:user/_permissions perm))}
        (select-keys perm [:meta/creator :meta/created-at :db/id])))
    permissions))

(def sort-lifecycles
  (partial sort-by (comp lifecycle-order #(get-in % [:thk.lifecycle/type :db/ident]))))

(def activity-sort-priority-vec
  [:activity.name/pre-design
   :activity.name/preliminary-design
   :activity.name/land-acquisition
   :activity.name/detailed-design
   :activity.name/construction
   :activity.name/other])

(defn- activity-sort-priority [activity]
  (.indexOf activity-sort-priority-vec
            (-> activity :activity/name :db/ident)))

(def sort-activities
  (partial sort-by :activity/estimated-start-date))
