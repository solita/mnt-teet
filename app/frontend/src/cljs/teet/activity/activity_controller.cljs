(ns teet.activity.activity-controller
  (:require [tuck.core :as t]
            [teet.localization :refer [tr]]
            [teet.common.common-controller :as common-controller]
            goog.math.Long
            [teet.snackbar.snackbar-controller :as snackbar-controller]))

(defmethod common-controller/on-server-error :conflicting-activities [err app]
  (let [error (-> err ex-data :error)]
    ;; General error handler for when the client sends faulty data.
    ;; Commands can fail requests with :error :bad-request to trigger this
    (t/fx (snackbar-controller/open-snack-bar app (tr [:error error]) :warning))))

(defrecord UpdateActivityForm [form-data])
(defrecord SaveActivityForm [])
(defrecord SaveActivityResponse [response])
(defrecord DeleteActivity [])
(defrecord DeleteActivityResponse [response])

(extend-protocol t/Event
  UpdateActivityForm
  (process-event [{form-data :form-data} app]
    (update app :edit-activity-data merge form-data))

  SaveActivityForm
  (process-event [_ {:keys [edit-activity-data] :as app}]
    (let [[start end] (:activity/estimated-date-range edit-activity-data)
          payload (-> edit-activity-data
                      (dissoc :activity/estimated-date-range)
                      (assoc :activity/estimated-start-date start)
                      (assoc :activity/estimated-end-date end))]
      (t/fx app
            {:tuck.effect/type :command!
             ;; create/update
             :command          :activity/update
             :payload          payload
             :success-message  (tr [:notifications :activity-updated])
             :result-event     ->SaveActivityResponse})))

  SaveActivityResponse
  (process-event [{response :response} {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page             page
           :params           params
           :query            (dissoc query :edit)}
          common-controller/refresh-fx)))
