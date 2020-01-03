(ns teet.comments.comments-view
  (:require [teet.ui.form :as form]
            [teet.ui.layout :as layout]
            [teet.common.common-styles :as common-styles]
            [herb.core :refer [<class]]
            [teet.localization :refer [tr]]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.typography :as typography]
            [teet.user.user-info :as user-info]
            [teet.ui.text-field :refer [TextField]]))

(defn comments [{:keys [e!
                        new-comment
                        comments
                        update-comment-event
                        save-comment-event]}]
  [layout/section
   [:div {:class (<class common-styles/gray-light-border)}
    [typography/Heading3 (tr [:document :comments])]
    [:span {:style {:margin-left "1rem"}}
     (str "(" (count comments) ")")]]
   [itemlist/ItemList {}
    (doall
     (for [{id :db/id
            :comment/keys [author comment timestamp]} comments]
       ^{:key id}
       [:div
        [typography/SectionHeading
         [user-info/user-name author]]
        [:div (.toLocaleString timestamp)]
        [typography/Paragraph comment]]))]

   [form/form {:e! e!
               :value new-comment
               :on-change-event update-comment-event
               :save-event save-comment-event
               :spec :document/new-comment-form}
    ^{:attribute :comment/comment}
    [TextField {:rows 4
                :multiline true
                :InputLabelProps {:shrink true}
                :full-width true
                :placeholder (tr [:document :new-comment])
                :variant "outlined"}]]])
