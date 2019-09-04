(ns teet.task.task-controller
  (:require [tuck.core :as t]
            [teet.routes :as routes]
            [goog.math.Long]
            [taoensso.timbre :as log]
            [teet.document.document-controller]))

(defrecord FetchTask [task-id])
(defrecord UploadDocuments [files])

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
             :app-path [:task task :task/documents]}))))
