(ns teet.comments.comments-view
  (:require [teet.authorization.authorization-check :refer [when-authorized]]
            [teet.ui.form :as form]
            [teet.ui.layout :as layout]
            [teet.common.common-styles :as common-styles]
            [herb.core :refer [<class]]
            [teet.localization :refer [tr]]
            teet.task.task-spec
            [teet.ui.itemlist :as itemlist]
            [teet.ui.query :as query]
            [teet.ui.format :as format]
            [teet.comments.comments-styles :as comments-styles]
            [teet.ui.typography :as typography]
            [teet.user.user-info :as user-info]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.buttons :as buttons]
            [teet.comments.comments-controller :as comment-controller]
            [teet.ui.skeleton :as skeleton]))

(defn- new-comment-footer [{:keys [validate disabled?]}]
  [:div {:class (<class comments-styles/comment-buttons-style)}
   [buttons/button-primary {:disabled disabled?
                            :type     :submit
                            :on-click validate}
    (tr [:comment :save])]])

(defn comment-list
  [e! _app comments _breacrumbs]
  [itemlist/ItemList {}
   (doall
     (for [{id :db/id
            :comment/keys [author comment timestamp] :as entity} comments]
       ^{:key id}
       [:div
        [:div {:class [(<class common-styles/space-between-center) (<class common-styles/margin-bottom 0.5)]}
         [:span
          [typography/SectionHeading
           {:style {:display :inline-block}}
           [user-info/user-name author]]
          [typography/GreyText {:style {:display :inline-block
                                        :margin-left "1rem"}}
           (format/date timestamp)]]
         (when-authorized :comment-delete
                          entity
                          [buttons/delete-button-with-confirm {:small? true
                                                               :action (e! comment-controller/->DeleteComment id)}
                           (tr [:buttons :delete])])]
        [typography/Paragraph comment]]))])

(defn comment-skeleton
  [n]
  [:<>
   (doall
     (for [y (range n)]
       ^{:key y}
       [skeleton/skeleton {:parent-style (skeleton/comment-skeleton-style)}]))])

(defn lazy-comments
  [{:keys [e! app
           entity-type
           entity-id
           new-comment
           update-comment-event
           save-comment-event]}]
  (let [comments (get-in app [:comments-for-entity entity-id])]
    [layout/section
     [query/query {:e! e!
                   :query (case entity-type
                            :task :task/fetch-comments
                            :task/fetch-comments)
                   :args {:db/id entity-id}
                   :skeleton [comment-skeleton 2]
                   :state-path [:comments-for-entity entity-id]
                   :state comments
                   :view comment-list
                   :refresh (count comments)}]
     [form/form {:e! e!
                 :value new-comment
                 :on-change-event update-comment-event
                 :save-event save-comment-event
                 :footer new-comment-footer
                 :spec :task/new-comment-form}
      ^{:attribute :comment/comment}
      [TextField {:rows 4
                  :multiline true
                  :InputLabelProps {:shrink true}
                  :full-width true
                  :placeholder (tr [:document :new-comment])}]]]))

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
