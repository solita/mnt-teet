(ns teet.file.file-controller
  "Controller for managing document uploads"
  (:require [tuck.core :as t]
            [teet.log :as log]
            [goog.math.Long]
            tuck.effect
            [teet.common.common-controller :as common-controller]
            [teet.localization :refer [tr]]
            [teet.file.file-model :as file-model]))

(defrecord UploadFiles [files task-id on-success progress-increment]) ; Upload files (one at a time) to document
(defrecord UploadFinished []) ; upload completed, can close dialog
(defrecord UploadFileUrlReceived [file-data file document-id url on-success])

(defrecord DeleteFile [file-id])
(defrecord DeleteFileResult [])

(defrecord AddFilesToTask [files]) ;; upload more files to existing document
(defrecord NavigateToFile [file])

(defrecord UpdateFileStatus [file-id status])

(extend-protocol t/Event

  UpdateFileStatus
  (process-event [{:keys [file-id status]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :file/update-status
           :payload {:file-id file-id
                     :status status}
           :result-event common-controller/->Refresh}))

  DeleteFile
  (process-event [{file-id :file-id} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :document/delete-file
           :payload {:file-id file-id}
           :success-message (tr [:document :file-deleted-notification])
           :result-event ->DeleteFileResult}))

  DeleteFileResult
  (process-event [_ {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page page
           :params params
           :query (dissoc query :file)}
          common-controller/refresh-fx))

  AddFilesToTask
  (process-event [{files :files} app]
    (let [task-id (goog.math.Long/fromString (get-in app [:params :task]))]
      (t/fx app
            (fn [e!]
              (e! (map->UploadFiles {:files files
                                     :task-id task-id
                                     :progress-increment (int (/ 100 (count files)))
                                     :on-success ->UploadFinished}))))))

  UploadFiles
  (process-event [{:keys [files task-id on-success progress-increment] :as event} app]
    (if-let [file (first files)]
      ;; More files to upload
      (do
        (log/info "More files to upload. Uploading: " file)
        (t/fx (if progress-increment
                (update-in app [:new-document :in-progress?]
                           + progress-increment)
                app)
              {:tuck.effect/type :command!
               :command :task/upload-file
               :payload {:task-id task-id
                         :file (file-model/file-info file)}
               :result-event (fn [result]
                               (map->UploadFileUrlReceived
                                (merge result
                                       {:file-data file
                                        :on-success (update event :files rest)})))}))
      (do
        (log/info "No more files to upload. Return on-success event: " on-success)
        (t/fx app
              (fn [e!] (e! (on-success)))))))

  UploadFileUrlReceived
  (process-event [{:keys [file file-data document-id url on-success]} app]
    (t/fx (assoc-in app [:task (get-in app [:params :task]) :new-document :progress]
                    [:upload file])
          (fn [e!]
            (-> (js/fetch url #js {:method "PUT" :body file-data})
                (.then (fn [^js/Response resp]
                         (if (.-ok resp)
                           (do
                             (log/info "Upload successfull, calling on-success: " on-success)
                             (e! on-success))
                           ;; FIXME: notify somehow
                           (log/warn "Upload failed: " (.-status resp) (.-statusText resp)))))))))


  UploadFinished
  (process-event [_ {:keys [query page params] :as app}]
    (t/fx (dissoc app :new-document)
          {:tuck.effect/type :navigate
           :page page
           :params params
           :query (dissoc query :add-files :add-document)}
          common-controller/refresh-fx))

  NavigateToFile
  (process-event [{file :file} {params :params :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page :file
           :params (assoc params :file (:db/id file))})))

(defn download-url [file-id]
  (common-controller/query-url :file/download-file {:file-id file-id}))
