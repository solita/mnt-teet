(ns teet.document.document-controller
  "Controller for managing document uploads"
  (:require [tuck.core :as t]
            [tuck.effect]
            [taoensso.timbre :as log]
            [goog.math.Long]
            tuck.effect
            [teet.routes :as routes]
            [teet.common.common-controller :as common-controller]))

(defrecord CreateDocument []) ; create empty document and link it to task
(defrecord CancelDocument []) ; cancel document creation
(defrecord UpdateDocumentForm [form-data])
(defrecord UploadFiles [files document-id on-success progress-increment]) ; Upload files (one at a time) to document
(defrecord UploadFinished []) ; upload completed, can close dialog
(defrecord UploadFileUrlReceived [file-data file document-id url on-success])
(defrecord FetchDocument [document-id]) ; fetch document

(defrecord UpdateNewCommentForm [form-data]) ; update new comment form data
(defrecord Comment []) ; save new comment to document

(defmethod routes/on-navigate-event :task-document [{{:keys [document]} :params}]
  (->FetchDocument document))

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
  (process-event [{form-data :form-data} app]
    (update-in app [:document (get-in app [:params :document]) :new-comment] merge form-data))

  Comment
  (process-event [_ app]
    (let [doc (get-in app [:params :document])
          new-comment (get-in app [:document doc :new-comment :comment/comment])]
      (t/fx (update-in app [:document doc] dissoc :new-comment)
            {:tuck.effect/type :command!
             :command :document/comment
             :payload {:document-id (goog.math.Long/fromString doc)
                       :comment new-comment}
             :result-event (fn [_] (->FetchDocument doc))})))

  UpdateDocumentForm
  (process-event [{form-data :form-data} app]
    (update-in app [:task (get-in app [:params :task]) :new-document] merge form-data))

  CreateDocument
  (process-event [_ app]
    (let [task (get-in app [:params :task])
          path [:task task :new-document]
          doc (get-in app path)
          files (:document/files doc)
          file-count (count files)]
      (log/info "CREATE DOC " doc " WITH " file-count " files")
      (t/fx (update-in app path assoc :in-progress? 0)
            {:tuck.effect/type :command!
             :command :document/new-document
             :payload {:document (select-keys doc [:document/name :document/status :document/description])
                       :task-id (goog.math.Long/fromString task)}
             :result-event (fn [doc-id]
                             (map->UploadFiles {:files files
                                                :document-id doc-id
                                                :progress-increment (int (/ 100 file-count))
                                                :on-success ->UploadFinished}))})))

  UploadFiles
  (process-event [{:keys [files document-id on-success progress-increment] :as event} app]
    (if-let [file (first files)]
      ;; More files to upload
      (do
        (log/info "More files to upload. Uploading: " file)
        (t/fx (update-in app [:task (get-in app [:params :task]) :new-document :in-progress?]
                         + progress-increment)
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
    (t/fx (update-in app [:task (get-in app [:params :task])] dissoc :new-document)
          {:tuck.effect/type :navigate
           :page :phase-task
           :params (:params app)
           :query {}})))

(defn download-url [file-id]
  (common-controller/query-url :document/download-file {:file-id file-id}))
