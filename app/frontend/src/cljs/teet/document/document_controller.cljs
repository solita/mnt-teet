(ns teet.document.document-controller
  "Controller for managing document uploads"
  (:require [tuck.core :as t]
            [tuck.effect]
            [taoensso.timbre :as log]
            [goog.math.Long]
            tuck.effect
            [teet.common.common-controller :as common-controller]))

(defrecord CreateDocument []) ; create empty document and link it to task
(defrecord CancelDocument []) ; cancel document creation
(defrecord UpdateDocumentForm [form-data])
(defrecord UploadFiles [files document-id on-success progress-increment]) ; Upload files (one at a time) to document
(defrecord UploadFinished []) ; upload completed, can close dialog
(defrecord UploadFileUrlReceived [file-data file document-id url on-success])
(defrecord FetchDocument [document-id]) ; fetch document
(defrecord UploadFilesToDocument [files]) ; upload new files to existing document from the document page

(defrecord UpdateNewCommentForm [form-data]) ; update new comment form data
(defrecord Comment []) ; save new comment to document

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
    (update-in app [:new-comment] merge form-data))

  Comment
  (process-event [_ app]
    (let [doc (get-in app [:params :document])
          new-comment (get-in app [:new-comment :comment/comment])]
      (t/fx (dissoc app :new-comment)
            {:tuck.effect/type :command!
             :command :document/comment
             :payload {:document-id (goog.math.Long/fromString doc)
                       :comment new-comment}
             :result-event common-controller/->Refresh})))

  UpdateDocumentForm
  (process-event [{form-data :form-data} app]
    (update app :new-document merge form-data))

  CreateDocument
  (process-event [_ {doc :new-document :as app}]
    (let [task (get-in app [:params :task])
          files (:document/files doc)
          file-count (count files)]
      (log/info "CREATE DOC " doc " WITH " file-count " files")
      (t/fx (update app :new-document assoc :in-progress? 0)
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
      (t/fx (update-in app [:route :task-document :document/files]
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
    (t/fx (update-in app [:task (get-in app [:params :task])] dissoc :new-document)
          {:tuck.effect/type :navigate
           :page :phase-task
           :params (:params app)
           :query {}}
          (fn [e!]
            (e! (common-controller/->Refresh))))))

(defn download-url [file-id]
  (common-controller/query-url :document/download-file {:file-id file-id}))
