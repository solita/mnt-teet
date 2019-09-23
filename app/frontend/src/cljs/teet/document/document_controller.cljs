(ns teet.document.document-controller
  "Controller for managing document uploads"
  (:require [tuck.core :as t]
            [tuck.effect]
            [taoensso.timbre :as log]
            [goog.math.Long]
            tuck.effect))

(defrecord CreateDocument []) ; create empty document and link it to task
(defrecord CancelDocument []) ; cancel document creation
(defrecord UpdateDocumentForm [form-data])

(defrecord UploadDocument [file document app-path])
(defrecord UploadDocumentUrlReceived [file document app-path result])
(defrecord UpdateDocumentProgress [app-path document progress])

(defn- file-info [^js/File f]
  {:document/name (.-name f)
   :document/size (.-size f)
   :document/type (.-type f)})

(extend-protocol t/Event
  UploadDocument
  (process-event [{:keys [file document app-path]} app]
    (log/info "UPLOADING: " file)
    (t/fx app
          {:tuck.effect/type :command!
           :command :document/upload
           :payload document
           :result-event #(->UploadDocumentUrlReceived file document app-path %)}))

  UpdateDocumentForm
  (process-event [{form-data :form-data} app]
    (update-in app [:task (get-in app [:params :task]) :new-document] merge form-data))

  CreateDocument
  (process-event [{task-id :task-id} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :document/new-document
           :payload {:task-id (goog.math.Long/fromString task-id)}
           :result-path [:task task-id :new-document :db/id]}))

  UploadDocumentUrlReceived
  (process-event [{:keys [file app-path result]} app]
    (let [{:keys [document url]} result]
      (t/fx (update-in app app-path conj (assoc document
                                                :progress 0))
            {:tuck.effect/type ::upload!
             :file file
             :document document
             :url url
             :app-path app-path})))

  UpdateDocumentProgress
  (process-event [{:keys [document app-path progress]} app]
    (update-in app app-path
               (fn [docs]
                 (mapv (fn [doc]
                         (if (not= (:db/id document) (:db/id doc))
                           doc
                           (if (nil? progress)
                             (dissoc doc :progress)
                             (assoc doc :progress progress))))
                       docs)))))

(defmethod tuck.effect/process-effect ::upload! [e! {:keys [file document url app-path]}]
  (-> (js/fetch url #js {:method "PUT" :body file})
      (.then (fn [^js/Response resp]
               (e! (->UpdateDocumentProgress app-path document nil))
               (when-not (.-ok resp)
                 (log/warn "Upload failed: " (.-status resp) (.-statusText resp)))))))

(defmethod tuck.effect/process-effect :upload-documents [e! {:keys [files task-id project-id app-path]}]
  (assert project-id "Must specify THK project id with :project-id key")
  (assert (vector? app-path) "Must specify :app-path vector in app state to store uploaded document info")
  (assert files "Must specify :files to upload")
  (doseq [file files]
    (e! (->UploadDocument file
                          (assoc (file-info file)
                                 :thk/id project-id
                                 :task-id task-id)
                          app-path))))
