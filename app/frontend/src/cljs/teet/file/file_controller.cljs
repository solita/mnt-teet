(ns teet.file.file-controller
  "Controller for managing document uploads"
  (:require [tuck.core :as t]
            [teet.log :as log]
            [goog.math.Long]
            tuck.effect
            [teet.common.common-controller :as common-controller]
            [teet.localization :refer [tr]]
            [teet.file.file-model :as file-model]
            [clojure.string :as str]
            [teet.snackbar.snackbar-controller :as snackbar-controller]))

(defrecord UploadFiles [files project-id task-id on-success progress-increment file-results]) ; Upload files (one at a time) to document
(defrecord UploadFinished []) ; upload completed, can close dialog
(defrecord UploadFileUrlReceived [file-data file document-id url on-success])
(defrecord UploadNewVersion [file new-version])
(defrecord UploadSuccess [file-id])
(defrecord AfterUploadRefresh [])

(defrecord DeleteFile [file-id])

(defrecord AddFilesToTask [files on-success]) ;; upload more files to existing document
(defrecord NavigateToFile [file])

(defrecord DeleteAttachment [on-success-event file-id])

(defrecord MarkUploadComplete [file-id on-success])
(defrecord MarkUploadCompleteResponse [on-success response])

(defrecord UpdateFileSeen [file-id])
(defrecord UpdateFileSeenResponse [response])

(extend-protocol t/Event

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
  (process-event [{:keys [file-id on-success-event attached-to success-message]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :file/delete-attachment
           :payload {:file-id file-id
                     :attached-to attached-to}
           :result-event (or on-success-event
                             common-controller/->Refresh)
           :success-message success-message}))

  AddFilesToTask
  (process-event [{:keys [files on-success]} app]
    (let [task-id (common-controller/->long (get-in app [:params :task]))]
      (t/fx app
            (fn [e!]
              (e! (map->UploadFiles {:files files
                                     :task-id task-id
                                     :progress-increment (int (/ 100 (count files)))
                                     :on-success on-success}))))))


  UploadSuccess
  (process-event [{file-id :file-id} {:keys [page params query] :as app}]
    (t/fx app
      {:tuck.effect/type :navigate
       :page             page
       :params           (assoc params :file (str file-id))
       :query            query}
      common-controller/refresh-fx))

  AfterUploadRefresh
  (process-event [_ app]
    (t/fx (dissoc app :new-document)
          common-controller/refresh-fx))

  UploadNewVersion
  (process-event [{:keys [file new-version]} {params :params :as app}]
    (t/fx app
          {:tuck.effect/type :command!
           :command :file/upload
           :payload {:task-id (common-controller/->long (get-in app [:params :task]))
                     :file (file-model/file-info (:file-object (first new-version)))
                     :previous-version-id (:db/id file)}
           :result-event (fn [{file :file :as result}]
                           (map->UploadFileUrlReceived
                             (merge result
                                    {:file-data (:file-object (first new-version))
                                     :on-success (->UploadSuccess (:db/id file))})))}))

  UploadFiles
  (process-event [{:keys [files project-id task-id on-success progress-increment
                          attachment? attach-to
                          file-results]
                   :as event} app]
    ;; Validate files
    (if-let [error (some (comp file-model/validate-file
                               file-model/file-info
                               :file-object)
                         files)]
      (snackbar-controller/open-snack-bar
       app
       (case (:error error)
         :file-too-large (tr [:document :file-too-large])
         :file-type-not-allowed (tr [:document :invalid-file-type])
         "Error")
       :error)
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
                 :payload (merge {:file (merge (file-model/file-info (:file-object file))
                                               (when-let [pos (:file/pos-number file)]
                                                 (when (not (str/blank? pos))
                                                   {:file/pos-number (js/parseInt pos)})))}
                                 (if attachment?
                                   {:project-id project-id
                                    :attach-to attach-to}
                                   {:task-id task-id}))
                 :result-event (fn [result]
                                 (map->UploadFileUrlReceived
                                  (merge result
                                         {:file-data (:file-object file)
                                          :on-success (update event :files rest)})))}))
        (do
          (log/info "No more files to upload. Return on-success event: " on-success)
          (t/fx app
                (fn [e!]
                  ;; Invoke on-success and if it returns an event, apply it
                  (when-let [evt (on-success file-results)]
                    (e! evt))))))))

  UploadFileUrlReceived
  (process-event [{:keys [file file-data document-id url on-success]} app]
    (t/fx (assoc-in app [:task (get-in app [:params :task]) :new-document :progress]
                    [:upload file])
          (fn [e!]
            (-> (js/fetch url #js {:method "PUT" :body file-data})
                (.then (fn [^js/Response resp]
                         (if (.-ok resp)
                           (do
                             (log/info "Upload success, marking file as complete: " (:db/id file))
                             (e! (->MarkUploadComplete (:db/id file)
                                                       (update on-success
                                                               :file-results
                                                               (fnil conj []) file))))
                           ;; FIXME: notify somehow
                           (log/warn "Upload failed: " (.-status resp) (.-statusText resp)))))
                (.catch (fn [error]
                          ;; happens on CORS errors and network-level errors
                          (log/error "Upload failed: " error)
                          (log/debug "If the erroris a a 301 status: you need matching region between set region in the environment and the home region of the s3 bucket")))))))


  MarkUploadComplete
  (process-event [{:keys [file-id on-success]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :file/upload-complete
           :payload {:db/id file-id}
           :result-event (partial ->MarkUploadCompleteResponse on-success)}))

  MarkUploadCompleteResponse
  (process-event [{:keys [on-success response]} app]
    (log/info "Upload complete response: " response)
    (t/fx app
          (fn [e!]
            (log/info "File marked as complete, continue with on-success event: " on-success)
            (e! on-success))))

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
           :params (assoc params :file (:db/id file))}))

  UpdateFileSeen
  (process-event [{file-id :file-id} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :file/seen
           :payload {:file-id (common-controller/->long file-id)}
           :result-event ->UpdateFileSeenResponse}))

  UpdateFileSeenResponse
  (process-event [_ app]
    (log/info "File marked as seen.")
    app))

(defn download-url [file-id]
  (common-controller/query-url :file/download-file {:file-id file-id}))
