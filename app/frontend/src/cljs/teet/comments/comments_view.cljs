(ns teet.comments.comments-view
  (:require [teet.authorization.authorization-check :refer [when-authorized]]
            [teet.ui.form :as form]
            [teet.ui.layout :as layout]
            [teet.common.common-styles :as common-styles]
            [herb.core :refer [<class]]
            [teet.localization :refer [tr]]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.format :as format]
            [teet.ui.icons :as icons]
            [teet.comments.comments-styles :as comments-styles]
            [teet.ui.typography :as typography]
            [teet.user.user-info :as user-info]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.buttons :as buttons]
            [teet.comments.comments-controller :as comment-controller]
            [reagent.core :as r]
            [teet.theme.theme-colors :as theme-colors]))

(defn- new-comment-footer [{:keys [validate disabled?]}]
  [:div {:class (<class comments-styles/comment-buttons-style)}
   [buttons/button-primary {:disabled disabled?
                            :type     :submit
                            :on-click validate}
    (tr [:comment :save])]])

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
   [itemlist/ItemList {}
    (doall
      (for [{id            :db/id
             :comment/keys [author comment timestamp] :as entity} comments]
        ^{:key id}
        [:div
         [:div {:class (<class common-styles/space-between-center)}
          [typography/GreyText
           (format/date timestamp) " – " [user-info/user-name author]]
          (when-authorized :comment-delete
                           entity
                           [buttons/delete-button-with-confirm {:small? true
                                                                :action (e! comment-controller/->DeleteComment id)}
                            (tr [:buttons :delete])])]
         [typography/Paragraph comment]]))]

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
