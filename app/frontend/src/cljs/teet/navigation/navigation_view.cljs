(ns teet.navigation.navigation-view
  (:require [teet.routes :as routes]
            [teet.ui.select :as select]
            [teet.ui.material-ui :refer [AppBar Toolbar Drawer List ListItem
                                         ListItemText ListItemIcon Link LinearProgress]]
            [teet.ui.icons :as icons]
            [teet.ui.common :as ui-common]
            [teet.localization :as localization :refer [tr]]
            [teet.navigation.navigation-controller :as navigation-controller]
            [teet.navigation.navigation-logo :as navigation-logo]
            [teet.navigation.navigation-style :as navigation-style]
            [teet.search.search-view :as search-view]
            [teet.common.common-controller :as common-controller :refer [when-feature]]
            [herb.core :as herb :refer [<class]]
            [teet.authorization.authorization-check :refer [authorized?]]
            [teet.login.login-controller :as login-controller]
            [teet.notification.notification-view :as notification-view]
            [teet.ui.buttons :as buttons]))


(def uri-quote (fnil js/encodeURIComponent "(nil)"))

(defn language-selector
  []
  [select/select-with-action
   {:container-class (herb/join (<class navigation-style/language-select-container-style)
                                (<class navigation-style/divider-style))
    :label (str (tr [:common :language]))
    :class (<class navigation-style/language-select-style)
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
  [{:user/keys [given-name family-name person-id] :as user}]
  (let [ua-string (.-userAgent js/navigator)
        current-uri (.-URL js/document)
        subject-text (str "TEET Feedback, " (.toLocaleDateString (js/Date.))
                          ", " given-name " " family-name)
        body-text (str "Session info:\n"
                       "Browser type string: " ua-string "\n"
                       "Address in TEET: " current-uri "\n"
                       "TEET branch: " (aget js/window "teet_branch") "\n"
                       "TEET git hash: " (aget js/window "teet_githash") "\n"
                       "User: " (if user (str given-name " " family-name
                                              " (person code:" person-id ")\n\n")
                                         "User not logged in"))]
    [:div {:class (<class navigation-style/feedback-container-style)}
     [Link {:class (<class navigation-style/feedback-style)
            :href (str "mailto:teet-feedback@mnt.ee"
                       "?Subject=" (uri-quote subject-text)
                       "&body=" (uri-quote body-text))}
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
                           :data (:user/email user)}])

(defn logout [e!]
  [:div {:class (herb/join (<class navigation-style/logout-container-style)
                           (<class navigation-style/divider-style))}
   [Link {:class (<class navigation-style/logout-style)
          :href "/#/login"
          :on-click (e! login-controller/->Logout)}
    (tr [:common :log-out])]])

(defn navigation-header-links
  ([user e!]
   [navigation-header-links user e! true])
  ([user e! logged-in?]
   [:div {:style {:display :flex
                  :justify-content :flex-end}}
    [feedback-link user]
    (when logged-in?
      [notification-view/notifications e!])
    [language-selector]
    (when logged-in?
      (when-feature :my-role-display
                    [user-info user]))
    (if logged-in?
      [logout e!]
      [buttons/button-primary {:style {:margin "0 1rem"}
                               :href "/oauth2/request"}
       (tr [:login :login])])]))

(defn header
  [e! {:keys [open? page quick-search]} user]
  [:<>
   [AppBar {:position "sticky"
            :className (herb/join (<class navigation-style/appbar)
                                  (<class navigation-style/appbar-position open?))}
    [Toolbar {:className (herb/join (<class navigation-style/toolbar))}
     [:div {:class (<class navigation-style/logo-style)}
      [navigation-logo/maanteeamet-logo false]]
     [search-view/quick-search e! quick-search]
     [navigation-header-links user e!]]]

   [Drawer {;:class-name (<class navigation-style/drawer open?)
            :classes {"paperAnchorDockedLeft" (<class navigation-style/drawer open?)}
            :variant "permanent"
            :anchor "left"
            :open open?}
    [page-listing e! open? user page]]])

(defn login-header
  [e!]
  [AppBar {:position "sticky"
           :className (herb/join (<class navigation-style/appbar))}
   [Toolbar {:className (herb/join (<class navigation-style/toolbar))}
    [:div {:class (<class navigation-style/logo-style)}
     [navigation-logo/maanteeamet-logo true]]
    [navigation-header-links nil e! false]]])

(defn main-container [navigation-open? content]
  [:main {:class (<class navigation-style/main-container navigation-open?)}
   (when (common-controller/in-flight-requests?)
     [:div {:style {:position :absolute :width "100%"
                    :overflow :hidden}}
      [LinearProgress]])
   content])
