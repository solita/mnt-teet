(ns teet.meeting.meeting-controller
  (:require goog.math.Long
            [teet.common.common-controller :as common-controller]
            [teet.localization :refer [tr]]
            [tuck.core :as t]
            [teet.util.collection :as cu]
            [teet.snackbar.snackbar-controller :as snackbar-controller]))

(defrecord SubmitMeetingForm [activity-id form-data close-event])
(defrecord CreateMeetingResult [activity-id close-event result])
(defrecord CancelMeeting [activity-id meeting-id close-event])

(defrecord SubmitAgendaForm [meeting form-data close-event])
(defrecord DeletionSuccess [close-event response])
(defrecord DeleteAgendaTopic [agenda-id close-event])

(defrecord SubmitDecisionForm [agenda-eid form-data close-event])
(defrecord DeleteDecision [decision-id close-event])

(defrecord AddParticipant [meeting participant])
(defrecord RemoveParticipant [participant-id])
(defrecord RemoveParticipantResult [participant-id result])
(defrecord SendNotifications [meeting])

(defrecord SubmitReview [meeting-id form-data close-event])

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
             :result-event (if editing?
                             (partial common-controller/->ModalFormResult close-event)
                             (partial ->CreateMeetingResult activity-id close-event))})))

  SubmitReview
  (process-event [{:keys [meeting-id form-data close-event]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :meeting/review
           :payload {:meeting-id meeting-id
                     :form-data (select-keys form-data [:review/comment :review/decision])}
           :success-message "Review submitted"
           :result-event (partial common-controller/->ModalFormResult close-event)}))

  CreateMeetingResult
  (process-event [{activity-id :activity-id
                   result :result
                   close-event :close-event}
                  {:keys [params] :as app}]
    (t/fx app
          (fn [e!]
            (e! (close-event)))
          (fn [e!]
            (e! (common-controller/->Navigate
                  :meeting
                  (merge params
                         {:meeting (get-in result [:tempids "new-meeting"])
                          :activity activity-id})
                  {})))))

  CancelMeeting
  (process-event [{:keys [activity-id meeting-id close-event]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :meeting/cancel
           :payload {:activity-eid (common-controller/->long activity-id)
                     :meeting-id meeting-id}
           :success-message (tr [:notifications :meeting-deleted])
           :result-event (partial ->DeletionSuccess close-event)}))

  DeleteDecision
  (process-event [{:keys [decision-id close-event]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :meeting/delete-decision
           :payload {:decision-id decision-id}
           :success-message (tr [:notifications :decision-deleted])
           :result-event (partial common-controller/->ModalFormResult close-event)}))

  DeletionSuccess
  (process-event [{:keys [close-event _response]} {:keys [params] :as app}]
    (t/fx app
          (fn [e!]
            (e! (close-event)))
          (fn [e!]
            (e! (common-controller/->Navigate :activity-meetings (dissoc params :meeting) {})))))

  SubmitAgendaForm
  (process-event [{:keys [meeting form-data close-event]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :meeting/update-agenda
           :payload {:db/id (:db/id meeting)
                     :meeting/agenda [(cu/without-nils
                                       (merge {:db/id "new-agenda-item"}
                                              form-data))]}
           :result-event (partial common-controller/->ModalFormResult close-event)}))

  DeleteAgendaTopic
  (process-event [{:keys [agenda-id close-event]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :meeting/delete-agenda
           :payload {:agenda-id agenda-id}
           :success-message (tr [:notifications :topic-deleted])
           :result-event (partial common-controller/->ModalFormResult close-event)}))

  RemoveParticipant
  (process-event [{id :participant-id} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :meeting/remove-participation
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
            [:participation.role/participant
             (merge {:db/id "non-teet-user"}
                    (select-keys participant #{:user/given-name :user/family-name :user/email}))]
            [(:participation/role participant)
             (:participation/participant participant)])]
      (t/fx app
            {:tuck.effect/type :command!
             :command :meeting/add-participation
             :payload {:participation/in (:db/id meeting)
                       :participation/participant user
                       :participation/role role}
             :result-event common-controller/->Refresh})))

  SendNotifications
  (process-event [{meeting :meeting} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :meeting/send-notifications
           :payload {:db/id (:db/id meeting)}
           :result-event #(snackbar-controller/->OpenSnackBar (tr [:meeting :notifications-sent])
                                                              :success)}))

  SubmitDecisionForm
  (process-event [{:keys [agenda-eid form-data close-event]} app]
    (let [editing? (boolean (:db/id form-data))]
      (t/fx app
            {:tuck.effect/type :command!
             :command (if editing?
                        :meeting/update-decision
                        :meeting/create-decision)
             :payload {:agenda-eid agenda-eid
                       :form-data form-data}
             :success-message (if editing?
                                (tr [:notifications :decision-updated])
                                (tr [:notifications :decision-created]))
             :result-event (partial common-controller/->ModalFormResult close-event)})))

  )
