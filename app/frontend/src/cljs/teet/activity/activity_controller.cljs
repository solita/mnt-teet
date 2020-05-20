(ns teet.activity.activity-controller
  (:require [tuck.core :as t]
            [teet.activity.activity-model :as activity-model]
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
(defrecord DeleteActivity [activity-id])
(defrecord DeleteActivityResponse [response])

(defrecord SubmitResults []) ; submit activity for review
(defrecord Review [status]) ; review results

(defn- tasks-for-activity
  "Given `selected-tasks`, of form `[<task-group> <task-type>]`,
  resturns only those whose task group matches the given
  `activity-name`"
  [activity-name selected-tasks sent-tasks]
  (println "sent-tasks" sent-tasks)
  (->> selected-tasks
       (filter (comp (get activity-model/activity-name->task-groups activity-name #{})
                     first))
       (map (fn [selected-task]
              (if (sent-tasks selected-task)
                (conj selected-task true)
                (conj selected-task false))))))

(extend-protocol t/Event
  UpdateActivityForm
  (process-event [{form-data :form-data} app]
    (update app :edit-activity-data merge form-data))

  SaveActivityForm
  (process-event [_ {:keys [edit-activity-data] :as app}]
    (let [new? (nil? (:db/id edit-activity-data))]
      (t/fx app
            {:tuck.effect/type :command!
             ;; create/update
             :command          (if new?
                                 :activity/create
                                 :activity/update)
             :payload          (if new?
                                 {:activity (dissoc edit-activity-data :selected-tasks)
                                  :tasks (tasks-for-activity (:activity/name edit-activity-data)
                                                             (:selected-tasks edit-activity-data)
                                                             (:sent-tasks edit-activity-data))
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
           :command :activity/submit-for-review
           :payload {:activity-id (goog.math.Long/fromString (:activity params))}
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
           :payload {:activity-id (goog.math.Long/fromString (:activity status))
                     :status (:status status)}
           :result-event common-controller/->Refresh})))


(defmethod common-controller/on-server-error :invalid-activity-dates [err app]
  (let [error (-> err ex-data :error)]
    ;; General error handler for when the client sends faulty data.
    ;; Commands can fail requests with :error :bad-request to trigger this
    (t/fx (snackbar-controller/open-snack-bar app (tr [:error error]) :warning))))
