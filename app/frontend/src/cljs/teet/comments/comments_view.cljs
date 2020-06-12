(ns teet.comments.comments-view
  (:require [herb.core :refer [<class]]
            [reagent.core :as r]
            [tuck.core :as t]
            [teet.app-state :as app-state]
            [teet.authorization.authorization-check :as authorization-check :refer [when-authorized]]
            [teet.comment.comment-model :as comment-model]
            teet.comment.comment-spec
            [teet.comments.comments-controller :as comments-controller]
            [teet.comments.comments-styles :as comments-styles]
            [teet.common.common-controller :as common-controller]
            [teet.common.common-styles :as common-styles]
            [teet.localization :refer [tr]]
            [teet.project.project-navigator-view :as project-navigator-view]
            teet.task.task-spec
            [teet.ui.animate :as animate]
            [teet.ui.buttons :as buttons]
            [teet.ui.form :as form]
            [teet.ui.format :as format]
            [teet.ui.icons :as icons]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.project-context :as project-context]
            [teet.ui.query :as query]
            [teet.ui.select :as select]
            [teet.ui.skeleton :as skeleton]
            [teet.ui.mentions :refer [mentions-input]]
            [teet.ui.material-ui :refer [IconButton]]
            [teet.ui.typography :as typography]
            [teet.ui.util :as util :refer [mapc]]
            [teet.ui.file-upload :as file-upload]
            [teet.user.user-info :as user-info]
            [teet.user.user-model :as user-model]
            [teet.util.collection :as cu]
            [teet.file.file-controller :as file-controller]
            [teet.log :as log]
            [teet.theme.theme-colors :as theme-colors]
            [clojure.string :as str]))


(defn- new-comment-footer [{:keys [validate disabled?]}]
  [:div {:class (<class comments-styles/comment-buttons-style)}
   [buttons/button-primary {:disabled disabled?
                            :type     :submit
                            :on-click validate}
    (tr [:comment :save])]])

(defn- comment-skeleton
  [n]
  [:<>
   (doall
     (for [y (range n)]
       ^{:key y}
       [skeleton/skeleton {:parent-style (skeleton/comment-skeleton-style)}]))])

(defn attachments [{:keys [files on-delete comment-id]}]
  (when (seq files)
    [:div {:class (<class comments-styles/attachment-list)}
     (util/with-keys
       (mapc (fn [{file-id :db/id name :file/name :as file}]
               [:div {:class (<class comments-styles/attachment-list-item)}
                [:a {:class (<class comments-styles/attachment-link)
                     :target :_blank
                     :href (common-controller/query-url :file/download-attachment
                                                        {:comment-id comment-id
                                                         :file-id file-id})}
                 name]
                (when on-delete
                  [IconButton {:size :small
                               :on-click #(on-delete file)}
                   [icons/action-delete]])])
             files))]))

(defn- edit-attached-images-field
  "File field that only allows uploading images. Files are
  directly uploaded and on-change called after success."
  [{:keys [e! value comment-id on-success-event]}]
  [project-context/consume
   (fn [{:keys [project-id]}]
     [:div
      [attachments {:files value
                    :comment-id comment-id
                    :on-delete (fn [{file-id :db/id}]
                                 (e! (comments-controller/->UpdateEditCommentForm
                                      {:comment/files
                                       (into []
                                             (cu/remove-by-id file-id)
                                             value)})))}]
      [file-upload/FileUploadButton
       {:id "images-field"
        :color :secondary
        :button-attributes {:variant :text
                            :size :small}
        :on-drop #(e! (file-controller/map->UploadFiles
                       {:files %
                        :project-id project-id
                        :attachment? true
                        :on-success (fn [uploaded-files]
                                      (log/info "FILES UPLOADED: " uploaded-files)
                                      (on-success-event
                                       {:comment/files (into (or value [])
                                                             uploaded-files)}))}))}
       (str "+ " (tr [:comment :add-images]))]])])

(defn form-field-spacer
  []
  {:margin-bottom "1rem"})

;; TODO: Both this and the create comment form should be replaced with
;;       form2 to make the add image button look decent.
(defn- edit-comment-form [e! comment-data]
  (r/with-let [[comment-form ->UpdateCommentForm]
               (common-controller/internal-state comment-data
                                                 {:merge? true})]
    [form/form2 {:e! e!
                 :value @comment-form
                 :on-change-event ->UpdateCommentForm
                 :cancel-event comments-controller/->CancelCommentEdit
                 :save-event #(comments-controller/->SaveEditCommentForm @comment-form)
                 :spec :comment/edit-comment-form}
     [:div {:class (<class common-styles/gray-container-style)}
      [:div {:class (<class form-field-spacer)}
       [form/field :comment/comment
        [mentions-input {:e! e!}]]

       [form/field :comment/files
        [edit-attached-images-field {:e! e!
                                     :comment-id (:db/id comment-data)
                                     :on-success-event comments-controller/->UpdateEditCommentForm}]]]

      [:div {:class (<class form-field-spacer)}
       [form/field :comment/visibility
        [select/select-enum {:e! e! :attribute :comment/visibility}]]]]

     [form/footer2]]))

