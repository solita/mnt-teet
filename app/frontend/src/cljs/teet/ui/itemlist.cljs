(ns teet.ui.itemlist
  (:require [teet.ui.material-ui :refer [Paper Button List ListItem ListItemIcon ListItemSecondaryAction Fab
                                         IconButton TextField Chip Avatar MuiThemeProvider CssBaseline Divider
                                         Checkbox CircularProgress Link]]
            [teet.ui.icons :as icons]
            [teet.ui.progress :as progress]
            [teet.ui.typography :refer [Heading1 Heading2 Heading3 Paragraph Text SectionHeading DataLabel]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.theme.itemlist-theme :as itemlist-theme]
            [herb.core :refer [<class]]))

(defn ListHeading
  [title subtitle]
  [:div {:class (<class itemlist-theme/heading)}
   [Heading2 title]
   (when subtitle
     [DataLabel subtitle])])

(defn ItemList
  [{:keys [title subtitle]} & children]
  [:div
   [ListHeading title subtitle]
   children])

(defn ProgressList
  [titles items]
  (let [success (count (filterv #(= (:status %) :success) items))
        fails (count (filterv #(= (:status %) :fail) items))]
    [ItemList
     titles
     [:div {:style {:display :flex}}
      [progress/circle
       {:radius 30 :stroke 7}
       {:success success :fail fails :total (count items)}]
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
               "-")]
            [SectionHeading (:name item)]
            [ListItemSecondaryAction
             [ListItemIcon
              [icons/navigation-chevron-right]]]]
           [Divider]])]]]]))

(defn LinkList
  [titles items]
  [ItemList
   titles
   [:div {:style {:margin-top "1rem"}}
    [:ul {:style {:list-style "none"
                  :margin 0
                  :padding 0}}
     (for [item items]
       ^{:key (:name item)}
       [:li
        [Link {:href (:link item)}
         (:name item)]])]]])

