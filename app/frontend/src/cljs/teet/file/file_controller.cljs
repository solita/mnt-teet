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
            [teet.snackbar.snackbar-controller :as snackbar-controller]
            [teet.file.filename-metadata :as filename-metadata]
            [teet.link.link-controller :as link-controller]))

(defrecord UploadFiles [files project-id task-id on-success progress-increment file-results linked-from]) ; Upload files (one at a time) to document
(defrecord FileUploadError [error])
(defrecord UploadFinished []) ; upload completed, can close dialog
(defrecord UploadFileUrlReceived [file-data file document-id url on-success linked-from])
(defrecord UploadNewVersion [file new-version progress-atom task-id])
(defrecord UploadSuccess [file-id])
(defrecord AfterUploadRefresh [])

(defrecord DeleteFile [file-id])

(defrecord AddFilesToTask [files-form task on-success linked-from])
(defrecord NavigateToFile [file])

(defrecord DeleteAttachment [on-success-event file-id])

(defrecord MarkUploadComplete [file-id on-success linked-from])
(defrecord MarkUploadCompleteResponse [on-success response])

(defrecord UpdateFileSeen [file-id])
(defrecord UpdateFileSeenResponse [response])

(defrecord UpdateFilesForm [new-value])
(defrecord FilesFormMetadataReceived [filename metadata])
(defrecord UploadNewVersionError [progress-atom err])

;; Modify file info
(defrecord ModifyFile [file callback])

(defmethod common-controller/on-server-error :invalid-chars-in-description
  [err app]
  (snackbar-controller/open-snack-bar app
                                      (tr [:error (-> err ex-data :error)] {:characters file-model/allowed-chars-string})
                                      :error))

(defmethod common-controller/on-server-error :description-too-long
  [err app]
  (snackbar-controller/open-snack-bar app
                                      (tr [:error (-> err ex-data :error)] {:limit file-model/max-description-length})
                                      :error))


(defmethod common-controller/on-server-error :file-type-not-allowed
  [err app]
  (t/fx (snackbar-controller/open-snack-bar app
                                            (tr [:error (-> err ex-data :error)])
                                            :error)
    common-controller/refresh-fx))

