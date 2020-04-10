(ns teet.comments.comments-controller
  (:require [goog.math.Long]
            [tuck.core :as t]
            tuck.effect
            [teet.localization :refer [tr]]
            [teet.project.task-model :as task-model]))

(defrecord DeleteComment [comment-id commented-entity])
(defrecord DeleteCommentResult [commented-entity])

(defrecord UpdateFileNewCommentForm [form-data])            ; update new comment on selected file
(defrecord UpdateNewCommentForm [form-data])                ; update new comment form data
(defrecord CommentOnEntity [entity-type entity-id comment files visibility])
(defrecord ClearCommentField [])
(defrecord CommentAddSuccess [entity-id])

(defrecord OpenEditCommentDialog [comment-entity commented-entity])
(defrecord UpdateEditCommentForm [form-data])
(defrecord CancelCommentEdit [])
(defrecord SaveEditCommentForm [])
(defrecord SaveEditCommentSuccess [])

(defn comments-query [commented-entity]
  {:tuck.effect/type :query
   :query :comment/fetch-comments
   :args commented-entity
   :result-path [:comments-for-entity (:db/id commented-entity)]})

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
  (process-event [{:keys [entity-type entity-id comment files visibility] :as keto} app]
    (assert (some? visibility))
    (t/fx app
          {:tuck.effect/type :command!
           :command :comment/create
           :payload {:entity-id entity-id
                     :entity-type entity-type
                     :comment comment
                     :visibility visibility
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
  (process-event [{:keys [comment-id commented-entity]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command          :comment/delete-comment
           :payload          {:comment-id comment-id}
           :success-message (tr [:notifications :comment-deleted])
           :result-event     (partial ->DeleteCommentResult commented-entity)}))

  DeleteCommentResult
  (process-event [{:keys [commented-entity]} app]
    (t/fx app
          (comments-query commented-entity)))

  OpenEditCommentDialog
  (process-event [{:keys [comment-entity commented-entity]} app]
    (-> app
        (assoc-in [:stepper :dialog] {:type :edit-comment})
        (assoc :edit-comment-data
               (merge {:comment/files []}
                      (select-keys comment-entity
                                   [:db/id :comment/comment :comment/files])
                      {:comment/commented-entity commented-entity}))))

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
            :payload (-> edit-comment-data
                         (select-keys [:db/id :comment/comment :comment/files])
                         (update :comment/files (partial map :db/id)))
            :success-message (tr [:notifications :comment-edited])})))

  SaveEditCommentSuccess
  (process-event [_ app]
    (let [commented-entity (-> app :edit-comment-data :comment/commented-entity)]
      (t/fx (-> app
                (dissoc :edit-comment-data)
                (update :stepper dissoc :dialog))
            (comments-query commented-entity)))))
