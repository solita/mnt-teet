(ns teet.activity.activity-controller
  (:require goog.math.Long
            [teet.common.common-controller :as common-controller]
            [teet.localization :refer [tr]]
            [teet.snackbar.snackbar-controller :as snackbar-controller]
            [teet.task.task-controller :as task-controller]
            [tuck.core :as t]))

(defmethod common-controller/on-server-error :conflicting-activities [err app]
  (let [error (-> err ex-data :error)]
    ;; General error handler for when the client sends faulty data.
    ;; Commands can fail requests with :error :bad-request to trigger this
    (t/fx (snackbar-controller/open-snack-bar app (tr [:error error]) :warning))))

(defrecord UpdateActivityForm [form-data])
(defrecord SaveActivityForm [])
(defrecord SaveActivityResponse [response])
(defrecord DeleteActivity [activity-id])
(defrecord DeleteActivityResponse [response])

(defrecord SubmitResults []) ; submit activity for review
(defrecord Review [status]) ; review results

(extend-protocol t/Event
  UpdateActivityForm
  (process-event [{form-data :form-data} app]
    (update app :edit-activity-data merge form-data))

  SaveActivityForm
  (process-event [_ {:keys [edit-activity-data] :as app}]
    (let [new? (nil? (:db/id edit-activity-data))
          tasks-to-create (task-controller/tasks-for-activity-name  (:activity/name edit-activity-data)
                                                                    (:selected-tasks edit-activity-data)
                                                                    (:sent-tasks edit-activity-data))]
      (t/fx app
            {:tuck.effect/type :command!
             ;; create/update
             :command          (if new?
                                 :activity/create
                                 :activity/update)
             :payload          (if new?
                                 {:activity (dissoc edit-activity-data :selected-tasks)
                                  :tasks tasks-to-create
                                  :lifecycle-id (get-in app [:stepper :lifecycle])}
                                 {:activity edit-activity-data})
             :success-message  (tr [:notifications (if new? :activity-created :activity-updated)])
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
           :command :activity/submit-for-review
           :payload {:activity-id (common-controller/->long (:activity params))}
           :success-message (tr [:activity :submit-results-success])
           :result-event common-controller/->Refresh}))

  DeleteActivityResponse
  (process-event
    [_ {:keys [params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page :project
           :params (dissoc params :activity)
           :query query}
          common-controller/refresh-fx))

  DeleteActivity
  (process-event
    [{activity-id :activity-id} app]
    (println "IN DELETE:" activity-id)
    (t/fx (update-in app [:stepper] dissoc :dialog)
          {:tuck.effect/type :command!
           :command :activity/delete
           :payload {:db/id activity-id}
           :success-message (tr [:notifications :activity-deleted])
           :result-event ->DeleteActivityResponse}))

  Review
  (process-event [{status :status} {params :params :as app}]
    (assert (:activity status))
    (assert status)
    (t/fx app
          {:tuck.effect/type :command!
           :command :activity/review
           :payload {:activity-id (common-controller/->long (:activity status))
                     :status (:status status)}
           :result-event common-controller/->Refresh})))


(defmethod common-controller/on-server-error :invalid-activity-dates [err app]
  (let [error (-> err ex-data :error)]
    ;; General error handler for when the client sends faulty data.
    ;; Commands can fail requests with :error :bad-request to trigger this
    (t/fx (snackbar-controller/open-snack-bar app (tr [:error error]) :warning))))
