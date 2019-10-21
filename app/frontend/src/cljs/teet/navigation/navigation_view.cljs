(ns teet.navigation.navigation-view
  (:require [reagent.core :as r]
            [teet.routes :as routes]
            [teet.ui.material-ui :refer [AppBar Toolbar Button Chip Avatar IconButton
                                         Drawer Breadcrumbs Link Typography
                                         List ListItem ListItemText ListItemIcon
                                         Divider]]
            [teet.ui.icons :as icons]
            [teet.localization :as localization :refer [tr]]
            [teet.navigation.navigation-controller :as navigation-controller]
            [teet.navigation.navigation-style :as navigation-style]
            [teet.search.search-view :as search-view]
            [herb.core :as herb :refer [<class]]
            [teet.ui.util :as util]
            [teet.ui.typography :as typography]))

(defn- drawer-header
  [e! open?]
  [:div {:class (<class navigation-style/maanteeamet-logo)}
   (when open?
     [:div
      [:a {:href "/#/"}
       [:img {:src "/img/maanteeametlogo.png"}]]])
   [IconButton {:color :secondary
                :on-click #(e! (navigation-controller/->ToggleDrawer))}
    (if open?
      [icons/navigation-chevron-left]
      [icons/navigation-chevron-right])]])

(defn- view-link [{:keys [open? current-page link icon name]}]
  [ListItem {:component "a"
             :href (routes/url-for link)
             :align-items "center"
             :button true
             :classes {:root (<class navigation-style/drawer-list-item-style)}}
   [ListItemIcon {:style {:display :flex
                          :justify-content :center}}
    [icon {:classes {:root (<class navigation-style/drawer-link-style (= current-page (:page link)))}}]]
   (when open?
     [ListItemText {:classes {:primary (<class navigation-style/drawer-link-style (= current-page (:page link)))}
                    :primary
                    name}])])

(defn- page-listing
  [open? page]
  [List {:class (<class navigation-style/page-listing)}
   [view-link {:open? open?
               :current-page page
               :link {:page :root}
               :icon icons/maps-map
               :name (tr [:projects :map-view])}]
   [view-link {:open? open?
               :current-page page
               :link {:page :projects-list}
               :icon icons/action-list
               :name (tr [:projects :list-view])}]
   [view-link {:open? open?
               :current-page page
               :link {:page :road
                      :query {:road 1
                              :carriageway 1
                              :start-m 100
                              :end-m 17000}}
               :icon icons/maps-my-location
               :name "Road location"}]
   [view-link {:open? open?
               :current-page page
               :link {:page :components}
               :icon icons/content-archive
               :name "Components"}]])

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
  [:div {:class (<class navigation-style/drawer-footer)}
   [user-info e! user open?]])

(defn header
  [e! {:keys [open? page breadcrumbs quick-search]} user]
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
    [page-listing open? page]
    [drawer-footer e! user open?]]])

(defn main-container [navigation-open? content]
  [:main {:class (<class navigation-style/main-container navigation-open?)}
   content])
