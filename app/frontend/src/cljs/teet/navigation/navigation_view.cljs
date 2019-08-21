(ns teet.navigation.navigation-view
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [AppBar Button CardHeader TextField Typography Chip Avatar IconButton Drawer Divider Fab]]
            [teet.ui.icons :as icons]
            [teet.localization :as localization :refer [tr]]
            [teet.navigation.navigation-controller :as navigation-controller]))

(defn user-info [{:keys [given-name family-name] :as user} label?]
  (if-not user
    [Button {:color "primary"
             :href "/oauth2/request"}
     (tr [:login :login])]
    (if label?
      [Chip {:avatar (r/as-element [Avatar [icons/action-face]])
             :label (str given-name " " family-name)}]
      [Avatar
       [icons/action-face]])))

(defn header
  [e! {:keys [title open?]} user]
  [:nav
   [Drawer {:variant "permanent"
            :anchor "left"
            :open open?
            :style {:width (if open?
                             "200px"
                             "80px")
                    :flex-shrink 0
                    :box-sizing "border-box"
                    :padding "0.25rem"
                    :white-space "nowrap"}}
    [:div {:style {:display "flex"
                   :align-items "center"
                   :justify-content "space-between"
                   :flex-direction "column"}}
     [:div {:style {:padding "1rem"
                    :display "flex"
                    :align-items "center"
                    :justify-content "space-between"}}
      (when open?
        [Typography (merge {:variant "h6"})
         title])
      [IconButton {:color "primary"
                   :on-click #(e! (navigation-controller/->ToggleDrawer))}
       (if open?
         [icons/navigation-chevron-left]
         [icons/navigation-chevron-right])]]
     [:<>
      [user-info user open?]]]]])

