(ns teet.navigation.navigation-view
  (:require [reagent.core :as r]
            [teet.routes :as routes]
            [teet.ui.material-ui :refer [AppBar Toolbar Button Chip Avatar IconButton
                                         Drawer Breadcrumbs Link Typography
                                         List ListItem ListItemText ListItemIcon
                                         Divider]]
            [teet.ui.icons :as icons]
            [teet.theme.theme-colors :as theme-colors]
            [teet.localization :as localization :refer [tr]]
            [teet.navigation.navigation-controller :as navigation-controller]
            [teet.navigation.navigation-style :as navigation-style]
            [teet.search.search-view :as search-view]
            [herb.core :as herb :refer [<class]]
            [teet.ui.util :as util]
            [teet.ui.typography :as typography]))

(defn- drawer-header
  [e! open?]
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

(defn- page-listing
  [open?]
  [:<>
   [List
    (when open?
      [ListItem {}
       [typography/Heading2 (tr [:projects :title])]])
    [ListItem {:component "a"
               :href "/#/projects/list"
               :align-items "center"
               :button true}
     [ListItemIcon {:style {:display :flex
                            :justify-content :center}}
      [icons/action-list]]
     (when open?
       [ListItemText {:primary (tr [:projects :list-view])}])]
    [ListItem {:component "a"
               :href "/#/projects/map"
               :align-items "center"
               :button true}
     [ListItemIcon {:style {:display :flex
                            :justify-content :center}}
      [icons/maps-map]]
     (when open?
       [ListItemText {:primary (tr [:projects :map-view])}])]
    [ListItem {} [Divider]]
    [ListItem {:component "a"
               :href "/#/components"
               :align-items "center"
               :button true}
     [ListItemIcon {:style {:display :flex
                            :justify-content :center}}
      [icons/content-archive]]
     (when open?
       [ListItemText {:primary "Components"}])]]])

(defn user-info [e! {:keys [user/given-name user/family-name] :as user} label?]
  (let [handle-click! (fn user-clicked [_]
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
  [e! {:keys [open? breadcrumbs quick-search]} user]
  [:<>
   [AppBar {:position "sticky"
            :className (herb/join (<class navigation-style/appbar)
                                  (<class navigation-style/appbar-position open?))}


    [Toolbar {:className (<class navigation-style/toolbar)}
     [Breadcrumbs {}
      (util/with-keys
        (for [crumb (butlast breadcrumbs)]
          [Link {:href (routes/url-for crumb)}
           (:title crumb)]))
       (when-let [{title :title} (last breadcrumbs)]
         [:span title])]

     [search-view/quick-search e! quick-search]]]

   [Drawer {;:class-name (<class navigation-style/drawer open?)
            :classes {"paperAnchorDockedLeft" (<class navigation-style/drawer open?)}
            :variant "permanent"
            :anchor "left"
            :open open?}
    [drawer-header e! open?]
    [page-listing open?]
    [drawer-footer e! user open?]]])

(defn main-container [navigation-open? content]
  [:main {:class (<class navigation-style/main-container navigation-open?)}
   content])
