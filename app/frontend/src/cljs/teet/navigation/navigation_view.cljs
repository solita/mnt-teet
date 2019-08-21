(ns teet.navigation.navigation-view
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [AppBar Toolbar Button Typography Chip Avatar IconButton Drawer]]
            [teet.ui.icons :as icons]
            [teet.localization :as localization :refer [tr]]
            [teet.navigation.navigation-controller :as navigation-controller]
            [teet.navigation.navigation-style :as navigation-style]
            [herb.core :refer [<class]]))

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
  [:<>
   [AppBar {:position "static"
            :className (<class navigation-style/appbar-position open?)}
    [Toolbar
     [user-info user true]]]

   [Drawer {;:class-name (<class navigation-style/drawer open?)
            :classes {"paperAnchorDockedLeft" (<class navigation-style/drawer open?)}
            :variant "permanent"
            :anchor "left"
            :open open?}
    [:div {:style {:display "flex"
                   :align-items "center"
                   :justify-content "space-between"
                   :flex-direction "column"}}
     [:div {:style {:padding "1rem"
                    :display "flex"
                    :align-items "center"
                    :justify-content "space-between"}}
      (when open?
        [Typography {:variant "h6"}
         [:div {:style {:display "inline-block"}}
          [:img {:src "/img/teet-logo.png"}]
          [:div {:style {:display "inline-block"
                         :position "relative"
                         :top -6 :left 5}}
           title]]])
      [IconButton {:color "primary"
                   :on-click #(e! (navigation-controller/->ToggleDrawer))}
       (if open?
         [icons/navigation-chevron-left]
         [icons/navigation-chevron-right])]]]]])

(defn main-container [navigation-open? content]
  [:main {:class (<class navigation-style/main-container navigation-open?)}
   content])
