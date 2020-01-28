(ns teet.thk.thk-export
  "Export project lifecycle and activity data to THK"
  (:require [datomic.client.api :as d]
            [teet.thk.thk-mapping :as thk-mapping]
            [clojure.string :as str]))

(def ^:private date-format (java.text.SimpleDateFormat. "yyyy-MM-dd"))

(defn- dt [get]
  (fn [row]
    (some->> row get
             (.format date-format))))

(defn- all-projects [db]
  (d/q '[:find (pull ?e [*
                         {:thk.project/lifecycles
                          [*
                           {:thk.lifecycle/activities
                            [*]}]}])
         :where [?e :thk.project/id _]]
       db))

(defn read-integration-info [info-str]
  (when info-str
    (binding [*read-eval* false]
      (read-string info-str))))

(defn find-last-tx-of [db id]
  (ffirst
   (d/q '[:find (max ?txi)
          :where
          [?e _ _ ?tx]
          [?tx :db/txInstant ?txi]
          :in $ ?e]
        db id)))

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
                 (case csv-column
                   ;; Fetch TEET update timestamps from tx info
                   "object_teetupdstamp"
                   (thk-mapping/datetime-str (find-last-tx-of db (:db/id project)))
                   "phase_teetupdstamp"
                   (thk-mapping/datetime-str (find-last-tx-of db (:db/id lifecycle)))
                   "activity_teetupdstamp"
                   (thk-mapping/datetime-str (find-last-tx-of db (:db/id activity)))

                   ;; Regular columns
                   (let [[teet-kw _ fmt] (thk-mapping/thk->teet csv-column)
                         fmt (or fmt str)
                         value (get data teet-kw)]
                     (if value
                       (fmt value)
                       ""))))
               thk-mapping/csv-column-names))))))
