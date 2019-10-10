(ns teet.login.login-view
  "Login page"
  (:require [teet.login.login-controller :as login-controller]
            [teet.ui.material-ui :refer [Container Card CardHeader CardContent
                                         Button Typography TextField]]
            [teet.ui.layout :as layout]
            [teet.ui.itemlist]
            [teet.localization :as localization]
            [teet.ui.select :as select]
            [taoensso.timbre :as log]
            [reagent.core :as r]
            [teet.ui.typography :as typography]))

(defn login-page [e! {login :login}]
  [Container {:maxWidth "sm"}
   [typography/Heading1 "Maanteeamet TEET"]
   [layout/column {:content-style {:padding-bottom "2em"}}
    [typography/Heading2 "Select language"]
    [select/outlined-select {:label "Language"
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
    [typography/Heading2 "Login with demo user"]

    [TextField {:label "Password"
                :id "password-textfield"
                :value (get login :password "")
                :on-change (fn pw-on-change! [e]
                              (e! (login-controller/->SetPassword (-> e .-target .-value)))
                              #_(log/info "password changed"))}]
    (doall
     (for [{:user/keys [given-name family-name organization email] :as user} login-controller/mock-users]
       ^{:key email}
       [:span
        [Card
         [CardHeader {:title (str given-name " " family-name)
                      :action (r/as-element
                               [Button {:color "primary"
                                        :on-click #(do
                                                     (e! (login-controller/->StartLoginAttempt user))
                                                     (log/info "Start login: " user))}
                                "Login"])}]
         [CardContent
          [Typography
           "Name: " given-name " " family-name [:br]
           "Organization: " organization [:br]
           "Email: " email]]]
        [:br]]))]])
