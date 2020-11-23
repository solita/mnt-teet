(ns teet.comments.comments-controller
  (:require [goog.math.Long]
            [tuck.core :as t]
            tuck.effect
            [teet.localization :refer [tr]]
            [teet.project.task-model :as task-model]
            [teet.ui.animate :as animate]))

(defn comment-dom-id [id]
  (str "comment-" id))

(defrecord DeleteComment [comment-id commented-entity after-comment-deleted-event])
(defrecord DeleteCommentSuccess [commented-entity after-comment-deleted-event])
(defrecord QueryEntityComments [commented-entity])

(defrecord UpdateFileNewCommentForm [form-data])            ; update new comment on selected file
(defrecord UpdateNewCommentForm [form-data])                ; update new comment form data
(defrecord CommentOnEntity [entity-type entity-id comment files visibility track? mentions
                            after-comment-added-event])
(defrecord ClearCommentField [])
(defrecord CommentsSeen [entity-id comment-target])
(defrecord SetCommentsAsOld [update-path])
(defrecord CommentAddSuccess [entity-id after-comment-added-event])

(defrecord SaveEditCommentForm [commented-entity form-data])
(defrecord SaveEditCommentSuccess [commented-entity results])

(defrecord SetCommentStatus [comment-id status commented-entity])
(defrecord ResolveCommentsOfEntity [entity-id entity-type])

(defrecord FocusOnComment [comment-id])

(defrecord IncrementCommentCount [path])
(defrecord DecrementCommentCount [path])

(defn comments-query [commented-entity]
  {:tuck.effect/type :query
   :query :comment/fetch-comments
   :args commented-entity
   :result-path [:comments-for-entity (:eid commented-entity)]})

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
  (process-event [{:keys [entity-type entity-id comment files visibility track? mentions
                          after-comment-added-event]} app]
    (assert (some? visibility))
    (let [mentions (vec (keep :user mentions))]
      (t/fx app
            {:tuck.effect/type :command!
             :command :comment/create
             :payload {:entity-id entity-id
                       :entity-type entity-type
                       :comment comment
                       :visibility visibility
                       :files (mapv :db/id files)
                       :track? track?
                       :mentions mentions}
             :result-event (partial ->CommentAddSuccess entity-id after-comment-added-event)})))

  CommentAddSuccess
  (process-event [{:keys [entity-id after-comment-added-event]} app]
    (t/fx
     ;; Add nil comment as the first comment in list
     (update-in app [:comments-for-entity entity-id]
                (fn [comments]
                  (into [nil] comments)))
     (fn [e!]
       (when after-comment-added-event
         (e! (after-comment-added-event))))))

  ClearCommentField
  (process-event [_ app]
    (dissoc app :comment-form))


  DeleteComment
  (process-event [{:keys [comment-id commented-entity after-comment-deleted-event]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command          :comment/delete-comment
           :payload          {:comment-id comment-id}
           :success-message (tr [:notifications :comment-deleted])
           :result-event     (partial ->DeleteCommentSuccess commented-entity after-comment-deleted-event)}))

  DeleteCommentSuccess
  (process-event [{:keys [commented-entity after-comment-deleted-event]} app]
    (t/fx app
          (fn [e!]
            (when after-comment-deleted-event
              (e! (after-comment-deleted-event))))
          (fn [e!]
            (e! (->QueryEntityComments commented-entity)))))

  QueryEntityComments
  (process-event [{:keys [commented-entity]} app]
    (t/fx app
          (comments-query commented-entity)))

  SetCommentsAsOld
  (process-event [{:keys [update-path]} app]
    (update-in app update-path (fn [{:comment/keys [old-comments new-comments] :as val}]
                                 {:comment/old-comments (+ old-comments new-comments)
                                  :comment/new-comments 0})))

  CommentsSeen
  (process-event [{:keys [entity-id comment-target]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :comment/entity-comments-seen
           :payload {:eid entity-id
                     :for comment-target}
           :result-event :ignore}))

  SaveEditCommentForm
  (process-event [{:keys [form-data commented-entity]} app]
    (let [mentions (vec (keep :user (:comment/mentions form-data)))]
      (t/fx app
            {:tuck.effect/type :command!
             :result-event (partial ->SaveEditCommentSuccess commented-entity)
             :command :comment/update
             :payload (-> form-data
                          (select-keys [:db/id :comment/comment :comment/visibility :comment/files])
                          (merge {:comment/mentions mentions})
                          (update :comment/files (partial map :db/id)))
             :success-message (tr [:notifications :comment-edited])})))

  SaveEditCommentSuccess
  (process-event [{:keys [commented-entity]} app]
    (t/fx app
          (comments-query commented-entity)))

  SetCommentStatus
  (process-event [{:keys [comment-id status commented-entity]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :comment/set-status
           :payload {:db/id comment-id
                     :comment/status status}
           :result-event (partial ->QueryEntityComments commented-entity)
           :success-message (tr [:notifications :comment-status-changed])}))

  ResolveCommentsOfEntity
  (process-event [{:keys [entity-id entity-type]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :comment/resolve-comments-of-entity
           :payload {:entity-id entity-id
                     :entity-type entity-type}
           :result-event (partial ->QueryEntityComments {:eid entity-id
                                                         :for entity-type})
           :success-message (tr [:notifications :comments-resolved])}))

  FocusOnComment
  (process-event [{:keys [comment-id]} {:keys [page params query] :as app}]
    (animate/scroll-into-view-by-id! (comment-dom-id comment-id)
                                     {:behavior :smooth})
    app)

  IncrementCommentCount
  (process-event [{path :path} app]
    (update-in app path
               #(inc (or % 0))))
  DecrementCommentCount
  (process-event [{path :path} app]
    (update-in app path
               #(when %
                  (dec %)))))
