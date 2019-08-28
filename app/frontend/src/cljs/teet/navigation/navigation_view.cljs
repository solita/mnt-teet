(ns teet.navigation.navigation-view
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [AppBar Toolbar Button Typography Chip Avatar IconButton
                                         Drawer TextField InputAdornment FormControl InputLabel Input
                                         List ListItem ListItemText ListItemIcon]]
            [teet.ui.icons :as icons]
            [teet.theme.theme-colors :as theme-colors]
            [teet.localization :as localization :refer [tr]]
            [teet.navigation.navigation-controller :as navigation-controller]
            [teet.navigation.navigation-style :as navigation-style]
            [teet.search.search-view :as search-view]
            [herb.core :refer [<class]]))

(defn user-info [{:keys [given-name family-name] :as user} label?]
  (if-not user
    [Button {:color :secondary
             :href "/oauth2/request"}
     (tr [:login :login])]
    (if label?
      [Chip {:avatar (r/as-element [Avatar [icons/action-face]])
             :label (str given-name " " family-name)}]
      [Avatar
       [icons/action-face]])))

(defn drawer-header
  [e! title open?]
  [:div {:style {:display "flex"
                 :align-items "center"
                 :justify-content "space-between"
                 :flex-direction "column"
                 :margin-bottom "50px"
                 :background-color theme-colors/white}}
   [:div {:style {:display "flex"
                  :height "90px"
                  :align-items "center"
                  :justify-content "space-between"}}
    (when open?
      [:div {:style {:display :flex}}
       [:img {:style {:max-width "100%"}
              :src "/img/maanteeametlogo.png"}]])
    [IconButton {:color :secondary
                 :on-click #(e! (navigation-controller/->ToggleDrawer))}
     (if open?
       [icons/navigation-chevron-left]
       [icons/navigation-chevron-right])]]])

(defn drawer-footer
  [user open?]
  [:div {:style {:margin-top "auto"
                 :padding "1rem 0"
                 :display :flex
                 :justify-content :center}}
   [user-info user open?]])

(defn page-listing
  [e! open?]
  [:div
   [List
    [ListItem {:alignItems "center"
               :button true}
     [ListItemIcon
      [icons/action-list {:fontSize :large}]]
     (when open?
       [ListItemText {:primary "Projects"}])]]])

(defn header
  [e! {:keys [title open?]} user]
  [:<>
   [AppBar {:position "sticky"
            :className (<class navigation-style/appbar-position open?)
            :color :default}
    [Toolbar {:className (<class navigation-style/appbar)}
     [Typography {:variant "h6"}
      title]
     [search-view/quick-search e!]]]

   [Drawer {;:class-name (<class navigation-style/drawer open?)
            :classes {"paperAnchorDockedLeft" (<class navigation-style/drawer open?)}
            :variant "permanent"
            :anchor "left"
            :open open?}
    [drawer-header e! title open?]
    [page-listing e! open?]
    [drawer-footer user open?]]])

(defn main-container [navigation-open? content]
  [:main {:class (<class navigation-style/main-container navigation-open?)}
   content])
