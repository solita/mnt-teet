(ns teet.comments.comments-controller
  (:require [tuck.core :as t]
            [goog.math.Long]
            tuck.effect
            [teet.project.task-model :as task-model]
            [teet.common.common-controller :as common-controller]))

(defrecord DeleteComment [comment-id])

(defrecord UpdateFileNewCommentForm [form-data])            ; update new comment on selected file
(defrecord CommentOnDocument [])                            ; save new comment to document
(defrecord UpdateNewCommentForm [form-data])                ; update new comment form data
(defrecord CommentOnEntity [entity-type entity-id comment])
(defrecord ClearCommentField [])
(defrecord CommentAddSuccess [entity-id])

(extend-protocol t/Event
  CommentOnDocument
  (process-event [_ app]
    (let [doc (get-in app [:query :document])
          new-comment (->> (get-in app [:route :activity-task :task/documents])
                           (filter #(= (str (:db/id %)) doc))
                           first
                           :new-comment
                           :comment/comment)]
      (t/fx app
            {:tuck.effect/type :command!
             :command          :comment/comment-on-document
             :payload          {:document-id (goog.math.Long/fromString doc)
                                :comment           new-comment}
             :result-event     common-controller/->Refresh})))


  UpdateFileNewCommentForm
  (process-event [{form-data :form-data} {:keys [query] :as app}]
    (let [file-id (:file query)]
      (update-in app
                 [:route :activity-task]
                 (fn [task]
                   (update-in task (conj (task-model/file-by-id-path task file-id) :new-comment)
                              merge form-data)))))

  UpdateNewCommentForm
  (process-event [{form-data :form-data} {:keys [query] :as app}]
    (update-in app
               [:route :activity-task :task/documents]
               (fn [documents]
                 (mapv #(if (= (str (:db/id %)) (:document query))
                          (assoc % :new-comment form-data)
                          %)
                       documents))))

  CommentOnEntity
  (process-event [{:keys [entity-type entity-id comment]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :comment/create
           :payload {:entity-id entity-id
                     :entity-type entity-type
                     :comment comment}
           :result-event (partial ->CommentAddSuccess entity-id)}))

  CommentAddSuccess
  (process-event [{entity-id :entity-id} app]
    ;; Add nil comment as the first comment in list
    (update-in app [:comments-for-entity entity-id]
               (fn [comments]
                 (into [nil] comments))))

  ClearCommentField
  (process-event [_ app]
    (dissoc app :comment-form))


  DeleteComment
  (process-event [{comment-id :comment-id} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command          :comment/delete-comment
           :payload          {:comment-id comment-id}
           :result-event     common-controller/->Refresh})))
