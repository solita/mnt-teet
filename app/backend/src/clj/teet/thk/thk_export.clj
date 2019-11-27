(ns teet.thk.thk-export
  "Export project lifecycle and activity data to THK"
  (:require [datomic.client.api :as d]))

(def ^:private columns
  [{:name "project_id" :get :thk.project/id}
   {:name "project_est_start_date" :get :thk.project/estimated-start-date}
   {:name "project_est_end_date" :get :thk.project/estimated-end-date}
   {:name "lifecycle_est_start_date" :get :thk.lifecycle/estimated-start-date}
   {:name "lifecycle_est_end_date" :get :thk.lifecycle/estimated-end-date}
   {:name "activity_name" :get :activity/name}
   {:name "activity_est_start_date" :get :activity/estimated-start-date}
   {:name "activity_est_end_date" :get :activity/estimated-end-date}])

(defn export-thk-projects [connection]
  (let [db (d/db connection)
        ;; PENDING: pull *ACTUAL* start/end dates
        projects
        (d/q '[:find (pull ?e [:thk.project/id
                               :thk.project/estimated-start-date
                               :thk.project/estimated-end-date
                               {:thk.project/lifecycles
                                [:thk.lifecycle/estimated-start-date
                                 :thk.lifecycle/estimated-end-date
                                 {:thk.lifecycle/activities
                                  [:activity/name
                                   :activity/estimated-start-date
                                   :activity/estimated-end-date]}]}])
               :where [?e :thk.project/id _]]
             db)]
    (concat
     [(mapv :name columns)]
     (for [[project] (take 20 projects)
           lifecycle (:thk.project/lifecycles project)
           activity (:thk.lifecycle/activities lifecycle)
           :let [_ (println "lc: " lifecycle)
                 row (merge project lifecycle activity)]]
       (vec
        (for [{get-value :get} columns]
          (get-value row)))))))
