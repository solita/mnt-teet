(ns teet.task.task-controller
  (:require [goog.math.Long]
            [tuck.core :as t]
            [teet.activity.activity-model :as activity-model]
            [teet.common.common-controller :as common-controller]
            [teet.file.file-controller]
            [teet.localization :refer [tr]]
            [teet.log :as log]
            [teet.snackbar.snackbar-controller :as snackbar-controller]
            [teet.util.collection :as cu]
            [teet.project.task-model :as task-model]))

(defn tasks-for-activity-name
  "Given `selected-tasks` of form `[<task-group> <task-type>]`, and
  `sent-tasks` of same form, returns only those whose task group
  matches the given `activity-name`, in form `[<task-group>
  <task-type> <task-sent?>]` where `<task-sent?>` is true, if the task
  was present in `sent-tasks`, `false` otherwise."
  [activity-name selected-tasks sent-tasks]
  (->> selected-tasks
       (filter (comp (get activity-model/activity-name->task-groups activity-name #{})
                     first))
       (map (fn [selected-task]
              (if (and sent-tasks (sent-tasks selected-task)) ;; sent-tasks can be null when none of the send to thk has been touched
                (conj selected-task true)
                (conj selected-task false))))))

(defrecord UploadDocuments [files])
(defrecord UpdateTask [task updated-task]) ; update task info to database
(defrecord UpdateTaskResponse [response])
(defrecord UpdateTaskStatus [status])

(defrecord DeleteTask [task-id])
(defrecord DeleteTaskResult [response])

(defrecord OpenEditModal [entity])
(defrecord UpdateEditTaskForm [form-data])
(defrecord CancelTaskEdit [])
(defrecord SaveTaskForm [])
(defrecord SaveTaskSuccess [])

(defrecord OpenAddDocumentDialog [])
(defrecord CloseAddDocumentDialog [])

(defrecord SubmitResults []) ; submit task for review
(defrecord StartReview []) ; change status to in review
(defrecord Review [result]) ; review results

;; Add multiple tasks
(defrecord OpenAddTasksDialog [activity-id])
(defrecord UpdateAddTasksForm [form-data])
(defrecord SaveAddTasksForm [activity-name])
(defrecord SaveAddTasksResponse [response])
(defrecord CloseAddTasksDialog [])
(defrecord SavePartForm [close-event task-id form-data])
(defrecord DeleteFilePart [close-event part-id])


(extend-protocol t/Event
  SubmitResults
  (process-event [_ {params :params :as app}]
    (t/fx app
          {:tuck.effect/type :command!
           :command :task/submit
           :payload {:task-id (common-controller/->long (:task params))}
           :success-message (tr [:task :submit-results-success])
           :result-event common-controller/->Refresh}))

  SavePartForm
  (process-event [{task-id :task-id
                   form-data :form-data
                   close-event :close-event} app]
    (let [editing? (boolean (:db/id form-data))]
      (t/fx app
            {:tuck.effect/type :command!
             :command (if editing?
                        :task/edit-part
                        :task/create-part)
             :success-message (if editing?
                                (tr [:task :part-edited-succesfully])
                                (tr [:task :part-added-succesfully]))
             :payload {:task-id task-id
                       :part-id (:db/id form-data)
                       :part-name (:file.part/name form-data)}
             :result-event (partial common-controller/->ModalFormResult close-event)})))

  DeleteFilePart
  (process-event [{close-event :close-event
                   part-id :part-id} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :task/delete-part
           :payload {:part-id part-id}
           :success-message (tr [:task :part-deleted-successfully])
           :result-event (partial common-controller/->ModalFormResult close-event)}))

  StartReview
  (process-event [_ {params :params :as app}]
    (t/fx app
          {:tuck.effect/type :command!
           :command :task/start-review
           :payload {:task-id (common-controller/->long (:task params))}
           :success-message (tr [:task :start-review-success])
           :result-event common-controller/->Refresh}))

  Review
  (process-event [{result :result} {params :params :as app}]
    (t/fx app
          {:tuck.effect/type :command!
           :command :task/review
           :payload {:task-id (common-controller/->long (:task params))
                     :result result}
           :result-event common-controller/->Refresh}))

  OpenEditModal
  (process-event [{entity :entity} {:keys [params page query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page             page
           :params           params
           :query            (assoc query :edit entity)}))

  DeleteTask
  (process-event [{task-id :task-id} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command          :task/delete
           :success-message  (tr [:notifications :task-deleted])
           :payload          {:db/id (common-controller/->long task-id)}
           :result-event     ->DeleteTaskResult}))

  DeleteTaskResult
  (process-event [_response {:keys [page params query] :as app}]
    (t/fx (-> app
              (dissoc :edit-task-data)
              (update :stepper dissoc :dialog))
          {:tuck.effect/type :navigate
           :page             :activity
           :params           (select-keys params [:project :activity])}))

  OpenAddDocumentDialog
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :navigate
           :page :activity-task
           :params (:params app)
           :query {:add-document 1}}))

  CloseAddDocumentDialog
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :navigate
           :page :activity-task
           :params (:params app)
           :query {}}))

  UploadDocuments
  (process-event [{:keys [files]} {:keys [params] :as app}]
    (let [{:keys [task project]} params]
      (t/fx app
            {:tuck.effect/type :upload-documents
             :files files
             :task-id (common-controller/->long task)
             :project-id project
             :app-path [:task task :task/documents]})))

  UpdateTaskStatus
  (process-event [{status :status} app]
    (let [task-id (get-in app [:params :task])]
      (t/fx app
        {:tuck.effect/type :command!
         :command          :task/update
         :payload          {:db/id (common-controller/->long task-id)
                            :task/status status}
         :success-message (tr [:notifications :task-status-updated])
         :result-event     common-controller/->Refresh})))

  UpdateEditTaskForm
  (process-event [{form-data :form-data} app]
    (update-in app [:edit-task-data] merge form-data))

  CancelTaskEdit
  (process-event [_ {:keys [page params query] :as app}]
    (t/fx (-> app
              (dissoc :edit-task-data)
              (update :stepper dissoc :dialog))
          {:tuck.effect/type :navigate
           :page             page
           :params           params
           :query            (dissoc query :modal :add :edit :activity :lifecycle)}))

  SaveTaskForm
  (process-event [_ {edit-task-data :edit-task-data
                     stepper :stepper :as app}]
    (t/fx app
          (merge
           {:tuck.effect/type :command!
            :result-event ->SaveTaskSuccess}
           {:command :task/update
            :payload (select-keys edit-task-data
                                  task-model/edit-form-keys)
            :success-message (tr [:notifications :task-updated])})))

  SaveTaskSuccess
  (process-event [_ {:keys [page query params] :as app}]
    (t/fx (-> app
              (dissoc :edit-task-data)
              (update :stepper dissoc :dialog))
          common-controller/refresh-fx))

  UpdateTask
  (process-event [{:keys [task updated-task]} app]
    (let [id (:db/id task)
          task-path  [:task (str id)]
          new-task (merge task updated-task)]

      (t/fx (assoc-in app task-path new-task)
            {:tuck.effect/type :command!
             :command          :task/update
             :payload          (assoc updated-task :db/id id)
             :result-event     ->UpdateTaskResponse})))

  UpdateTaskResponse
  (process-event [{response :response} app]
    (log/info "GOT RESPONSE: " response)
    app)

  OpenAddTasksDialog
  (process-event [{activity-id :activity-id} {:keys [page params query] :as app}]
    (-> app
        (assoc-in [:stepper :dialog]
                  {:type :add-tasks})
        (assoc :add-tasks-data {:db/id activity-id})))

  UpdateAddTasksForm
  (process-event [{form-data :form-data} app]
    (update app :add-tasks-data merge form-data))

  SaveAddTasksForm
  (process-event [{activity-name :activity-name} {:keys [route add-tasks-data] :as app}]
    (let [tasks-to-create (tasks-for-activity-name activity-name
                                                   (:activity/tasks-to-add add-tasks-data)
                                                   (:sent-tasks add-tasks-data))]
      (t/fx (assoc-in app [:add-tasks-data :in-progress?] true)
            {:tuck.effect/type :command!
             :command          :activity/add-tasks
             :payload          {:db/id (:db/id add-tasks-data)
                                :activity/tasks-to-add tasks-to-create
                                :task/estimated-start-date (:task/estimated-start-date add-tasks-data)
                                :task/estimated-end-date (:task/estimated-end-date add-tasks-data)}
             :success-message  (tr [:notifications :tasks-created])
             :result-event     ->SaveAddTasksResponse})))

  SaveAddTasksResponse
  (process-event [{response :response} {:keys [page params query] :as app}]
    (t/fx (-> app
              (update :stepper dissoc :dialog)
              (dissoc :add-tasks-data))
          common-controller/refresh-fx))

  CloseAddTasksDialog
  (process-event [_ {:keys [page params query] :as app}]
    (t/fx (-> app
              (update :stepper dissoc :dialog)
              (dissoc :add-tasks-data))
          {:tuck.effect/type :navigate
           :page             page
           :params           params
           :query            (dissoc query :modal :add :edit :activity :lifecycle)})))

(defmethod common-controller/on-server-error :invalid-task-dates [err app]
  (let [error (-> err ex-data :error)]
    ;; General error handler for when the client sends faulty data.
    ;; Commands can fail requests with :error :bad-request to trigger this
    (t/fx (snackbar-controller/open-snack-bar app (tr [:error error]) :warning))))

(defn document-page-url [{{:keys [project activity task]} :params} doc]
  (str "#/projects/" project "/" activity "/" task "/" (:db/id doc)))

(defn download-document-url [doc]
  (common-controller/query-url :document/download (select-keys doc [:db/id])))

(defn new-status [status-kw]
  {:db/id "new-status"
   :task.status/timestamp (js/Date.)
   :task.status/status {:db/ident status-kw}
   ;; user filled in by server
   ;; comment?
   })
