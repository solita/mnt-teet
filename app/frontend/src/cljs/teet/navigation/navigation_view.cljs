(ns teet.navigation.navigation-view
  (:require [reagent.core :as r]
            [teet.routes :as routes]
            [teet.ui.select :as select]
            [teet.ui.material-ui :refer [AppBar Toolbar Drawer List ListItem
                                         ListItemText ListItemIcon Link LinearProgress
                                         Badge IconButton Menu MenuItem]]
            [teet.ui.icons :as icons]
            [teet.ui.common :as ui-common]
            [teet.ui.util :refer [mapc]]
            [teet.localization :as localization :refer [tr tr-enum]]
            [teet.navigation.navigation-controller :as navigation-controller]
            [teet.navigation.navigation-logo :as navigation-logo]
            [teet.navigation.navigation-style :as navigation-style]
            [teet.search.search-view :as search-view]
            [teet.common.common-controller :as common-controller :refer [when-feature]]
            [herb.core :as herb :refer [<class]]
            [teet.authorization.authorization-check :refer [authorized?]]
            [teet.login.login-controller :as login-controller]
            [teet.ui.query :as query]))

(def entity-quote (fnil js/escape "(nil)"))

(def uri-quote (fnil js/encodeURIComponent "(nil)"))

(defn language-selector
  []
  [select/select-with-action {:container-class (herb/join (<class navigation-style/language-select-container-style)
                                                          (<class navigation-style/divider-style))
                              :label (str (tr [:common :language]))
                              :select-class (<class navigation-style/language-select-style)
                              :id "language-select"
                              :name "language"
                              :value (case @localization/selected-language
                                       :et
                                       {:value "et" :label (get localization/language-names "et")}
                                       :en
                                       {:value "en" :label (get localization/language-names "en")})
                              :items [{:value "et" :label (get localization/language-names "et")}
                                      {:value "en" :label (get localization/language-names "en")}]
                              :on-change (fn [val]
                                           (localization/load-language!
                                             (keyword (:value val))
                                             (fn [language _]
                                               (reset! localization/selected-language
                                                       language))))}])


(defn feedback-link
  []
  (let [ua-string (.-userAgent js/navigator)
        current-uri (.-URL js/document)
        body-text (str "Session info:\n"
                       "Browser type string: " ua-string "\n"
                       "Address in TEET: " current-uri "\n\n")]
    [:div {:class (<class navigation-style/feedback-container-style)}
     [Link {:class (<class navigation-style/feedback-style)
            :href (str "mailto:teet-feedback@mnt.ee?Subject=TEET Feedback&body="
                       (-> body-text
                           entity-quote))}
      (tr [:common :send-feedback])]]))

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
     [ListItemText {:primary (tr [:common :hide-menu])}])])

(defn- view-link [{:keys [open? current-page link icon name]}]
  (let [current-page? (= current-page (:page link))]
    [ListItem {:component "a"
               :href (routes/url-for link)
               :align-items "center"
               :button true
               :disable-ripple true
               :classes {:root (<class navigation-style/drawer-list-item-style current-page?)}}
     [ListItemIcon {:style {:display :flex
                            :justify-content :center}}
      [icon]]
     (when open?
       [ListItemText {:primary name}])]))

(defn- page-listing
  [e! open? user page]
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
   (when-feature :road-information-view
     [view-link {:open? open?
                 :current-page page
                 :link {:page :road
                        :query {:road 1
                                :carriageway 1
                                :start-m 100
                                :end-m 17000}}
                 :icon icons/maps-my-location
                 :name "Road location"}])
   (when-feature :component-view
     [view-link {:open? open?
                 :current-page page
                 :link {:page :components}
                 :icon icons/content-archive
                 :name "Components"}])
   (when (authorized? user :admin/add-user)
     [view-link {:open? open?
                 :current-page page
                 :link {:page :admin}
                 :icon icons/action-settings
                 :name (tr [:admin :title])}])])



(defn user-info [user]
  [ui-common/labeled-data {:class (<class navigation-style/divider-style)
                           :label (tr [:user :role])
                           :data (:user/family-name user)}])

(defn logout [e!]
  [:div {:class (herb/join (<class navigation-style/logout-container-style)
                           (<class navigation-style/divider-style))}
   [Link {:class (<class navigation-style/logout-style)
          :href "/#/login"
          :on-click (e! login-controller/->Logout)}
    (tr [:common :log-out])]])

(defn- notifications* [e! notifications]
  (r/with-let [selected-item (r/atom nil)
               handle-click! (fn [event]
                               (reset! selected-item (.-currentTarget event)))
               handle-close! (fn []
                               (reset! selected-item nil))]
    [:div {:class (herb/join (<class navigation-style/notification-style)
                             (<class navigation-style/divider-style))}
     [Badge {:badge-content (count notifications)
             :color "error"}
      [IconButton
       {:color "primary"
        :size "small"
        :component "span"
        :on-click handle-click!}
       [icons/social-notifications {:color "primary"}]]]
     [Menu {:anchor-el @selected-item
            :open (boolean @selected-item)
            :on-close handle-close!}
      (mapc (fn [{:notification/keys [type target]}]
              [MenuItem {:on-click :D}
               (tr-enum type)])
            notifications)]]))

(defn notifications [e!]
  [query/query {:e! e!
                :query :notification/unread-notifications
                :args {}
                :simple-view [notifications* e!]
                :loading-state []
                :poll-seconds 300}])

(defn navigation-header-links
  [user e!]
  [:div {:style {:display :flex
                 :justify-content :flex-end}}
   [feedback-link]
   [notifications e!]
   [language-selector]
   (when-feature :my-role-display
     [user-info user])
   [logout e!]])

(defn header
  [e! {:keys [open? page quick-search]} user]
  [:<>
   [AppBar {:position "sticky"
            :className (herb/join (<class navigation-style/appbar)
                                  (<class navigation-style/appbar-position open?))}
    [Toolbar {:className (herb/join (<class navigation-style/toolbar))}
     [:div {:class (<class navigation-style/logo-style)}
      navigation-logo/maanteeamet-logo]
     [search-view/quick-search e! quick-search]
     [navigation-header-links user e!]]]

   [Drawer {;:class-name (<class navigation-style/drawer open?)
            :classes {"paperAnchorDockedLeft" (<class navigation-style/drawer open?)}
            :variant "permanent"
            :anchor "left"
            :open open?}
    [page-listing e! open? user page]]])

(defn main-container [navigation-open? content]
  [:main {:class (<class navigation-style/main-container navigation-open?)}
   (when (common-controller/in-flight-requests?)
     [:div {:style {:position :absolute :width "100%"
                    :overflow :hidden}}
      [LinearProgress]])
   content])
