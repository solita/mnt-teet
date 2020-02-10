(ns teet.task.task-controller
  (:require [tuck.core :as t]
            [goog.math.Long]
            [teet.log :as log]
            [teet.localization :refer [tr]]
            [teet.document.document-controller]
            [teet.common.common-controller :as common-controller]))

(defrecord UploadDocuments [files])
(defrecord UpdateTask [task updated-task]) ; update task info to database
(defrecord UpdateTaskResponse [response])
(defrecord UpdateTaskStatus [status])
(defrecord UpdateTaskForm [form-data])
(defrecord CreateTask [])
(defrecord CreateTaskResult [result])

(defrecord DeleteTask [task-id])
(defrecord DeleteTaskResult [response])

(defrecord OpenEditModal [entity])
(defrecord CloseEditDialog [])
(defrecord UpdateEditTaskForm [form-data])
(defrecord PostTaskEditForm [])
(defrecord TaskEditSuccess [])
(defrecord MoveDataForEdit [])

(defrecord OpenAddDocumentDialog [])
(defrecord CloseAddDocumentDialog [])

(extend-protocol t/Event
  CloseEditDialog
  (process-event [_ {:keys [params page query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page page
           :params params
           :query (dissoc query :edit)}))

  MoveDataForEdit
  (process-event [_ app]
    (let [task-data (select-keys (get-in app [:route :activity-task]) [:db/id :task/assignee :task/type :task/description])
          task-with-type (assoc task-data :task/type (get-in task-data [:task/type :db/ident]))]
      (assoc app :edit-task-data task-with-type)))

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
           :payload          {:db/id (goog.math.Long/fromString task-id)}
           :result-event     ->DeleteTaskResult}))

  DeleteTaskResult
  (process-event [_response {:keys [page params query] :as app}]
    (let [activity-id (get-in app [:route :activity-task :activity/_tasks 0 :db/id])
          lifecycle-id (get-in app
                               [:route :activity-task :activity/_tasks 0 :thk.lifecycle/_activities 0 :db/id])]
      (t/fx app
            {:tuck.effect/type :navigate
             :page             :project
             :params           {:project (:project params)}
             :query            {:lifecycle lifecycle-id
                                :activity activity-id}})))

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
  (process-event [{status :status :as event} app]
    (let [{id :db/id} (common-controller/page-state app)]
      (t/fx app
        {:tuck.effect/type :command!
         :command          :task/update
         :payload          {:db/id id
                            :task/status status}
         :success-message  "Task status update successful"
         :result-event     common-controller/->Refresh})))

  UpdateEditTaskForm
  (process-event [{form-data :form-data} app]
    (update-in app [:edit-task-data] merge form-data))

  PostTaskEditForm
  (process-event [_ app]
    (let [task (:edit-task-data app)]
      (t/fx app
            {:tuck.effect/type :command!
             :command          :task/update
             :payload          task
             :success-message "Task edited succesfully"
             :result-event     ->TaskEditSuccess})))

  TaskEditSuccess
  (process-event [_ {:keys [page query params] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page             page
           :query            (dissoc query :edit)
           :params           params}
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
    (update-in app [:route :project :new-task] merge form-data))

  CreateTask
  (process-event [_ app]
    (log/info "create task!")
    (let [activity-id (get-in app [:query :activity])
          task (get-in app [:route :project :new-task])]
      (t/fx app
            {:tuck.effect/type :command!
             :command :project/add-task-to-activity
             :payload {:activity-id (goog.math.Long/fromString activity-id)
                       :task (-> task
                                 (update :task/assignee (fn [{id :user/id}] [:user/id id]))
                                 (merge {:db/id "new-task"}))}
             :result-event ->CreateTaskResult})))

  CreateTaskResult
  (process-event [{result :result} {:keys [page params query] :as app}]
    (log/info "create task result: " result)
    (let [project (get-in app [:params :project])]
      (t/fx (update-in app [:project project] dissoc :new-task)
           {:tuck.effect/type :navigate
            :page page
            :params params
            :query (dissoc query :add)}
           common-controller/refresh-fx))))

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


(def ^:const completed-statuses #{:task.status/completed :task.status/accepted})

(defn completed? [{status :task/status}]
  (boolean (completed-statuses (:db/ident status))))
