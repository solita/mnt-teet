(ns teet.meeting.meeting-controller
  (:require goog.math.Long
            [teet.common.common-controller :as common-controller]
            [teet.localization :refer [tr]]
            [teet.snackbar.snackbar-controller :as snackbar-controller]
            [teet.task.task-controller :as task-controller]
            [tuck.core :as t]))

(defrecord SubmitMeetingForm [activity-id form-data close-event])
(defrecord MeetingCreationResult [close-event response])
(defrecord UpdateMeetingForm [form-data])

(extend-protocol t/Event
  SubmitMeetingForm
  (process-event [{:keys [activity-id form-data close-event]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :meeting/create
           :payload {:activity-eid (common-controller/->long activity-id)
                     :meeting/form-data form-data}
           :success-message "Meeting created successfully"  ;; TODO ADD TRANSLATIONS
           :result-event (partial ->MeetingCreationResult close-event)}))

  MeetingCreationResult
  (process-event [{response :response
                   close-event :close-event} app]
    (t/fx app
          (fn [e!]
            (e! (close-event)))
          common-controller/refresh-fx))

  UpdateMeetingForm
  (process-event [{form-data :form-data} app]
    (println "form changed: " (pr-str form-data))
    (update-in app [:route :activity-meetings :meeting-form] merge form-data)))


