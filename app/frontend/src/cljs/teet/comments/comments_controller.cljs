(ns teet.comments.comments-controller
  (:require [tuck.core :as t]
            [teet.log :as log]
            [goog.math.Long]
            tuck.effect
            [teet.project.task-model :as task-model]
            [teet.common.common-controller :as common-controller]))

(defrecord DeleteComment [comment-id])
(defrecord CommentOnFile [])                                ; save new comment to file
(defrecord UpdateFileNewCommentForm [form-data])            ; update new comment on selected file
(defrecord Comment [])                                      ; save new comment to document
(defrecord UpdateNewCommentForm [form-data])                ; update new comment form data

(extend-protocol t/Event
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
             :command          :comment/post-comment
             :payload          {:comment-target-id (goog.math.Long/fromString doc)
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
             :command          :comment/post-comment
             :payload          {:comment-target-id (goog.math.Long/fromString file-id)
                                :comment           new-comment}
             :result-event     common-controller/->Refresh})))


  DeleteComment
  (process-event [{comment-id :comment-id} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command          :comment/delete-comment
           :payload          {:comment-id comment-id}
           :result-event     common-controller/->Refresh})))
