(ns teet.document.document-controller
  "Controller for managing document uploads"
  (:require [tuck.core :as t]
            [tuck.effect]
            [teet.log :as log]
            [goog.math.Long]
            tuck.effect
            [teet.common.common-controller :as common-controller]
            [teet.project.task-model :as task-model]
            [teet.localization :refer [tr]]))

(defrecord CreateDocument []) ; create empty document and link it to task
(defrecord CancelDocument []) ; cancel document creation
(defrecord UpdateDocumentForm [form-data])
(defrecord UpdateDocumentStatus [status])
(defrecord UploadFiles [files document-id on-success progress-increment]) ; Upload files (one at a time) to document
(defrecord UploadFinished []) ; upload completed, can close dialog
(defrecord UploadFileUrlReceived [file-data file document-id url on-success])
(defrecord FetchDocument [document-id]) ; fetch document
(defrecord UploadFilesToDocument [files]) ; upload new files to existing document from the document page

(defrecord UpdateNewCommentForm [form-data]) ; update new comment form data
(defrecord Comment []) ; save new comment to document
(defrecord UpdateFileNewCommentForm [form-data]) ; update new comment on selected file
(defrecord CommentOnFile []) ; save new comment to file

(defrecord DeleteDocument [document-id])
(defrecord DeleteDocumentResult [])
(defrecord DeleteFile [file-id])
(defrecord DeleteFileResult [])

(defrecord AddFilesToDocument [files]) ;; upload more files to existing document

(defn- file-info [^js/File f]
  {:file/name (.-name f)
   :file/size (.-size f)
   :file/type (.-type f)})

(extend-protocol t/Event

  FetchDocument
  (process-event [{document-id :document-id} app]
    (log/info "fetch doc " document-id)
    (t/fx app
          {:tuck.effect/type :query
           :query :document/fetch-document
           :args {:document-id (goog.math.Long/fromString document-id)}
           :result-path [:document document-id]}))

  UpdateNewCommentForm
  (process-event [{form-data :form-data} {:keys [query] :as app}]
    (update-in app
               [:route :activity-task :task/documents]
               (fn [documents]
                 (mapv #(if (= (str (:db/id %)) (:document query))
                          (assoc % :new-comment form-data)
                          %)
                       documents))))

  Comment
  (process-event [_ app]
    (let [doc (get-in app [:query :document])
          new-comment (->> (get-in app [:route :activity-task :task/documents])
                           (filter #(= (str (:db/id %)) doc))
                           first
                           :new-comment
                           :comment/comment)]
      (t/fx app
            {:tuck.effect/type :command!
             :command :document/comment
             :payload {:document-id (goog.math.Long/fromString doc)
                       :comment new-comment}
             :result-event common-controller/->Refresh})))

  UpdateFileNewCommentForm
  (process-event [{form-data :form-data} {:keys [query] :as app}]
    (let [file-id (:file query)]
      (update-in app
                 [:route :activity-task]
                 (fn [task]
                   (update-in task (conj (task-model/file-by-id-path task file-id) :new-comment)
                              merge form-data)))))

  CommentOnFile
  (process-event [_ {:keys [query] :as app}]
    (let [file-id (:file query)
          task (get-in app [:route :activity-task])
          new-comment (-> task
                          (get-in (task-model/file-by-id-path task file-id))
                          :new-comment
                          :comment/comment)]
      (t/fx app
            {:tuck.effect/type :command!
             :command :document/comment-on-file
             :payload {:file-id (goog.math.Long/fromString file-id)
                       :comment new-comment}
             :result-event common-controller/->Refresh})))

  DeleteDocument
  (process-event [{document-id :document-id} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :document/delete
           :success-message  (tr [:document :deleted-notification])
           :payload {:document-id document-id}
           :result-event ->DeleteDocumentResult}))

  DeleteDocumentResult
  (process-event [_ {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page page
           :params params
           :query (dissoc query :document)}
          common-controller/refresh-fx))

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

  UpdateDocumentForm
  (process-event [{form-data :form-data} app]
    (update app :new-document
            (fn [old-form]
              (let [new-form-data (merge old-form form-data)]
                (if (contains? form-data :document/category)
                  (dissoc new-form-data :document/sub-category)
                  new-form-data)))))

  CreateDocument
  (process-event [_ {doc :new-document :as app}]
    (let [task (get-in app [:params :task])
          files (:document/files doc)
          file-count (count files)]
      (log/info "CREATE DOC " doc " WITH " file-count " files")
      (t/fx (update app :new-document assoc :in-progress? 0)
            {:tuck.effect/type :command!
             :command :document/new-document
             :payload {:document (select-keys doc
                                              [:document/name :document/status :document/description :document/category :document/sub-category :document/author])
                       :task-id (goog.math.Long/fromString task)}
             :result-event (fn [doc-id]
                             (map->UploadFiles {:files files
                                                :document-id doc-id
                                                :progress-increment (int (/ 100 file-count))
                                                :on-success ->UploadFinished}))})))

  AddFilesToDocument
  (process-event [{files :files} app]
    (let [doc-id (goog.math.Long/fromString (get-in app [:query :document]))]
      (t/fx app
            (fn [e!]
              (e! (map->UploadFiles {:files files
                                     :document-id doc-id
                                     :progress-increment (int (/ 100 (count files)))
                                     :on-success ->UploadFinished}))))))

  UpdateDocumentStatus
  (process-event [{status :status :as event} app]
    (log/info "UpdateDocumentStatus" event)
    (let [{id :db/id} (common-controller/page-state app)]
      (t/fx app
        {:tuck.effect/type :command!
         :command :document/update-document
         :payload {:db/id id
                   :document/status status}
         :result-event common-controller/->Refresh})))

  UploadFiles
  (process-event [{:keys [files document-id on-success progress-increment] :as event} app]
    (if-let [file (first files)]
      ;; More files to upload
      (do
        (log/info "More files to upload. Uploading: " file)
        (t/fx (if progress-increment
                (update-in app [:new-document :in-progress?]
                           + progress-increment)
                app)
              {:tuck.effect/type :command!
               :command :document/upload-file
               :payload {:document-id document-id
                         :file (file-info file)}
               :result-event (fn [result]
                               (map->UploadFileUrlReceived
                                (merge result
                                       {:file-data file
                                        :on-success (update event :files rest)})))}))
      (do
        (log/info "No more files to upload. Return on-success event: " on-success)
        (t/fx app
              (fn [e!] (e! (on-success)))))))

  UploadFilesToDocument
  (process-event [{files :files} app]
    (let [doc-id  (get-in app [:params :document])]
      (t/fx (common-controller/update-page-state
             app [:document/files]
             into (comp (map file-info)
                        (map #(assoc %
                                     :in-progress? true
                                     :db/id (str (random-uuid))))) files)

            (fn [e!]
              (e! (map->UploadFiles {:files files
                                     :document-id (goog.math.Long/fromString doc-id)
                                     :on-success common-controller/->Refresh}))))))


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
  (process-event [_ app]
    (t/fx (dissoc app :new-document)
          {:tuck.effect/type :navigate
           :page :activity-task
           :params (:params app)
           :query {}}
          common-controller/refresh-fx)))

(defn download-url [file-id]
  (common-controller/query-url :document/download-file {:file-id file-id}))
