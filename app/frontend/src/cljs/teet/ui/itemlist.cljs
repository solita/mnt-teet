(ns teet.ui.itemlist
  (:require [teet.ui.material-ui :refer [Paper Button List ListItem ListItemIcon ListItemSecondaryAction Fab
                                         IconButton TextField Chip Avatar MuiThemeProvider CssBaseline Divider
                                         Checkbox CircularProgress Link]]
            [teet.ui.icons :as icons]
            [teet.ui.progress :as progress]
            [teet.ui.typography :refer [Heading1 Heading2 Heading3 Paragraph Text SectionHeading DataLabel]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.theme.itemlist-theme :as itemlist-theme]
            [herb.core :refer [<class]]
            [teet.ui.util :as util]
            [taoensso.timbre :as log]))

(defn ListHeading
  [{:keys [title subtitle action variant]
    :or {variant :primary}}]
  [:div {:class (<class itemlist-theme/heading variant)}
   (case variant
     :primary [Heading2 title]
     :secondary [Heading3 title])
   (when action
     [:div {:class (<class itemlist-theme/heading-action)}
      action])
   (when subtitle
     [DataLabel subtitle])])

(defn ItemList
  [{:keys [title subtitle variant class]
    :or {variant :primary}} & children]
  [:div (when class
          {:class class})
   [ListHeading (merge
                  {:title title
                   :subtitle subtitle
                   :variant variant})]
   (util/with-keys children)])

(defn ProgressList
  [titles items]
  (let [success (count (filterv #(= (:status %) :success) items))
        fails (count (filterv #(= (:status %) :fail) items))]
    [ItemList
     titles
     [:div {:style {:display :flex}}
      [:div {:style {:flex 1}}
       [List
        {:dense false}
        (for [item items]
          ^{:key (:name item)}
          [:<>
           [ListItem {:button true
                      :component "a"
                      :href (:link item)}
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
                     :margin "0 0.5rem"}}
       [progress/circle
        {:radius 70 :stroke 9}
        {:success success :fail fails :total (count items)}]]]]))

(defn LinkList
  [titles items on-click-fn]
  [ItemList
   titles
   [:div {:style {:margin-top "1rem"}}
    [:ul {:style {:list-style "none"
                  :margin 0
                  :padding 0}}
     (for [item items]
       ^{:key (:name item)}
       [:li
        [Link {:href (:link item)
               :onClick #(on-click-fn item)}
         (:name item)]])]]])

(defn DocumentList [{:keys [documents download-fn]}]
  [ItemList
   {:title "Documents"}
   (if (empty? documents)
     [:div "No documents"]
     (doall
       (for [{id :db/id
              :document/keys [name size type]
              progress :progress
              :as doc} documents]
         ^{:key id}
         [:div
          ;; FIXME: make a nice document UI
          [:br]
          [:div [:a {:href (download-fn doc)} name]
           " (type: " type ", size: " size ") "
           (when progress
             [CircularProgress])
           ]])))])

(defn Item [{:keys [label]} value]
  [:div
   [:b (str label ": ")]
   value])
