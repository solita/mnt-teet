(ns teet.activity.activity-controller
  (:require [tuck.core :as t]
            [teet.log :as log]
            [teet.common.common-controller :as common-controller]))

(defrecord UpdateActivityForm [form-data])
(defrecord CreateActivity [])
(defrecord CreateActivityResult [result])


(extend-protocol t/Event
  UpdateActivityForm
  (process-event [{form-data :form-data} app]
    (update-in app [:project (get-in app [:params :project]) :new-activity]
               merge form-data))

  CreateActivity
  (process-event [_ app]
    (let [project (get-in app [:params :project])
          [start end] (get-in app [:project project :new-activity :activity/estimated-date-range])
          payload (-> (get-in app [:project project :new-activity])
                      (dissoc :activity/estimated-date-range)
                      (assoc :activity/estimated-start-date start)
                      (assoc :activity/estimated-end-date end))]
      (t/fx (assoc-in app [:project project :create-activity-in-progress?] true)
            {:tuck.effect/type :command!
             :command :activity/create-activity
             :payload (merge {:db/id "new-activity"
                              :thk/id project}
                             payload) ;;TODO pura date-range
             :result-event ->CreateActivityResult})))

  CreateActivityResult
  (process-event [{result :result} app]
    (let [project (get-in app [:params :project])]
      (log/info "CREATE ACTIVITY RESULT: " result)
      (t/fx
       (-> app
           (update-in [:project project] dissoc :new-activity)
           (assoc-in [:project project :create-activity-in-progress?] false))
       {:tuck.effect/type :navigate
        :page :project
        :params {:project (get-in app [:params :project])}
        :query {}}
       common-controller/refresh-fx))))
