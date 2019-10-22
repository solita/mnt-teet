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
  [ListItem {:component "button"
             :align-items "center"
             :button true
             :on-click #(e! (navigation-controller/->ToggleDrawer))
             :classes {:root (<class navigation-style/drawer-list-item-style false)}}
   [ListItemIcon {:style {:display :flex
                          :justify-content :center}}
    (if open?
      [icons/navigation-close]
      [icons/navigation-menu])]
   (when open?
     [ListItemText {:primary "Hide menu"}])]) ;; TODO to localizations

(defn- view-link [{:keys [open? current-page link icon name]}]
  (let [current-page? (= current-page (:page link))]
    [ListItem {:component "a"
              :href (routes/url-for link)
              :align-items "center"
              :button true
              :classes {:root (<class navigation-style/drawer-list-item-style current-page?)}}
    [ListItemIcon {:style {:display :flex
                           :justify-content :center}}
     [icon]]
    (when open?
      [ListItemText {:primary name}])]))

(defn- page-listing
  [e! open? page]
  [List {:class (<class navigation-style/page-listing)}
   [drawer-header e! open?]
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
    [page-listing e! open? page]]])

(defn main-container [navigation-open? content]
  [:main {:class (<class navigation-style/main-container navigation-open?)}
   content])
