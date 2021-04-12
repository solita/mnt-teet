(ns teet.navigation.navigation-view
  (:require [reagent.core :as r]
            [herb.core :as herb :refer [<class]]
            [teet.authorization.authorization-check :refer [authorized?]]
            [teet.common.common-controller :as common-controller :refer [when-feature]]
            [teet.localization :as localization :refer [tr]]
            [teet.login.login-controller :as login-controller]
            [teet.navigation.navigation-controller :as navigation-controller]
            [teet.navigation.navigation-logo :as navigation-logo]
            [teet.navigation.navigation-style :as navigation-style]
            [teet.notification.notification-view :as notification-view]
            [teet.routes :as routes]
            [teet.search.search-view :as search-view]
            [teet.common.responsivity-styles :as responsivity-styles]
            [teet.ui.buttons :as buttons]
            [teet.ui.common :as common]
            [teet.ui.icons :as icons]
            [teet.ui.material-ui :refer [AppBar Toolbar Drawer List ListItem IconButton Portal
                                         ListItemText ClickAwayListener ClickAwayListener ListItemIcon LinearProgress Collapse]]
            [teet.ui.select :as select]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.typography :as typography]
            [teet.user.user-model :as user-model]
            [teet.ui.events :as events]
            [teet.common.common-styles :as common-styles]))


(def uri-quote (fnil js/encodeURIComponent "(nil)"))

(defn feedback-link
  [{:user/keys [given-name family-name person-id] :as user} url]
  (let [ua-string (.-userAgent js/navigator)
        subject-text (str "TEET Feedback, " (.toLocaleDateString (js/Date.))
                          ", " given-name " " family-name)
        body-text (str "Session info:\n"
                       "Browser type string: " ua-string "\n"
                       "Address in TEET: " url "\n"
                       "TEET branch: " (aget js/window "teet_branch") "\n"
                       "TEET git hash: " (aget js/window "teet_githash") "\n"
                       "User: " (if user
                                  (str given-name " " family-name
                                       " (person code:" person-id ")\n\n")
                                  "User not logged in"))]
    [:div {:class (<class navigation-style/feedback-container-style)}
     [common/Link {:class (<class navigation-style/feedback-style)
                   :href (str "mailto:teet-feedback@transpordiamet.ee"
                              "?Subject=" (uri-quote subject-text)
                              "&body=" (uri-quote body-text))}
      [icons/action-feedback-outlined {:color :primary}]
      [:span {:class (<class responsivity-styles/desktop-only-style
                             {:margin-left "0.5rem"
                              :display :inline-block}
                             {:display :none})}
       (tr [:common :send-feedback])]]]))

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
               :class (str "left-menu-" (cljs.core/name (:page link)))
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
               :icon icons/action-assignment
               :name (tr [:dashboard :title])}]
   [view-link {:open? open?
               :current-page page
               :link {:page :projects}
               :icon icons/maps-map
               :name (tr [:projects :map-view])}]
   [view-link {:open? open?
               :current-page page
               :link {:page :projects-list}
               :icon icons/action-list
               :name (tr [:projects :list-view])}]
   (when-feature :asset-db
     [view-link {:open? open?
                 :current-page page
                 :link {:page :asset-type-library}
                 :icon icons/editor-schema
                 :name (tr [:asset :type-library :link])}])
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

(defmulti header-extra-panel (fn [_e! _user opts extra-panel]
                               extra-panel))

(defmethod header-extra-panel :default
  [_ _ _ _]
  "")