(defmethod project-navigator-view/project-navigator-dialog :edit-comment
  [{:keys [e! app] :as _opts} _dialog]
  [edit-comment-form e! (:edit-comment-data app)])

(defn- edit-comment-button [e! comment-entity commented-entity]
  [buttons/button-text {:size :small
                        :color :primary
                        :start-icon (r/as-element [icons/image-edit])
                        :on-click #(e! (comments-controller/->OpenEditCommentDialog comment-entity commented-entity))}
   (tr [:buttons :edit])])

(defn- comment-text
  "Split comment text and highlight user mentions."
  [text]
  [:span
   (doall
    (map-indexed
     (fn [i part]
       (if-let [[_ name :as m] (re-matches comment-model/user-mention-name-pattern part)]
         ^{:key (str i)}
         [:b (str "@" name)]
         ^{:key (str i)}
         [:span part]))
     (str/split text comment-model/user-mention-pattern)))])

(defn- comment-contents-and-status
  [e!
   {:meta/keys [modified-at]
    :comment/keys [comment status mentions]
    comment-id :db/id
    :as comment-entity}
   commented-entity]
  [:div {:class (<class comments-styles/comment-contents
                        (comment-model/tracked? comment-entity)
                        (:db/ident status))}
   (when (comment-model/tracked? comment-entity)
     [:div {:class (<class comments-styles/comment-status (:db/ident status))}
      (tr [:enum (:db/ident status)])
      [when-authorized :comment/set-status
       comment-entity
       (case (:db/ident status)
         :comment.status/unresolved
         [buttons/button-text {:color :primary
                               :end-icon (r/as-element [icons/action-check-circle-outline])
                               :on-click #(e! (comments-controller/->SetCommentStatus comment-id
                                                                                      :comment.status/resolved
                                                                                      commented-entity))}
          (tr [:comment :resolve])]

         :comment.status/resolved
         [buttons/button-text {:end-icon (r/as-element [icons/content-block])
                               :on-click #(e! (comments-controller/->SetCommentStatus comment-id
                                                                                      :comment.status/unresolved
                                                                                      commented-entity))}
          (tr [:comment :unresolve])])]])

   [typography/Text
    [comment-text comment]
    (when modified-at
      [:span {:class (<class comments-styles/data)}
       (tr [:comment :edited]
           {:date (format/date modified-at)})])]])

(defn- comment-entry [e! {id :db/id
                          :comment/keys [author comment timestamp files visibility]
                          :as comment-entity}
                      commented-entity
                      quote-comment!
                      focused?]
  [:div (merge {:id (comments-controller/comment-dom-id id)
                :class (<class comments-styles/comment-entry focused?)}
               (when focused?
                 {:ref (fn [el]
                         (when el
                           (animate/focus! el)))}))
   [:div {:class [(<class common-styles/space-between-center) (<class common-styles/margin-bottom 0)]}
    [:div
     [typography/SectionHeading
      {:style {:display :inline-block}}
      [user-info/user-name author]]
     [:span {:class (<class comments-styles/data)}
      (format/date timestamp)]
     [buttons/button-text {:size :small
                           :color :primary
                           :start-icon (r/as-element [icons/editor-format-quote])
                           :on-click #(quote-comment! (user-model/user-name author)
                                                      comment)}
      (tr [:comment :quote])]]
    [:span {:class (<class comments-styles/data)}
     (tr [:enum (:db/ident visibility)])]]
   [comment-contents-and-status e! comment-entity commented-entity]
   [:div
    [when-authorized :comment/update
     comment-entity
     [edit-comment-button e! comment-entity commented-entity]]
    [when-authorized :comment/delete-comment
     comment-entity
     [buttons/delete-button-with-confirm {:small? true
                                          :icon-position :start
                                          :action (e! comments-controller/->DeleteComment id commented-entity)}
      (tr [:buttons :delete])]]]
   [attachments {:files files :comment-id id}]])

(defn unresolved-comments-info [e! commented-entity unresolved-comments]
  [:div {:class (<class comments-styles/unresolved-comments)}
   (tr [:comment :unresolved-count] {:unresolved-count (count unresolved-comments)})
   [buttons/link-button {:on-click #(e! (comments-controller/->FocusOnComment (-> unresolved-comments first :db/id)))}
    (tr [:comment :open-latest-unresolved])]
   [when-authorized :comment/resolve-comments-of-entity
    {}
    [buttons/button-text {:color :primary
                          :end-icon (r/as-element [icons/action-check-circle-outline])
                          :on-click #(e! (comments-controller/->ResolveCommentsOfEntity
                                          (:db/id commented-entity)
                                          (:for commented-entity)))}
     (tr [:comment :resolve-all])]]])

(defn comment-list
  [{:keys [e! quote-comment! commented-entity focused-comment]} comments]
  (let [unresolved-comments (filterv comment-model/unresolved? comments)]
    [:<>
     (when (seq unresolved-comments)
       [unresolved-comments-info e! commented-entity unresolved-comments])
     [itemlist/ItemList {}
      (doall
       (for [{id :db/id :as comment-entity} comments
             :let [focused? (= (str id) focused-comment)]]
         (if (nil? comment-entity)
           ;; New comment was just added but hasn't been refetched yet, show skeleton
           ^{:key "loading-comment"}
           [comment-skeleton 1]

           ^{:key (str id)}
           [comment-entry e! comment-entity commented-entity quote-comment! focused?])))]]))