(extend-protocol t/Event
  DeleteFile
  (process-event [{file-id :file-id} {params :params :as app}]
    (t/fx app
          {:tuck.effect/type :command!
           :command :file/delete
           :payload {:file-id file-id}
           :success-message (tr [:document :file-deleted-notification])
           :result-event (fn [_]
                           (common-controller/->Navigate
                            :activity-task
                            (common-controller/route-params app #{:project :activity :task})
                            {}))}))

  DeleteAttachment
  (process-event [{:keys [file-id on-success-event attached-to success-message]} app]
    ;; attached-to is a keyword-id pair such as [:meeting-decision id]
    (t/fx app
          {:tuck.effect/type :command!
           :command :file/delete-attachment
           :payload {:file-id file-id
                     :attached-to attached-to}
           :result-event (or on-success-event
                             common-controller/->Refresh)
           :success-message success-message}))

  AddFilesToTask
  (process-event [{:keys [files-form task on-success linked-from]} app]
    (let [files (mapv (fn [file]
                        (merge file

                               (if-let [part-number (some-> file :metadata :part js/parseInt)]
                                 ;; Use detected part
                                 {:file/part {:file.part/number part-number}}

                                 ;; Use part selected from pulldown
                                 {:file/part (:file/part files-form)})))
                      (:task/files files-form))
          task-id (:db/id task)]
      (t/fx app
            (fn [e!]
              (e! (map->UploadFiles (merge {:files files
                                            :task-id task-id
                                            :progress-increment (int (/ 100 (count files)))
                                            :on-success on-success}
                                           (when linked-from
                                             {:linked-from linked-from}))))))))


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
    (t/fx (-> app
              (common-controller/update-page-state [] dissoc :files-form)
              (dissoc :new-document))
          common-controller/refresh-fx))

  UploadNewVersion
  (process-event [{:keys [file new-version progress-atom task-id]} app]
    (log/info "STEP: UploadNewVersion file:" file ", new-version: " new-version ", task-id: " task-id)
    (t/fx app
          {:tuck.effect/type :command!
           :command :file/replace
           :payload {:task-id task-id
                     :file (merge (file-model/file-info (:file-object new-version))
                                  (select-keys new-version
                                               [:file/description
                                                :file/extension
                                                :file/document-group
                                                :file/sequence-number
                                                :file/original-name]))
                     :previous-version-id (:db/id file)}
           :error-event (partial ->UploadNewVersionError progress-atom)
           :result-event (fn [result]
                           (map->UploadFileUrlReceived
                             (merge result
                                    {:file-data (:file-object new-version)
                                     :on-success (common-controller/->Refresh)})))}))

  UploadNewVersionError
  (process-event [{:keys [progress-atom err]} app]
    (let [error (-> err ex-data :error)
          tr-option (case error
                      :invalid-chars-in-description {:characters file-model/allowed-chars-string}
                      :description-too-long {:limit file-model/max-description-length}
                      nil)]
      (reset! progress-atom false)
      (t/fx
        (snackbar-controller/open-snack-bar
          (assoc-in app [:new-document :in-progress?] false)
          (if tr-option
            (tr [:error error] tr-option)
            (tr [:error error]))
          :error)
        common-controller/refresh-fx)))

  UploadFiles
  (process-event [{:keys [files project-id task-id on-success progress-increment
                          attachment? user-attachment? attach-to
                          file-results linked-from]
                   :as event} app]
    (log/info "FILES: " files)
    ;; Validate files
    (if-let [file (first files)]
      ;; More files to upload
      (do
        (log/info "More files to upload. Uploading: " file)
        (t/fx (if progress-increment
                (update-in app [:new-document :in-progress?]
                           + progress-increment)
                app)
              {:tuck.effect/type :command!
               :command (cond
                          attachment?
                          :file/upload-attachment

                          (get-in file [:metadata :file-id])
                          :file/replace

                          user-attachment?
                          :file/upload-user-attachment

                          :else
                          :file/upload)
               :payload (merge {:file (merge (file-model/file-info (:file-object file))
                                             (select-keys file [:file/description
                                                                :file/extension
                                                                :file/sequence-number
                                                                :file/document-group
                                                                :file/part]))}
                               (when-let [prev-file-id (get-in file [:metadata :file-id])]
                                 {:previous-version-id prev-file-id})
                               (if user-attachment?
                                 {:employee-id attach-to}
                                (if attachment?
                                 {:project-id project-id
                                    :attach-to attach-to}
                                 {:task-id task-id})))
               :error-event ->FileUploadError
               :result-event (fn [result]
                               (map->UploadFileUrlReceived
                                 (merge result
                                        {:file-data (:file-object file)
                                         :linked-from linked-from
                                         :on-success (update event :files rest)})))}))
      (do
        (log/info "No more files to upload. Return on-success event: " on-success)
        (t/fx app
              (fn [e!]
                ;; Invoke on-success and if it returns an event, apply it
                (when-let [evt (on-success file-results)]
                  (e! evt)))))))

  FileUploadError
  (process-event [{:keys [error]} app]
    (let [error (-> error ex-data :error)
          tr-option (case error
                      :invalid-chars-in-description {:characters file-model/allowed-chars-string}
                      :description-too-long {:limit file-model/max-description-length}
                      nil)]
      (t/fx
        (snackbar-controller/open-snack-bar
          (assoc-in app [:new-document :in-progress?] false)
          (if tr-option
            (tr [:error error] tr-option)
            (tr [:error error]))
          :warning))))

  UploadFileUrlReceived
  (process-event [{:keys [file file-data document-id url on-success linked-from] :as args} app]
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
                                                               (fnil conj [])
                                                               file)
                                                       linked-from)))
                           ;; FIXME: notify somehow
                           (log/warn "Upload failed: " (.-status resp) (.-statusText resp)))))
                (.catch (fn [error]
                          ;; happens on CORS errors and network-level errors
                          (log/error "Upload failed: " error)
                          (log/debug "If the erroris a a 301 status: you need matching region between set region in the environment and the home region of the s3 bucket")))))))


  MarkUploadComplete
  (process-event [{:keys [file-id on-success linked-from]} app]
    (t/fx app
          (when linked-from
            (fn [e!]
              (e! (link-controller/map->AddLink
                    {:from linked-from :to file-id :type :file}))))
          {:tuck.effect/type :command!
           :command :file/upload-complete
           :success-message (tr [:file :upload-success])
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

(defn file-updated
  "Processing to run when a file in upload form was changed.
  Handles dependencies between fields."
  [{:file/keys [document-group] :as file}]
  (as-> file file
    ;; IF document-group is not selected, file can't have seq#
    (if (nil? document-group)
      (dissoc file :file/sequence-number)
      file)))

;; Implement metadata fetching for file upload forms
(extend-protocol t/Event

  ;; When file form changes, initiate metadata fetch for any new
  ;; files
  UpdateFilesForm
  (process-event [{:keys [new-value]} app]
    (let [form (-> (common-controller/page-state app :files-form)
                   (merge new-value)
                   (update :task/files
                           #(mapv file-updated %)))
          files (:task/files form)
          files-to-fetch (filter (complement :metadata)
                                 files)
          new-app (common-controller/update-page-state app [:files-form] (constantly form))]

      (if (seq files-to-fetch)
        (apply t/fx new-app
               (for [{f :file-object} files-to-fetch
                     :let [name (:file/name (file-model/file-info f))]]
                 {:tuck.effect/type :query
                  :query :file/resolve-metadata
                  :args {:file/name name}
                  :result-event (partial ->FilesFormMetadataReceived name)}))
        new-app)))

  ;; When we receive new metadata for the file of a given name
  FilesFormMetadataReceived
  (process-event [{:keys [filename metadata]} app]
    (common-controller/update-page-state
     app
     [:files-form :task/files]
     (fn [files]
       (mapv (fn [{fo :file-object :as file}]
               (if (= (:file/name (file-model/file-info fo))
                      filename)
                 (merge
                  file
                  {:metadata metadata
                   :file/document-group (:document-group-kw metadata)
                   :file/sequence-number (:sequence-number metadata)
                   :file/original-name filename}
                  (if (:description metadata)
                    {:file/description (:description metadata)
                     :file/extension (:extension metadata)}
                    (let [{:keys [description extension]}
                          (filename-metadata/name->description-and-extension (last (str/split filename #"_")))]
                      {:file/description description
                       :file/extension extension})))
                 file))
             files))))

  ModifyFile
  (process-event [{:keys [file callback]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :file/modify
           :payload (update file :file/sequence-number
                            #(cond
                               (or (nil? %) (int? %)) %
                               (str/blank? %) nil
                               :else (js/parseInt %)))
           :result-event #(do
                            (when callback
                              (callback))
                            (common-controller/->Refresh))})))
