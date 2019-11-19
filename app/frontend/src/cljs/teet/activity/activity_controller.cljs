(ns teet.activity.activity-controller
  (:require [tuck.core :as t]
            [teet.log :as log]
            [teet.common.common-controller :as common-controller]
            goog.math.Long))

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
          lifecycle (get-in app [:params :lifecycle])
          [start end] (get-in app [:project project :new-activity :activity/estimated-date-range])
          payload (-> (get-in app [:project project :new-activity])
                      (dissoc :activity/estimated-date-range)
                      (assoc :activity/estimated-start-date start)
                      (assoc :activity/estimated-end-date end))]
      (t/fx (assoc-in app [:project project :create-activity-in-progress?] true)
            {:tuck.effect/type :command!
             :command          :activity/create-activity
             :payload          (merge {:lifecycle-id (goog.math.Long/fromString lifecycle)}
                                      payload) ;;TODO pura date-range
             :result-event     ->CreateActivityResult})))

  CreateActivityResult
  (process-event [_ {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page             page
           :params           params
           :query            (dissoc query :add-activity)}
          common-controller/refresh-fx)))
