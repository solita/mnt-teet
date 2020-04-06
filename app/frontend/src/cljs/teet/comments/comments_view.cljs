(ns teet.comments.comments-view
  (:require [herb.core :refer [<class]]
            [reagent.core :as r]
            [tuck.core :as t]
            [teet.authorization.authorization-check :refer [when-authorized]]
            [teet.comments.comments-controller :as comments-controller]
            [teet.comments.comments-styles :as comments-styles]
            [teet.common.common-controller :as common-controller]
            [teet.common.common-styles :as common-styles]
            [teet.localization :refer [tr]]
            teet.task.task-spec
            [teet.ui.animate :as animate]
            [teet.ui.buttons :as buttons]
            [teet.ui.form :as form]
            [teet.ui.format :as format]
            [teet.ui.icons :as icons]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.layout :as layout]
            [teet.ui.query :as query]
            [teet.ui.skeleton :as skeleton]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.material-ui :refer [IconButton]]
            [teet.ui.typography :as typography]
            [teet.ui.util :refer [mapc]]
            [teet.ui.file-upload :as file-upload]
            [teet.user.user-info :as user-info]
            [teet.user.user-model :as user-model]
            [teet.file.file-controller :as file-controller]
            [teet.log :as log]
            [teet.ui.util :as util]))

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
       (butlast
        (interleave
         (mapc (fn [{file-id :db/id name :file/name :as file}]
                 [:div {:class (<class comments-styles/attachment-list-item)}
                  [:a {:target :_blank
                       :href (common-controller/query-url :file/download-attachment
                                                          {:comment-id comment-id
                                                           :file-id file-id})}
                   name]
                  (when on-delete
                    [IconButton {:on-click #(on-delete file)}
                     [icons/action-delete]])])
               files)
         (repeat [:hr {:class (<class comments-styles/attachment-list-separator)}]))))]))

(defn- comment-entry [e! {id :db/id :comment/keys [author comment timestamp files] :as entity}
                      quote-comment!]
  [:div {:class (<class common-styles/margin-bottom 1)}
   [:div {:class [(<class common-styles/space-between-center) (<class common-styles/margin-bottom 0)]}
    [:span
     [typography/SectionHeading
      {:style {:display :inline-block}}
      [user-info/user-name author]]
     [typography/GreyText {:style {:display :inline-block
                                   :margin-left "1rem"}}
      (format/date timestamp)]
     [buttons/button-text {:size :small
                           :color :primary
                           :start-icon (r/as-element [icons/editor-format-quote])
                           :on-click #(quote-comment! (user-model/user-name author)
                                                      comment)}
      (tr [:comment :quote])]]]
   [typography/Paragraph comment]
   [attachments {:files files :comment-id id}]
   [:div ;; TODO edit button, proper styles
    (when-authorized :comment/delete-comment
      entity
      [buttons/delete-button-with-confirm {:small? true
                                           :icon-position :start
                                           :action (e! comments-controller/->DeleteComment id)}
       (tr [:buttons :delete])])]])

(defn comment-list
  [quote-comment! e! _app comments _breacrumbs]
  [itemlist/ItemList {}
   (doall
    (for [{id :db/id :as entity} comments]
      (if (nil? entity)
        ;; New comment was just added but hasn't been refetched yet, show skeleton
        ^{:key "loading-comment"}
        [comment-skeleton 1]

        ^{:key id}
        [comment-entry e! entity quote-comment!])))])

(defn- attached-images-field
  "File field that only allows uploading images. Files are
  directly uploaded and on-change called after success."
  [{:keys [e! value on-success-event]}]
  [:div
   [attachments {:files value
                 :comment-id nil
                 :on-delete (fn [{id :db/id}]
                              (e! (file-controller/->DeleteAttachment
                                   (constantly
                                    (on-success-event
                                     {:comment/files
                                      (into []
                                            (filter #(not= (:db/id %) id))
                                            value)}))
                                   id)))}]
   [file-upload/FileUploadButton
    {:id "images-field"
     :color :secondary
     :on-drop #(e! (file-controller/map->UploadFiles
                    {:files %
                     :attachment? true
                     :on-success (fn [files]
                                   (log/info "FILES UPLOADED: " files)
                                   (on-success-event
                                    {:comment/files (into (or value [])
                                                          files)}))}))}
    (tr [:comment :add-images])]])

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

(defn lazy-comments
  [{:keys [e! app
           entity-type
           entity-id
           show-comment-form?]
    :or {show-comment-form? true}}]
  (r/with-let [[comment-form ->UpdateCommentForm]
               (common-controller/internal-state {} {:merge? true})]
    (let [comments (get-in app [:comments-for-entity entity-id])]
      [layout/section
       [query/query {:e! e!
                     :query :comment/fetch-comments
                     :args {:db/id entity-id
                            :for entity-type}
                     :skeleton [comment-skeleton 1]
                     :state-path [:comments-for-entity entity-id]
                     :state comments
                     :view (partial comment-list
                                    #(e! ((quote-comment-fn comment-form)
                                          (str %1 ": \"" %2 "\""))))
                     :refresh (count comments)}]
       (when show-comment-form?
         [form/form {:e! e!
                     :value @comment-form
                     :on-change-event ->UpdateCommentForm
                     :save-event #(let [{:comment/keys [comment files]} @comment-form]
                                    (reset! comment-form {})
                                    (comments-controller/->CommentOnEntity
                                     entity-type entity-id comment files))
                     :footer new-comment-footer
                     :spec :task/new-comment-form}
          ^{:attribute :comment/comment}
          [TextField {:id "new-comment-input"
                      :rows 4
                      :multiline true
                      :InputLabelProps {:shrink true}
                      :full-width true
                      :placeholder (tr [:document :new-comment])}]

          ^{:attribute :comment/files}
          [attached-images-field {:e! e!
                                  :on-success-event ->UpdateCommentForm} ]])])))
