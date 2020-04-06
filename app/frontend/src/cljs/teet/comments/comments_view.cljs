(ns teet.comments.comments-view
  (:require [herb.core :refer [<class]]
            [reagent.core :as r]
            [teet.authorization.authorization-check :refer [when-authorized]]
            [teet.comments.comments-controller :as comments-controller]
            [teet.comments.comments-styles :as comments-styles]
            [teet.common.common-controller :as common-controller]
            [teet.common.common-styles :as common-styles]
            [teet.localization :refer [tr]]
            teet.task.task-spec
            [teet.ui.buttons :as buttons]
            [teet.ui.form :as form]
            [teet.ui.format :as format]
            [teet.ui.icons :as icons]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.layout :as layout]
            [teet.ui.query :as query]
            [teet.ui.skeleton :as skeleton]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.typography :as typography]
            [teet.ui.util :refer [mapc]]
            [teet.ui.file-upload :as file-upload]
            [teet.user.user-info :as user-info]
            [teet.file.file-controller :as file-controller]
            [teet.log :as log]))

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

(defn- comment-entry [e! {id :db/id :comment/keys [author comment timestamp files] :as entity}]
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
                           :start-icon (r/as-element [icons/editor-format-quote])}
      (tr [:buttons :quote])]]]
   [typography/Paragraph comment]
   (when (seq files)
     [:ul {:class (<class comments-styles/attachment-list)}
      (mapc (fn [{file-id :db/id name :file/name}]
              [:li [:a {:target :_blank
                        :href (common-controller/query-url :file/download-attachment
                                                           {:comment-id id
                                                            :file-id file-id})}
                    name]])
            files)])
   [:div ;; TODO edit button, proper styles
    (when-authorized :comment/delete-comment
      entity
      [buttons/delete-button-with-confirm {:small? true
                                           :icon-position :start
                                           :action (e! comments-controller/->DeleteComment id)}
       (tr [:buttons :delete])])]])

(defn comment-list
  [e! _app comments _breacrumbs]
  [itemlist/ItemList {}
   (doall
    (for [{id :db/id :as entity} comments]
      (if (nil? entity)
        ;; New comment was just added but hasn't been refetched yet, show skeleton
        ^{:key "loading-comment"}
        [comment-skeleton 1]

        ^{:key id}
        [comment-entry e! entity])))])

(defn- attached-images-field
  "File field that only allows uploading images. Files are
  directly uploaded and on-change called after success."
  [{:keys [e! value on-success-event error]}]
  [:div
   (when (seq value)
     [:ul
      (mapc (fn [{:file/keys [name]}]
              [:li name]) value)])
   [file-upload/FileUploadButton
    {:id "images-field"
     :on-drop #(e! (file-controller/map->UploadFiles
                    {:files %
                     :attachment? true
                     :on-success (fn [files]
                                   (log/info "FILES UPLOADED: " files)
                                   (on-success-event
                                    {:comment/files (into (or value [])
                                                          files)}))}))}]])
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
                     :view comment-list
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
          [TextField {:rows 4
                      :multiline true
                      :InputLabelProps {:shrink true}
                      :full-width true
                      :placeholder (tr [:document :new-comment])}]

          ^{:attribute :comment/files}
          [attached-images-field {:e! e!
                                  :on-success-event ->UpdateCommentForm} ]])])))
