(ns teet.activity.activity-controller
  (:require [tuck.core :as t]
            [teet.localization :refer [tr]]
            [teet.common.common-controller :as common-controller]
            goog.math.Long
            [teet.snackbar.snackbar-controller :as snackbar-controller]
            [teet.log :as log]))

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

(defrecord SubmitResults []) ; submit activity for review
(defrecord Review [status]) ; review results


(extend-protocol t/Event
  UpdateActivityForm
  (process-event [{form-data :form-data} app]
    (update app :edit-activity-data merge form-data))

  SaveActivityForm
  (process-event [_ {:keys [edit-activity-data] :as app}]
    (let [new? (nil? (:db/id edit-activity-data))]
      (log/info "Save activity: " edit-activity-data)
      (t/fx app
            {:tuck.effect/type :command!
             ;; create/update
             :command          (if new?
                                 :activity/create
                                 :activity/update)
             :payload          (if new?
                                 {:activity (dissoc edit-activity-data :selected-tasks)
                                  :tasks (:selected-tasks edit-activity-data)
                                  :lifecycle-id (get-in app [:stepper :lifecycle])}
                                 {:activity edit-activity-data})
             :success-message  (tr [:notifications (if new? :activity-create :activity-updated)])
             :result-event     ->SaveActivityResponse})))

  SaveActivityResponse
  (process-event [{response :response} {:keys [page params query] :as app}]
    (t/fx (-> app
              (update :stepper dissoc :dialog))
          common-controller/refresh-fx))

  SubmitResults
  (process-event [_ {params :params :as app}]
    (t/fx app
          {:tuck.effect/type :command!
           :command :activity/submit
           :payload {:activity-id (goog.math.Long/fromString (:activity params))}
           :success-message (tr [:activity :submit-results-success])
           :result-event common-controller/->Refresh}))
  
  Review
  (process-event [{status :status} {params :params :as app}]
    (t/fx app
          {:tuck.effect/type :command!
           :command :activity/review
           :payload {:activity-id (goog.math.Long/fromString (:activity params))
                     :status status}
           :result-event common-controller/->Refresh})))
