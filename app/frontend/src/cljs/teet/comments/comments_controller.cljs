(ns teet.comments.comments-controller
  (:require [goog.math.Long]
            [tuck.core :as t]
            tuck.effect
            [teet.common.common-controller :as common-controller]
            [teet.localization :refer [tr]]
            [teet.project.task-model :as task-model]))

(defrecord DeleteComment [comment-id])

(defrecord UpdateFileNewCommentForm [form-data])            ; update new comment on selected file
(defrecord UpdateNewCommentForm [form-data])                ; update new comment form data
(defrecord CommentOnEntity [entity-type entity-id comment files])
(defrecord ClearCommentField [])
(defrecord CommentAddSuccess [entity-id])

(defrecord OpenEditCommentDialog [comment-id commented-entity comment])
(defrecord UpdateEditCommentForm [form-data])
(defrecord CancelCommentEdit [])
(defrecord SaveEditCommentForm [])
(defrecord SaveEditCommentSuccess [])

(extend-protocol t/Event

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
  (process-event [{:keys [entity-type entity-id comment files]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :comment/create
           :payload {:entity-id entity-id
                     :entity-type entity-type
                     :comment comment
                     :files (mapv :db/id files)}
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
           :result-event     common-controller/->Refresh}))

  OpenEditCommentDialog
  (process-event [{:keys [comment-id commented-entity comment]} app]
    (-> app
        (assoc-in [:stepper :dialog] {:type :edit-comment})
        (assoc :edit-comment-data
               {:db/id comment-id
                :comment/commented-entity commented-entity
                :comment/comment comment})))

  CancelCommentEdit
  (process-event [_ {:keys [page params] :as app}]
    (t/fx (-> app
              (dissoc :edit-comment-data)
              (update :stepper dissoc :dialog))
          {:tuck.effect/type :navigate
           :page             page
           :params           params
           :query            {:tab "comments"}}))

  UpdateEditCommentForm
  (process-event [{form-data :form-data} app]
    (update-in app [:edit-comment-data] merge form-data))

  SaveEditCommentForm
  (process-event [_ {edit-comment-data :edit-comment-data
                     stepper :stepper :as app}]
    (t/fx app
          (merge
           {:tuck.effect/type :command!
            :result-event ->SaveEditCommentSuccess
            :command :comment/update
            :payload (select-keys edit-comment-data [:db/id :comment/comment])
            :success-message (tr [:notifications :comment-edited])})))

  SaveEditCommentSuccess
  (process-event [_ app]
    (let [commented-entity (-> app :edit-comment-data :comment/commented-entity)]
      (t/fx (-> app
               (dissoc :edit-comment-data)
               (update :stepper dissoc :dialog))
           {:tuck.effect/type :query
            :query :comment/fetch-comments
            :args commented-entity
            :result-path [:comments-for-entity (:db/id commented-entity)]}))))
