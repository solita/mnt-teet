(ns teet.ui.itemlist
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [List ListItem ListItemIcon
                                         ListItemSecondaryAction Divider Link
                                         FormControlLabel Checkbox]]
            [teet.ui.icons :as icons]
            [teet.ui.progress :as progress]
            [teet.localization :refer [tr tr-tree]]
            [teet.ui.typography :refer [Heading2 Heading3 SectionHeading DataLabel]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.theme.itemlist-styles :as itemlist-styles]
            [herb.core :refer [<class]]
            [teet.ui.util :as util]
            [teet.util.collection :as uc]
            [teet.map.map-controller :as map-controller]
            [teet.ui.typography :as typography]))

(defn ListHeading
  [{:keys [title subtitle action variant]
    :or   {variant :primary}}]
  [:div {:class (<class itemlist-styles/heading variant)}
   (case variant
     :primary [Heading2 title]
     :secondary [Heading3 title])
   (when action
     [:div {:class (<class itemlist-styles/heading-action)}
      action])
   (when subtitle
     [DataLabel subtitle])])

(defn ItemList
  [{:keys [title subtitle variant class]
    :or   {variant :primary}} & children]
  [:div (when class
          {:class class})
   (when title
     [ListHeading {:title    title
                   :subtitle subtitle
                   :variant  variant}])
   (util/with-keys children)])

(defn ProgressList
  [titles items]
  (let [success (count (filterv #(= (:status %) :success) items))
        fails (count (filterv #(= (:status %) :fail) items))
        in-progress (count (filterv #(= (:status %) :in-progress) items))]
    [ItemList
     titles
     [:div {:style {:display :flex}}
      [:div {:style {:flex 1}}
       [List
        {:dense false}
        (for [item items]
          ^{:key (:name item)}
          [:<>
           [ListItem {:button    true
                      :component "a"
                      :href      (:link item)}
            [ListItemIcon
             (case (:status item)
               :success
               [icons/navigation-check {:style {:color theme-colors/success}}]
               :fail
               [icons/alert-error-outline {:color :error}]
               [icons/content-remove])]
            [SectionHeading (:name item)]
            [ListItemSecondaryAction
             [ListItemIcon {:style {:justify-content :flex-end}}
              [icons/navigation-chevron-right {:color :secondary}]]]]
           [Divider]])]]
      [:div {:style {:text-align :center
                     :margin     "0 0.5rem"}}
       [progress/circle
        {:radius 70 :stroke 9
         :defs [[:pattern {:id "progress"
                           :x 0 :y 0
                           :width 9
                           :height 9
                           :patternUnits "userSpaceOnUse"
                           :patternTransform "rotate(-45 0 0)"}
                 [:rect {:x 0 :y 0 :width 9 :height 9 :fill theme-colors/success}]
                 [:line {:x1 4 :y1 0
                         :x2 4 :y2 9
                         :style {:stroke "lightgray" :stroke-width 4}}]]]
         :slices [[success theme-colors/success]
                  [fails "red"]
                  [in-progress "url(#progress)"]]}]]]]))

(defn LinkList
  [titles items on-click-fn]
  [ItemList
   titles
   [:div {:style {:margin-top "1rem"}}
    [:ul {:style {:list-style "none"
                  :margin     0
                  :padding    0}}
     (for [item items]
       ^{:key (:name item)}
       [:li
        [Link {:href    (:link item)
               :onClick #(on-click-fn item)}
         (:name item)]])]]])

(defn Item [{:keys [label]} value]
  [:div
   [:b (str label ": ")]
   value])

(defn checkbox-item [{:keys [checked? value on-change on-mouse-enter on-mouse-leave] :as item}]
  [FormControlLabel
   (uc/without-nils {:class          (<class itemlist-styles/checkbox-container)
                     :on-mouse-enter on-mouse-enter
                     :on-mouse-leave on-mouse-leave
                     :label          (r/as-component [:span
                                                      {:class (<class itemlist-styles/checkbox-label checked?)}
                                                      value])
                     :control        (r/as-component [Checkbox {:checked   checked?
                                                                :value     value
                                                                :class     (<class itemlist-styles/layer-checkbox)
                                                                :color     :primary
                                                                :on-change on-change}])})])

(defn checkbox-list
  ([items] (checkbox-list {} items))
  ([{:keys [key]
     :or   {key :id}}
    items]
   [:div {:class (<class itemlist-styles/checkbox-list-contents)}
    (doall
      (for [item items]
        ^{:key (key item)}
        [checkbox-item item]))]))

(defn white-link-list
  [items]
  [:ul {:style {:padding-left 0}}
   (doall
     (for [{:keys [key href title selected?]} items]
       ^{:key key}
       [:li {:class (<class itemlist-styles/white-link-item-style)}
        [:a {:class (<class itemlist-styles/white-link-style selected?)
             :href  href} title]]))])

(defn permission-list
  [permissions]
  [:ul {:style {:padding 0}}
   (for [{:keys [user] :as permission} permissions]
     ^{:key (:db/id user)}
     [:li {:class (<class itemlist-styles/user-list-element-style)}
      [typography/Heading3
       (:user/given-name user) " " (:user/family-name user)]
      [typography/Text (tr [:roles (:permission/role permission)])]])])
