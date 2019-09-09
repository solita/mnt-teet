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


(def mock-users [{:user/id #uuid "4c8ec140-4bd8-403b-866f-d2d5db9bdf74"
                  :user/given-name "Danny"
                  :user/family-name "Design-Manager"
                  :user/email "danny.design-manager@example.com"
                  :user/organization "Maanteeamet"}

                 {:user/id #uuid "ccbedb7b-ab30-405c-b389-292cdfe85271"
                  :user/given-name "Carla"
                  :user/family-name "Consultant"
                  :user/email "carla.consultant@example.com"
                  :user/organization "ACME Road Consulting, Ltd."}

                 {:user/id #uuid "fa8af5b7-df45-41ba-93d0-603c543c880d"
                  :user/given-name "Benjamin"
                  :user/family-name "Boss"
                  :user/email "benjamin.boss@example.com"
                  :user/organization "Maanteeamet"}])


(defn login-page [e! {login :login}]
  [Container {:maxWidth "sm"}
   [typography/Heading1 "Maanteeamet TEET"]
   [layout/column {:content-style {:padding-bottom "2em"}}
    [typography/Heading2 "Login with demo user"]
    (doall
     (for [{:user/keys [given-name family-name organization email] :as user} mock-users]
       ^{:key email}
       [:span
        [Card
         [CardHeader {:title (str given-name " " family-name)
                      :action (r/as-element
                               [Button {:color "primary"
                                        :on-click #(do
                                                     (e! (login-controller/->Login user))
                                                     (log/info "Login: " user))}
                                "Login"])}]
         [CardContent
          [Typography
           "Name: " given-name " " family-name [:br]
           "Organization: " organization [:br]
           "Email: " email]]]
        [:br]]))]])
