(ns teet.document.document-controller
  "Controller for managing document uploads"
  (:require [tuck.core :as t]
            [tuck.effect]
            [taoensso.timbre :as log]))

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
  (let [file (first files)]
    ;; FIXME: handle multiple uploads?
    (e! (->UploadDocument file
                          (assoc (file-info file)
                                 :thk/id project-id
                                 :task-id task-id)
                          app-path))))
