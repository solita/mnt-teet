(ns teet.file.file-controller
  "Controller for managing document uploads"
  (:require [tuck.core :as t]
            [teet.log :as log]
            [goog.math.Long]
            tuck.effect
            [teet.common.common-controller :as common-controller]
            [teet.localization :refer [tr]]
            [teet.file.file-model :as file-model]
            [teet.transit :as transit]))

(defrecord UploadFiles [files task-id on-success progress-increment file-results]) ; Upload files (one at a time) to document
(defrecord UploadFinished []) ; upload completed, can close dialog
(defrecord UploadFileUrlReceived [file-data file document-id url on-success])
(defrecord UploadNewVersion [file new-version])
(defrecord UploadSuccess [file-id])

(defrecord DeleteFile [file-id])
(defrecord DeleteFileResult [])

(defrecord AddFilesToTask [files]) ;; upload more files to existing document
(defrecord NavigateToFile [file])

(defrecord UpdateFileStatus [file-id status])

(defrecord DeleteAttachment [on-success-event file-id])

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
  (process-event [{file-id :file-id} {params :params :as app}]
    (t/fx app
          {:tuck.effect/type :command!
           :command :file/delete
           :payload {:file-id file-id}
           :success-message (tr [:document :file-deleted-notification])
           :result-event (fn [_]
                           (log/info "FILE TUHOTTU!")
                           (common-controller/->Navigate :activity-task (dissoc params :file) {}))}))

  DeleteAttachment
  (process-event [{:keys [file-id on-success-event]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :file/delete-attachment
           :payload {:file-id file-id}
           :result-event on-success-event}))

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


  UploadSuccess
  (process-event [{file-id :file-id} {:keys [page params query] :as app}]
    (t/fx app
      {:tuck.effect/type :navigate
       :page             page
       :params           (assoc params :file (str file-id))
       :query            query}
      common-controller/refresh-fx))

  UploadNewVersion
  (process-event [{:keys [file new-version]} {params :params :as app}]
    (t/fx app
          {:tuck.effect/type :command!
           :command :file/upload
           :payload {:task-id (goog.math.Long/fromString (get-in app [:params :task]))
                     :file (file-model/file-info (first new-version))
                     :previous-version-id (:db/id file)}
           :result-event (fn [{file :file :as result}]
                           (map->UploadFileUrlReceived
                             (merge result
                                    {:file-data (first new-version)
                                     :on-success (->UploadSuccess (:db/id file))})))}))

  UploadFiles
  (process-event [{:keys [files task-id on-success progress-increment attachment?
                          file-results]
                   :as event} app]
    (if-let [file (first files)]
      ;; More files to upload
      (do
        (log/info "More files to upload. Uploading: " file)
        (t/fx (if progress-increment
                (update-in app [:new-document :in-progress?]
                           + progress-increment)
                app)
              {:tuck.effect/type :command!
               :command (if attachment? :file/upload-attachment :file/upload)
               :payload (merge {:file (file-model/file-info file)}
                               (when-not attachment?
                                 {:task-id task-id}))
               :result-event (fn [result]
                               (map->UploadFileUrlReceived
                                (merge result
                                       {:file-data file
                                        :on-success (update event :files rest)})))}))
      (do
        (log/info "No more files to upload. Return on-success event: " on-success)
        (t/fx app
              (fn [e!] (e! (on-success file-results)))))))

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
                             (e! (update on-success
                                         :file-results
                                         (fnil conj []) file)))
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
