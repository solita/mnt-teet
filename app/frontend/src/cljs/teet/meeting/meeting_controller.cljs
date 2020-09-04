(ns teet.meeting.meeting-controller
  (:require goog.math.Long
            [teet.common.common-controller :as common-controller]
            [teet.localization :refer [tr]]
            [tuck.core :as t]
            [teet.util.collection :as cu]
            [teet.snackbar.snackbar-controller :as snackbar-controller]))

(defrecord SubmitMeetingForm [activity-id form-data close-event])
(defrecord DeleteMeeting [activity-id meeting-id close-event])
(defrecord MeetingCreationResult [close-event response])
(defrecord UpdateMeetingForm [form-data])

(defrecord SubmitAgendaForm [meeting form-data close-event])
(defrecord AddAgendaResult [close-event response])
(defrecord DeletionSuccess [close-event response])

(defrecord AddParticipant [meeting participant])
(defrecord RemoveParticipant [participant-id])
(defrecord RemoveParticipantResult [participant-id result])

(extend-protocol t/Event
  SubmitMeetingForm
  (process-event [{:keys [activity-id form-data close-event]} app]
    (let [editing? (:db/id form-data)]
      (t/fx app
            {:tuck.effect/type :command!
             :command (if editing?
                        :meeting/update
                        :meeting/create)
             :payload {:activity-eid (common-controller/->long activity-id)
                       :meeting/form-data form-data}
             :success-message (if editing?
                                (tr [:notifications :meeting-updated])
                                (tr [:notifications :meeting-created]))
             :result-event (partial ->MeetingCreationResult close-event)})))

  DeleteMeeting
  (process-event [{:keys [activity-id meeting-id close-event]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :meeting/delete
           :payload {:activity-eid (common-controller/->long activity-id)
                     :meeting-id meeting-id}
           :success-message (tr [:notifications :meeting-deleted])
           :result-event (partial ->DeletionSuccess close-event)}))


  DeletionSuccess
  (process-event [{:keys [close-event _response]} {:keys [params] :as app}]
    (t/fx app
          (fn [e!]
            (e! (close-event)))
          (fn [e!]
            (e! (common-controller/->Navigate :activity-meetings (dissoc params :meeting) {})))))

  MeetingCreationResult
  (process-event [{close-event :close-event} app]
    (t/fx app
          (fn [e!]
            (e! (close-event)))
          common-controller/refresh-fx))

  UpdateMeetingForm
  (process-event [{form-data :form-data} app]
    (update-in app [:route :activity-meetings :meeting-form] merge form-data))

  SubmitAgendaForm
  (process-event [{:keys [meeting form-data close-event]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :meeting/update-agenda
           :payload {:db/id (:db/id meeting)
                     :meeting/agenda [(cu/without-nils
                                       (merge {:db/id "new-agenda-item"}
                                              form-data))]}
           :result-event (partial ->AddAgendaResult close-event)}))

  AddAgendaResult
  (process-event [{:keys [result close-event]} app]
    (t/fx app
          (fn [e!]
            (e! (close-event)))
          common-controller/refresh-fx))

  RemoveParticipant
  (process-event [{id :participant-id} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :meeting/remove-participant
           :payload {:db/id id}
           :result-event (partial ->RemoveParticipantResult id)}))

  RemoveParticipantResult
  (process-event [{:keys [participant-id result]} app]
    (t/fx (snackbar-controller/open-snack-bar
           {:app app
            :message (tr [:meeting :participant-removed])
            :variant :success
            :hide-duration 15000
            :action {:title (tr [:buttons :undo])
                     :event (common-controller/->UndoDelete participant-id nil)}})
          common-controller/refresh-fx))

  AddParticipant
  (process-event [{:keys [meeting participant]} app]
    (let [[role user]
          (if (:non-teet-user? participant)
            [:meeting.participant.role/participant
             (merge {:db/id "non-teet-user"}
                    (select-keys participant #{:user/given-name :user/family-name :user/email}))]
            [(:meeting.participant/role participant)
             (:meeting.participant/user participant)])]
      (t/fx app
            {:tuck.effect/type :command!
             :command :meeting/add-participant
             :payload {:meeting (:db/id meeting)
                       :participant {:meeting.participant/user user
                                     :meeting.participant/role role}}
             :result-event common-controller/->Refresh})))
  )
