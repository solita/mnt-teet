(ns teet.task.task-controller
  (:require [tuck.core :as t]
            [goog.math.Long]
            [teet.log :as log]
            [teet.document.document-controller]
            [teet.common.common-controller :as common-controller]))

(defrecord FetchTask [task-id])
(defrecord UploadDocuments [files])
(defrecord UpdateTask [task updated-task]) ; update task info to database
(defrecord UpdateTaskResponse [response])
(defrecord UpdateTaskStatus [status])
(defrecord UpdateTaskForm [form-data])
(defrecord CreateTask [])
(defrecord CreateTaskResult [result])

(defrecord DeleteTask [])
(defrecord DeleteTaskResult [response])

(defrecord OpenEditTask [])
(defrecord CloseEditDialog [])
(defrecord UpdateEditTaskForm [form-data])
(defrecord PostTaskEditForm [])
(defrecord TaskEditSuccess [])
(defrecord MoveDataForEdit [])

(defrecord OpenAddDocumentDialog [])
(defrecord CloseAddDocumentDialog [])


(extend-protocol t/Event
  FetchTask
  (process-event [{:keys [task-id]} app]
    (t/fx app
          {:tuck.effect/type :query
           :query            :project/fetch-task
           :args             {:task-id (goog.math.Long/fromString task-id)}
           :result-path      [:task task-id]}))

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

  OpenEditTask
  (process-event [_ {:keys [params page query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page             page
           :params           params
           :query            (assoc query :edit :task)}))

  DeleteTask
  (process-event [_ {:keys [params] :as app}]
    (t/fx app
          {:tuck.effect/type :command!
           :command :project/task-delete
           :success-message  "Task deleted successfully"    ;;TODO add localization
           :payload {:db/id (goog.math.Long/fromString (:task params))}
           :result-event ->DeleteTaskResult}))

  DeleteTaskResult
  (process-event [_response {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page :project
           :params {:project (:project params)}}))

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
         :command          :project/update-task
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
             :command          :project/update-task
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
             :command          :project/update-task
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
