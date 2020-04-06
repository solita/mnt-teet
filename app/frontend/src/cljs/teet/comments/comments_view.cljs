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
            [teet.user.user-info :as user-info]))

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

(defn- comment-entry [e! {id :db/id :comment/keys [author comment timestamp] :as entity}]
  [:div {:class (<class common-styles/margin-bottom 1)}
   [:div {:class [(<class common-styles/space-between-center) (<class common-styles/margin-bottom 0)]}
    [:span
     [typography/SectionHeading
      {:style {:display :inline-block}}
      [user-info/user-name author]]
     [typography/GreyText {:style {:display :inline-block
                                   :margin-left "1rem"}}
      (format/date timestamp)]
     [:span {:class (<class common-styles/margin-left 0.5)}
      [buttons/button-text {;; :size :small
                            :start-icon (r/as-element [icons/editor-format-quote])}
       (tr [:buttons :quote])]]]]

   [typography/Paragraph comment]
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


(defn lazy-comments
  [{:keys [e! app
           entity-type
           entity-id
           show-comment-form?]
    :or {show-comment-form? true}}]
  (r/with-let [[comment-form ->UpdateCommentForm] (common-controller/internal-state {})]
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
                     :save-event #(let [comment (:comment/comment @comment-form)]
                                    (reset! comment-form {})
                                    (comments-controller/->CommentOnEntity
                                     entity-type entity-id comment))
                     :footer new-comment-footer
                     :spec :task/new-comment-form}
          ^{:attribute :comment/comment}
          [TextField {:rows 4
                      :multiline true
                      :InputLabelProps {:shrink true}
                      :full-width true
                      :placeholder (tr [:document :new-comment])}]])])))

(defn comments [{:keys [e!
                        new-comment
                        comments
                        update-comment-event
                        save-comment-event]}]
  [layout/section
   [:div {:class (<class common-styles/gray-light-border)}
    [typography/Heading3 (tr [:document :comments])]
    [:span {:class (<class comments-styles/comment-amount)}
     (count comments)]]
   [comment-list e! nil comments nil]

   [form/form {:e!              e!
               :value           new-comment
               :on-change-event update-comment-event
               :save-event      save-comment-event
               :footer          new-comment-footer
               :spec            :document/new-comment-form}
    ^{:attribute :comment/comment}
    [TextField {:rows            4
                :multiline       true
                :InputLabelProps {:shrink true}
                :full-width      true
                :placeholder     (tr [:document :new-comment])}]]])
