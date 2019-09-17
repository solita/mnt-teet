(ns teet.navigation.navigation-view
  (:require [reagent.core :as r]
            [teet.routes :as routes]
            [teet.ui.material-ui :refer [AppBar Toolbar Button Chip Avatar IconButton
                                         Drawer TextField InputAdornment FormControl InputLabel Input
                                         List ListItem ListItemText ListItemIcon
                                         Paper Tabs Tab]]
            [teet.ui.icons :as icons]
            [teet.ui.typography :refer [Heading1]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.localization :as localization :refer [tr]]
            [teet.navigation.navigation-controller :as navigation-controller]
            [teet.navigation.navigation-style :as navigation-style]
            [teet.search.search-view :as search-view]
            [herb.core :refer [<class]]
            [taoensso.timbre :as log]
            [teet.common.common-controller :as common-controller]))

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
       [:a {:href "/#/"}
        [:img {:style {:max-width "100%"}
               :src "/img/maanteeametlogo.png"}]]])
    [IconButton {:color :secondary
                 :on-click #(e! (navigation-controller/->ToggleDrawer))}
     (if open?
       [icons/navigation-chevron-left]
       [icons/navigation-chevron-right])]]])

(defn page-listing
  [e! open?]
  [List
   [ListItem {:component "a"
              :href "/#/"
              :align-items "center"
              :button true}
    [ListItemIcon {:style {:display :flex
                           :justify-content :center}}
     [icons/action-list]]
    (when open?
      [ListItemText {:primary (tr [:projects :title])}])]
   [ListItem {:component "a"
              :href "/#/components"
              :align-items "center"
              :button true}
    [ListItemIcon {:style {:display :flex
                           :justify-content :center}}
     [icons/content-archive]]
    (when open?
      [ListItemText {:primary "Components"}])]])

(defn user-info [e! {:keys [user/given-name user/family-name] :as user} label?]
  (let [handle-click! (fn user-clicked [x]
                        (e! (navigation-controller/->GoToLogin)))]
    (if-not user
      [Button {:color :secondary
               :href "/#/login"
               :onClick handle-click!}
       (tr [:login :login])]
      (if label?
        [Chip {:avatar (r/as-element [Avatar [icons/action-face]])
               :label (str given-name " " family-name)
               :href "login"
               :onClick handle-click!}]
        [Avatar
         {:onClick handle-click!}
         [icons/action-face]]))))

(defn drawer-footer
  [e! user open?]
  [:div {:style {:margin-top "auto"
                 :padding "1rem 0"
                 :display :flex
                 :justify-content :center}}
   [user-info e! user open?]])

(defn header
  [e! {:keys [title open? tabs]} user]
  [:<>
   [AppBar {:position "sticky"
            :className (<class navigation-style/appbar-position open?)
            :color :default}


    [Toolbar {:className (<class navigation-style/appbar)}
     (when-not tabs
       [Heading1 title])
     (let [selected-tab (first (keep-indexed
                                (fn [i tab]
                                  (when (:selected? tab)
                                    i))
                                tabs))]
       [Tabs {:value selected-tab
              :indicatorColor "primary"
              :textColor "primary"} ; :onChange doesn't work
        (doall
         (map-indexed
          (fn [i {:keys [title] :as tab}]
            ^{:key i}
            [Tab {:label title :on-click #(e! (common-controller/map->Navigate tab))}])
          tabs))])

     [search-view/quick-search e!]]]

   [Drawer {;:class-name (<class navigation-style/drawer open?)
            :classes {"paperAnchorDockedLeft" (<class navigation-style/drawer open?)}
            :variant "permanent"
            :anchor "left"
            :open open?}
    [drawer-header e! title open?]
    [page-listing e! open?]
    [drawer-footer e! user open?]]])

(defn main-container [navigation-open? content]
  [:main {:class (<class navigation-style/main-container navigation-open?)}
   content])
