(ns teet.thk.thk-export
  "Export project lifecycle and activity data to THK"
  (:require [datomic.client.api :as d]))

(def ^:private date-format (java.text.SimpleDateFormat. "yyyy-MM-dd"))

(defn- dt [get]
  (fn [row]
    (some->> row get
             (.format date-format))))

(def ^:private columns
  ;; PENDING: pull *ACTUAL* start/end dates
  [{:name "project_id" :get :thk.project/id}
   {:name "project_est_start_date" :get (dt :thk.project/estimated-start-date)}
   {:name "project_est_end_date" :get (dt :thk.project/estimated-end-date)}
   {:name "lifecycle_est_start_date" :get (dt :thk.lifecycle/estimated-start-date)}
   {:name "lifecycle_est_end_date" :get (dt :thk.lifecycle/estimated-end-date)}
   {:name "activity_name" :get (comp name :db/ident :activity/name)}
   {:name "activity_est_start_date" :get (dt :activity/estimated-start-date)}
   {:name "activity_est_end_date" :get (dt :activity/estimated-end-date)}])

(defn- all-projects [db]
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
       db))

(defn export-thk-projects [connection]
  (let [db (d/db connection)
        projects (all-projects db)]
    (into
     [(mapv :name columns)]
     (comp
      (map first)
      ;; PENDING: do we need to send back projects without lifecycles?
      (filter #(seq (:thk.project/lifecycles %)))
      (mapcat (fn [{:thk.project/keys [lifecycles id] :as project}]
                (println "PROJECT: " id " HAS " (count lifecycles) "lifecycles")
                (for [{:thk.lifecycle/keys [activities] :as lifecycle} lifecycles
                      activity activities
                      :let [row (merge project lifecycle activity)]]
                  (mapv (fn [{get-value :get}]
                          (get-value row)) columns)))))
     projects)))
