(ns teet.thk.thk-export
  "Export project lifecycle and activity data to THK"
  (:require [datomic.client.api :as d]
            [teet.thk.thk-mapping :as thk-mapping]))

(def ^:private date-format (java.text.SimpleDateFormat. "yyyy-MM-dd"))

(defn- dt [get]
  (fn [row]
    (some->> row get
             (.format date-format))))

(defn- all-projects [db]
  (d/q '[:find (pull ?e [:thk.project/id
                         :thk.project/estimated-start-date
                         :thk.project/estimated-end-date
                         :thk.project/integration-info
                         {:thk.project/lifecycles
                          [:thk.lifecycle/estimated-start-date
                           :thk.lifecycle/estimated-end-date
                           :thk.lifecycle/type
                           :thk.lifecycle/integration-info
                           {:thk.lifecycle/activities
                            [:thk.activity/id
                             :activity/name
                             :activity/estimated-start-date
                             :activity/estimated-end-date
                             :activity/integration-info]}]}])
         :where [?e :thk.project/id _]]
       db))

(defn read-integration-info [info-str]
  (when info-str
    (binding [*read-eval* false]
      (read-string info-str))))

(defn export-thk-projects [connection]
  (let [db (d/db connection)
        projects (map first (all-projects db))]
    (into
     [thk-mapping/csv-column-names]
     (for [project projects
           lifecycle (:thk.project/lifecycles project)
           activity (:thk.lifecycle/activities lifecycle)
           :let [data (merge project lifecycle activity
                             (read-integration-info (:thk.project/integration-info project))
                             (read-integration-info (:thk.lifecycle/integration-info lifecycle))
                             (read-integration-info (:activity/integration-info activity)))]]
       (do
         (def the-data data)
         (mapv (fn [csv-column]
                 (let [[teet-kw] (thk-mapping/thk->teet csv-column)
                       value (get data teet-kw)]
                   (str value)))
               thk-mapping/csv-column-names))))))
