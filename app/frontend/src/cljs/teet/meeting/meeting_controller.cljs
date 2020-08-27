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

(defrecord SubmitAgendaForm [meeting form-data close-event])
(defrecord AddAgendaResult [close-event response])

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
    (update-in app [:route :activity-meetings :meeting-form] merge form-data))

  SubmitAgendaForm
  (process-event [{:keys [meeting form-data close-event]} app]
    (println "form data: " form-data)
    (t/fx app
          {:tuck.effect/type :command!
           :command :meeting/update-agenda
           :payload {:db/id (:db/id meeting)
                     :meeting/agenda [(merge {:db/id "new-agenda-item"}
                                             form-data)]}
           :result-event (partial ->AddAgendaResult close-event)}))

  AddAgendaResult
  (process-event [{:keys [result close-event]} app]
    (t/fx app
          (fn [e!]
            (e! (close-event)))
          common-controller/refresh-fx)))
