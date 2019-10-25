(ns teet.login.login-view
  "Login page"
  (:require [herb.core :refer [<class]]
            [teet.login.login-controller :as login-controller]
            [teet.ui.material-ui :refer [Container Card CardHeader CardContent
                                         Button Typography TextField ButtonBase]]
            [teet.ui.layout :as layout]
            [teet.navigation.navigation-logo :as navigation-logo]
            [teet.ui.itemlist]
            [teet.ui.icons :as icons]
            [teet.ui.buttons :as buttons]
            [teet.localization :refer [tr]]
            [teet.localization :as localization]
            [teet.login.login-styles :as login-styles]
            [teet.ui.select :as select]
            [taoensso.timbre :as log]
            [reagent.core :as r]
            [teet.ui.typography :as typography]))

(defn login-logo
  []
  [:span {:class (<class login-styles/logo-container)}
   [navigation-logo/logo-shield {:height 65 :width 58}]
   [:span {:class (<class login-styles/logo-text)}
    "MAANTEEAMET"]])

(defn login-page [e! {login :login}]
  [:main {:class (<class login-styles/login-background)}
   [:section {:class (<class login-styles/login-container)}
    [login-logo]
    [:div {:class (<class login-styles/user-list)}
     #_[select/outlined-select {:label "Language"
                                :id "language-select"
                                :name "Language"
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
                                                   language))))}]
     [TextField {:label "Password"
                 :id "password-textfield"
                 :type "password"
                 :variant :outlined
                 :style {:margin-bottom "2rem"}
                 :value (get login :password "")
                 :on-change (fn pw-on-change! [e]
                              (e! (login-controller/->SetPassword (-> e .-target .-value)))
                              #_(log/info "password changed"))}]
     (doall
       (for [{:user/keys [email family-name] :as user} login-controller/mock-users]
         ^{:key email}
         [buttons/white-button-with-icon {:icon icons/navigation-arrow-forward
                                          :on-click #(do
                                                       (e! (login-controller/->Login user))
                                                       (log/info "Start login: " user))}
          (str "Login as " family-name)]))]]])
