(ns teet.activity.activity-controller
  (:require [tuck.core :as t]
            [teet.log :as log]
            [teet.localization :refer [tr]]
            [teet.common.common-controller :as common-controller]
            goog.math.Long))

(defrecord UpdateActivityForm [form-data])
(defrecord CreateActivity [])
(defrecord CreateActivityResult [result])

(defrecord UpdateEditActivityForm [form-data])
(defrecord SaveEditActivityForm [form-data])
(defrecord SaveEditResult [response])

(extend-protocol t/Event
  UpdateActivityForm
  (process-event [{form-data :form-data} app]
    (update-in app [:project (get-in app [:params :project]) :new-activity]
               merge form-data))

  UpdateEditActivityForm
  (process-event [{form-data :form-data} app]
    (update app :edit-activity-data merge form-data))

  SaveEditActivityForm
  (process-event [_ {:keys [edit-activity-data] :as app}]
    (let [[start end] (:activity/estimated-date-range edit-activity-data)
          payload (-> edit-activity-data
                      (dissoc :activity/estimated-date-range)
                      (assoc :activity/estimated-start-date start)
                      (assoc :activity/estimated-end-date end))]
      (t/fx app
            {:tuck.effect/type :command!
             :command          :project/update-activity
             :payload          payload
             :success-message  (tr [:notifications :activity-updated])
             :result-event     ->SaveEditResult})))

  SaveEditResult
  (process-event [{response :response} {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page             page
           :params           params
           :query            (dissoc query :edit)}
          common-controller/refresh-fx))

  CreateActivity
  (process-event [_ app]
    (let [project-id (get-in app [:params :project])
          lifecycle-id (get-in app [:query :lifecycle])
          [start end] (get-in app [:project project-id :new-activity :activity/estimated-date-range])
          activity (-> (get-in app [:project project-id :new-activity])
                      (dissoc :activity/estimated-date-range)
                      (assoc :activity/estimated-start-date start)
                      (assoc :activity/estimated-end-date end))]
      (t/fx app
            {:tuck.effect/type :command!
             :command          :project/create-activity
             :payload          {:lifecycle-id (goog.math.Long/fromString lifecycle-id)
                                :activity activity}
             :result-event     ->CreateActivityResult})))

  CreateActivityResult
  (process-event [_ {:keys [page params query] :as app}]
    (t/fx (update-in app [:project (:project params)] dissoc :new-activity)
          {:tuck.effect/type :navigate
           :page             page
           :params           params
           :query            (dissoc query :add)}
          common-controller/refresh-fx)))
