(ns teet.login.login-view
  "Login page"
  (:require [herb.core :refer [<class]]
            [teet.login.login-controller :as login-controller]
            [teet.ui.material-ui :refer [Link Divider]]
            [teet.ui.text-field :refer [TextField]]
            [teet.navigation.navigation-logo :as navigation-logo]
            [teet.ui.icons :as icons]
            [teet.localization :refer [tr]]
            [teet.ui.buttons :as buttons]
            [teet.login.login-styles :as login-styles]
            [teet.log :as log]))

(defn login-logo
  []
  [:span {:class (<class login-styles/logo-container)}
   [navigation-logo/logo-shield {:height 65 :width 58}]
   [:span {:class (<class login-styles/logo-text)}
    "MAANTEEAMET"]])

(defn- dummy-login? []
  (let [host js/window.location.hostname]
    (or (= host "localhost")
        (= host "dev-teet.solitacloud.fi"))))

(defn login-page-heading-style
  []
  {:font-size "64px"
   :line-height "80px"
   :font-weight 300
   :color "#fff"
   :text-shadow "0px 4px 4px rgba(0, 0, 0, 0.25)"
   :margin 0})

(defn login-page [e! _]
  (e! (login-controller/->CheckSessionToken))
  (fn [e! {login :login}]
    [:main {:class (<class login-styles/login-background)}
     (if (dummy-login?)
       [:section {:class (<class login-styles/login-container)}
        [login-logo]
        [Link {:href "/oauth2/request"
               :class (<class login-styles/tara-login)}
         "TARA login"]
        [:<>
         [Divider]
         [:div {:class (<class login-styles/user-list)}
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
            (for [{:user/keys [email given-name family-name organization] :as user} login-controller/mock-users]
              ^{:key email}
              [buttons/white-button-with-icon {:icon icons/navigation-arrow-forward
                                               :on-click #(do
                                                            (e! (login-controller/->Login user))
                                                            (log/info "Start login: " user))}
               (str "Login as " given-name " " family-name " (" organization ")")]))]]]
       [:div {:style {:margin "8rem"}}
        [:h1 {:class (<class login-page-heading-style)}
         (tr [:login :greetings-text])]])]))
