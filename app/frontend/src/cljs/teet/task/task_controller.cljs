(ns teet.task.task-controller
  (:require [tuck.core :as t]
            [teet.routes :as routes]
            [goog.math.Long]
            [taoensso.timbre :as log]
            [teet.document.document-controller]
            [teet.common.common-controller :as common-controller]))

(defrecord FetchTask [task-id])
(defrecord UploadDocuments [files])
(defrecord UpdateTask [task updated-task]) ; update task info to database
(defrecord UpdateTaskResponse [response])
(defrecord AddCommentToTask [task-id comment])

(defmethod routes/on-navigate-event :task [{{:keys [task]} :params}]
  (->FetchTask task))


(extend-protocol t/Event
  FetchTask
  (process-event [{:keys [task-id]} app]
    (t/fx app
          {:tuck.effect/type :query
           :query :task/fetch-task
           :args {:task-id (goog.math.Long/fromString task-id)}
           :result-path [:task task-id]}))

  UploadDocuments
  (process-event [{:keys [files]} {:keys [params] :as app}]
    (let [{:keys [task project]} params]
      (t/fx app
            {:tuck.effect/type :upload-documents
             :files files
             :task-id (goog.math.Long/fromString task)
             :project-id project
             :app-path [:task task :task/documents]})))

  UpdateTask
  (process-event [{:keys [task updated-task]} app]
    (let [id (:db/id task)
          task-path  [:task (str id)]
          old-task (get-in app task-path)
          new-task (merge task updated-task)]

      (t/fx (assoc-in app task-path new-task)
            {:tuck.effect/type :command!
             :command :workflow/update-task
             :payload (assoc updated-task :db/id id)
             :result-event ->UpdateTaskResponse})))

  UpdateTaskResponse
  (process-event [{response :response} app]
    (log/info "GOT RESPONSE: " response)
    app)

  AddCommentToTask
  (process-event [{:keys [task-id comment]} app]
    (log/info "comment: " comment " to task " task-id)
    (t/fx app
          {:tuck.effect/type :command!
           :command :workflow/comment-task
           :payload {:task-id task-id
                     :comment comment}
           :result-event #(->FetchTask (str task-id))})))

(defn download-document-url [doc]
  (common-controller/query-url :document/download (select-keys doc [:db/id])))

(defn new-status [status-kw]
  {:db/id "new-status"
   :task.status/timestamp (js/Date.)
   :task.status/status {:db/ident status-kw}
   ;; user filled in by server
   ;; comment?
   })