(defn language-selector
  []
  [select/select-with-action
   {:container-class (<class navigation-style/language-select-container-style)
    :class (<class navigation-style/language-select-style)
    :empty-selection-label true
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

(defn language-options
  []
  (r/with-let [change-lan-fn (fn [val]
                               (localization/load-language!
                                 (keyword val)
                                 (fn [language _]
                                   (reset! localization/selected-language
                                           language))))]
    [:div {:class (<class common-styles/flex-row)}
     [:div {:style {:padding-right "0.5rem"
                    :border-right (str "1px solid " theme-colors/border-dark)}}
      (if (= @localization/selected-language :et)
        [typography/TextBold "EST"]
        [buttons/link-button {:id "ET"
                              :on-click #(change-lan-fn "et")}
         "EST"])]
     [:div {:style {:padding-left "0.5rem"}}
      (if (= @localization/selected-language :en)
        [typography/TextBold "ENG"]
        [buttons/link-button {:id "EN"
                              :on-click #(change-lan-fn "en")}
         "ENG"])]]))

(defmethod header-extra-panel :search
  [e! user opts _]
  [:div {:style {:min-width "300px"}}
   [:div {:class (<class navigation-style/extran-nav-heading-element-style)}
    [search-view/quick-search e! (:quick-search opts)]]])

(defmethod header-extra-panel :account-control
  [e! user _ _]
  [:div {:style {:min-width "300px"}}
   [:div {:class (<class navigation-style/extran-nav-heading-element-style)}
    [typography/Text2
     (user-model/user-name user)]]
   [:div {:class (<class navigation-style/extra-nav-element-style)}
    [common/Link {:href "#/account"
                  :on-click (e! navigation-controller/->CloseExtraPanel)
                  :class (<class common-styles/flex-row-center)
                  :id "account-page-link"}
     [icons/social-person-outlined {:color :primary
                                    :style {:margin-right "1rem"}}]
     (tr [:account :my-account])]]
   [:div {:class (<class navigation-style/extra-nav-element-style)}
    [:div {:class (<class common-styles/flex-row-center)}
     [icons/action-language {:color :primary
                             :style {:margin-right "1rem"}}]
     [language-options]]]
   [:div {:class (<class navigation-style/extra-nav-element-style)}
    [:div {:class (<class common-styles/flex-row-center)}
     [common/Link {:href "/#/login"
                   :on-click (e! login-controller/->Logout)
                   :class (<class common-styles/flex-align-center)}
      [icons/action-logout {:color :primary
                            :style {:margin-right "1rem"}}]
      (tr [:account :logout])]]]])


(defn user-info [user]
  [common/labeled-data {:class (<class navigation-style/divider-style)
                           :label (tr [:user :role])
                           :data (:user/email user)}])

(defn open-account-navigation
  [e!]
  [:div {:class (<class common-styles/flex-row-center)}
   [IconButton {:size :small
                :id "open-account-navigation"
                :on-click #(e! (navigation-controller/->ToggleExtraPanel :account-control))}
    [icons/social-person-outlined {:color :primary}]]])

(defn login-button
  []
  [:<>
   [buttons/large-button-primary {:class (<class
                                     responsivity-styles/visible-desktop-only)
                            :style {:margin "0 0 0 2rem"}
                            :href  "/oauth2/request"}
    (tr [:login :login])]
   [buttons/stand-alone-icon-button {:id "mobile-login-button-n"
                                     :class [(<class responsivity-styles/visible-mobile-only) (<class navigation-style/divider-style)]
                                     :icon [icons/social-person-outlined {:color :primary}]}]])

(defn navigation-header-links
  ([e! user url]
   [navigation-header-links e! user url true])
  ([e! user url logged-in?]
   [:div {:style {:display :flex
                  :justify-content :flex-end}}
    (when logged-in?
      [:<>
       [IconButton {:size :small
                    :id "open-mobile-search"
                    :class [(<class responsivity-styles/visible-mobile-only) (<class navigation-style/divider-style)]
                    :on-click #(e! (navigation-controller/->ToggleExtraPanel :search))}
        [icons/action-search {:color :primary}]]
       [notification-view/notifications e!]])

    [feedback-link user url]
    (when (not logged-in?)
      [:div {:class (<class navigation-style/divider-style)
             :style {:display :flex
                     :align-items :center
                     :padding-right "0.8rem"}}
       [icons/action-language {:style {:color theme-colors/primary
                                       :padding-right "0.5rem" :width "auto"}}]
       [language-selector]])
    (if logged-in?
      [open-account-navigation e!]
      [login-button])]))

(defn header-extra-panel-container
  [e! user quick-search extra-panel extra-panel-open?]
  [:div {:style {:align-self :flex-end}}
   [Collapse {:in (boolean extra-panel-open?)}
    [:div {:class (<class navigation-style/extra-nav-style)}
     [header-extra-panel e! user {:quick-search quick-search} extra-panel]]]])

(defn header
  [e! {:keys [open? page quick-search url extra-panel extra-panel-open?]} user]
  [:<>
   [ClickAwayListener {:on-click-away #(e! (navigation-controller/->CloseExtraPanel))}
    [AppBar {:position "sticky"
             :className (herb/join (<class navigation-style/appbar)
                                   (<class navigation-style/appbar-position open?))}


     [Toolbar {:className (herb/join (<class navigation-style/toolbar))}
      [IconButton {:on-click #(e! (navigation-controller/->ToggleDrawer))
                   :class (<class responsivity-styles/mobile-navigation-button)}
       [icons/navigation-menu]]
      [:div {:class (<class navigation-style/logo-style)}
       [navigation-logo/maanteeamet-logo false]]
      [:div {:class (<class responsivity-styles/desktop-only-style
                            {:position :relative
                             :flex-grow 1
                             :flex-basis "400px"
                             :display :block}
                            {:display :none})}
       [search-view/quick-search e! quick-search]]
      [navigation-header-links e! user url]]
     [header-extra-panel-container e! user quick-search extra-panel extra-panel-open?]]]

   (if (responsivity-styles/mobile?)
     [Drawer {:classes {"paperAnchorDockedLeft"
                        (<class navigation-style/mobile-drawer open?)}
              :variant "temporary"
              :on-close (e! navigation-controller/->CloseDrawer)
              :anchor "left"
              :open open?
              :disablePortal true}
      [page-listing e! open? user page]]
     [Drawer {:classes {"paperAnchorDockedLeft"
                        (<class navigation-style/desktop-drawer open?)}
              :variant "permanent"
              :on-close (e! navigation-controller/->CloseDrawer)
              :anchor "left"
              :open open?}
      [page-listing e! open? user page]])])

(defn login-header
  [e! {:keys [url] :as _app}]
  [AppBar {:position "sticky"
           :className (herb/join (<class navigation-style/appbar))}
   [Toolbar {:className (herb/join (<class navigation-style/toolbar))}
    [:div {:class (<class navigation-style/logo-style)}
     [navigation-logo/maanteeamet-logo true]]
    [navigation-header-links e! nil url false]]])

(defn main-container [navigation-open? content]
  [:main {:class (<class navigation-style/main-container navigation-open?)}
   (when (common-controller/in-flight-requests?)
     [:div {:style {:position :absolute :width "100%"
                    :overflow :hidden}}
      [LinearProgress]])
   content])
