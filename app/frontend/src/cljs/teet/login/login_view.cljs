(ns teet.login.login-view
  "Login page"
  (:require [teet.login.login-controller :as login-controller]
            [teet.ui.material-ui :refer [Container Card CardHeader CardContent Button
                                         Typography]]
            [teet.ui.layout :as layout]
            [teet.ui.itemlist]
            [teet.localization :refer [tr]]
            [teet.ui.icons :as icons]
            [taoensso.timbre :as log]
            [reagent.core :as r]
            [teet.ui.typography :as typography]))



(defn login-page [e! {login :login}]
  [Container {:maxWidth "sm"}
   [typography/Heading1 "Maanteeamet TEET"]
   [layout/column {:content-style {:padding-bottom "2em"}}
    [typography/Heading2 "Login with demo user"]
    (doall
     (for [{:user/keys [given-name family-name organization email] :as user} login-controller/mock-users]
       ^{:key email}
       [:span
        [Card
         [CardHeader {:title (str given-name " " family-name)
                      :action (r/as-element
                               [Button {:color "primary"
                                        :on-click #(e! (login-controller/->Login user))}
                                "Login"])}]
         [CardContent
          [Typography
           "Name: " given-name " " family-name [:br]
           "Organization: " organization [:br]
           "Email: " email]]]
        [:br]]))]])
