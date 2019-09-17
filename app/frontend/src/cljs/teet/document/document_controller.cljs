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


;; non-multipart version
#_(defmethod tuck.effect/process-effect ::upload! [e! {:keys [file document url app-path]}]
  (-> (js/fetch url #js {:method "PUT" :body file})
      (.then (fn [^js/Response resp]
               (e! (->UpdateDocumentProgress app-path document nil))
               (when-not (.-ok resp)
                 (log/warn "Upload failed: " (.-status resp) (.-statusText resp)))))))

(defn completion-xmldoc [part-etags]
  (str "<CompleteMultipartUpload>\n"
       (clojure.string/join
              (map #(str "  <Part> <PartNumber>" %1 "</PartNumber> <ETag>" %2 "</ETag> </Part>\n")
                   (range (count part-etags))
                   part-etags))
       "</CompleteMultipartUpload>\n"))


(defmethod tuck.effect/process-effect ::upload! [e! {:keys [file document url app-path]}]
  ;; you can upload any number of parts. the ones before the last one have to be at least 5mb long.
  ;; you save the returned etags of the uploaded parts.
  ;; at the final complete-multipart phase, s3 will cat the parts together when you provide the upload id and the etags.
  (log/info "multipart upload starting")
  (let [init-multipart! #(js/fetch (str (first (clojure.string/split url "?")) "?uploads") #js {:method "POST"})        
        upload-part! #(js/fetch (str url "&partNumber=" 1 "&uploadId=" (.text %))
                                #js {:method "PUT" :body file})
        part-etags (atom [])
        complete-multipart! #(js/fetch (str url "&uploadId=" (.text %))
                                       #js {:method "POST" :body (completion-xmldoc @part-etags)})]

    (->
     (init-multipart!)
     (.then (fn [^js/Response init-resp]
              (if (.-ok init-resp)
                (do 
                  (log/info "multipart upload init got ok response, body should contain upload id:" (.text init-resp))
                  (->  (upload-part! init-resp)
                       (.then (fn [^js/Response upload-resp]
                                (if (.-ok upload-resp)
                                  (do 
                                    (log/info "part 1 upload response text:" (.text upload-resp))
                                    (log/info "part 1 etag:" (.get (.-headers upload-resp) "ETag"))
                                    (swap! part-etags conj (.get (.-headers upload-resp) "ETag"))
                                    (-> (complete-multipart! upload-resp)
                                        (.then (fn [^js/Response complete-resp]
                                                 (e! (->UpdateDocumentProgress app-path document nil))
                                                 (if (.-ok complete-resp)
                                                   (log/info "status for complete-multipart call:" (.-statusText complete-resp))
                                                   (log/warn "Upload failed: " (.-status complete-resp) (.-statusText complete-resp)))))))
                                  (log/info "part upload response not ok"))))))
                (log/info "multipart upload init failed. statustext:" (.statusText init-resp))))))))

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
