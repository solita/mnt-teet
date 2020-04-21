(ns teet.task.task-controller
  (:require [tuck.core :as t]
            [goog.math.Long]
            [teet.log :as log]
            [teet.localization :refer [tr]]
            [teet.file.file-controller]
            [teet.common.common-controller :as common-controller]
            [teet.util.collection :as cu]))

(defrecord UploadDocuments [files])
(defrecord UpdateTask [task updated-task]) ; update task info to database
(defrecord UpdateTaskResponse [response])
(defrecord UpdateTaskStatus [status])
(defrecord UpdateTaskForm [form-data])

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
(defrecord Review [status]) ; review results

(extend-protocol t/Event
  SubmitResults
  (process-event [_ {params :params :as app}]
    (t/fx app
          {:tuck.effect/type :command!
           :command :task/submit
           :payload {:task-id (goog.math.Long/fromString (:task params))}
           :success-message (tr [:task :submit-results-success])
           :result-event common-controller/->Refresh}))

  StartReview
  (process-event [_ {params :params :as app}]
    (t/fx app
          {:tuck.effect/type :command!
           :command :task/start-review
           :payload {:task-id (goog.math.Long/fromString (:task params))}
           :success-message (tr [:task :start-review-success])
           :result-event common-controller/->Refresh}))

  Review
  (process-event [{status :status} {params :params :as app}]
    (t/fx app
          {:tuck.effect/type :command!
           :command :task/review
           :payload {:task-id (goog.math.Long/fromString (:task params))
                     :status status}
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
           :payload          {:db/id (if (string? task-id)
                                       (goog.math.Long/fromString task-id)
                                       task-id)}
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
             :task-id (goog.math.Long/fromString task)
             :project-id project
             :app-path [:task task :task/documents]})))

  UpdateTaskStatus
  (process-event [{status :status} app]
    (let [task-id (get-in app [:params :task])]
      (t/fx app
        {:tuck.effect/type :command!
         :command          :task/update
         :payload          {:db/id (goog.math.Long/fromString task-id)
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
    (let [{id :db/id :as task}
          (-> edit-task-data
              (update :db/id #(or % "new-task"))

              ;; Ensure only task with THK type can be sent
              (update :task/send-to-thk?
                      (fn [send?]
                        (boolean (and send? (get-in edit-task-data [:task/type :thk/task-type])))))

              ;; Take keyword value for task type
              (update :task/type :db/ident)
              cu/without-nils)]
      (t/fx app
            (merge
             {:tuck.effect/type :command!
              :result-event ->SaveTaskSuccess}
             (if (not= "new-task" id)
               {:command :task/update
                :payload task
                :success-message (tr [:notifications :task-updated])}
               {:command :task/create

                :payload {:activity-id (goog.math.Long/fromString (get-in stepper [:dialog :activity-id]))
                          :task task}
                :success-message (tr [:notifications :task-created])})))))

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

  UpdateTaskForm
  (process-event [{form-data :form-data} app]
    (update-in app [:route :project :add-task] merge form-data)))

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