(defn- quote-comment-fn
  "An ad hoc event that merges the quote at the end of current new
  comment text."
  [internal-state-atom]
  (fn [new-value]
    (reify t/Event
      (process-event [_ app]
        (swap! internal-state-atom update :comment/comment
               (fn [old-value]
                 (if (not-empty old-value)
                   (str old-value "\n" new-value)
                   new-value)))
        (animate/scroll-into-view-by-id! "new-comment-input" {:behavior :smooth})
        (js/setTimeout #(animate/focus-by-id! "new-comment-input")
                       500)
        app))))

(defn- attached-images-field
  "File field that only allows uploading images. Files are
  directly uploaded and on-change called after success."
  [{:keys [e! value on-success-event]}]
  [project-context/consume
   (fn [{:keys [project-id]}]
     [:div
      [attachments {:files value
                    :comment-id nil
                    :on-delete (fn [{id :db/id}]
                                 (e! (file-controller/->DeleteAttachment
                                      (constantly
                                       (on-success-event
                                        {:comment/files
                                         (into []
                                               (cu/remove-by-id id)
                                               value)}))
                                      id)))}]
      [file-upload/FileUploadButton
       {:id "images-field"
        :color :secondary
        :button-attributes {:variant :text
                            :size :small}
        :on-drop #(e! (file-controller/map->UploadFiles
                       {:files %
                        :project-id project-id
                        :attachment? true
                        :on-success (fn [files]
                                      (log/info "FILES UPLOADED: " files)
                                      (on-success-event
                                       {:comment/files (into (or value [])
                                                             files)}))}))}
       (str "+ " (tr [:comment :add-images]))]])])

(defn- comment-form-defaults [entity-type]
  (merge
   (case entity-type
     :file {:comment/track? true}
     {})
   {:comment/comment ""
    :comment/visibility :comment.visibility/internal}))

(defn visibility-selection-style
  []
  {:background-color :inherit
   :color theme-colors/primary})

(defn lazy-comments
  [{:keys [e! app
           entity-type
           entity-id
           show-comment-form?]
    :or {show-comment-form? true}}]
  (r/with-let [[comment-form ->UpdateCommentForm]
               (common-controller/internal-state (comment-form-defaults entity-type)
                                                 {:merge? true})]
    (let [comments (get-in app [:comments-for-entity entity-id])]
      [:div
       [query/query {:e! e!
                     :query :comment/fetch-comments
                     :args {:db/id entity-id
                            :for entity-type}
                     :skeleton [comment-skeleton 1]
                     :state-path [:comments-for-entity entity-id]
                     :state comments
                     :simple-view [comment-list {:e! e!
                                                 :quote-comment! #(e! ((quote-comment-fn comment-form)
                                                                       (str %1 ": \"" %2 "\"")))
                                                 :commented-entity {:db/id entity-id
                                                                    :for   entity-type}
                                                 :focused-comment (get-in app [:query :focus-on])}]
                     :refresh (count comments)}]
       (when (and show-comment-form?
                  ;; TODO: This circumvents the fact that there can be
                  ;; only one file upload element in the dom at once.
                  (not (-> app :stepper :dialog)))
         [form/form2 {:e! e!
                      :value @comment-form
                      :on-change-event ->UpdateCommentForm
                      :save-event #(let [{:comment/keys [comment files visibility track? mentions]} @comment-form]
                                     (reset! comment-form (comment-form-defaults entity-type))
                                     (comments-controller/->CommentOnEntity
                                       entity-type entity-id comment files visibility track? mentions))
                      :footer new-comment-footer
                      :spec :task/new-comment-form}
          [:div {:class (<class common-styles/gray-container-style)}
           [:div {:class (<class common-styles/heading-and-action-style)}
            [typography/Heading2 (tr [:document :new-comment])]
            [form/field :comment/visibility
             [select/select-enum {:e! e! :attribute :comment/visibility :tiny-select? true
                                  :show-empty-selection? false
                                  :show-label? false :class (<class visibility-selection-style)}]]]
           [:div {:class (<class form-field-spacer)}
            [form/field :comment/comment
             [mentions-input {:e! e!}]

             #_[TextField {:id "new-comment-input"
                         :rows 4
                         :multiline true
                         :full-width true
                         :placeholder (tr [:document :new-comment])}]]

            [form/field :comment/files
             [attached-images-field {:e! e!
                                     :on-success-event ->UpdateCommentForm}]]]

           ;; TODO: when-authorized doesn't play well with form
           (when (authorization-check/authorized? @app-state/user
                                                  :project/track-comment-status
                                                  {})
             [:div {:style {:text-align :right}}
              [form/field :comment/track?
               [select/checkbox {:label-placement :start}]]])
           [new-comment-footer]]])])))
