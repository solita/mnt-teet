(ns teet.login.login-view
  "Login page"
  (:require [teet.login.login-controller :as login-controller]
            [teet.ui.material-ui :refer [Container Card CardHeader CardContent TextField Grid Button]]
            [teet.ui.layout :as layout]
            [teet.localization :refer [tr]]
            [teet.ui.icons :as icons]
            [taoensso.timbre :as log]))

(defn login-page [e! {login :login}]
  [Container {:maxWidth "xs"}
   [Card
    [CardHeader {:title "Maanteeamet TEET"}]
    [CardContent
     [layout/column {:content-style {:padding-bottom "2em"}}
      [TextField {:style {:width "300px"}
                  :label (tr [:login :username])
                  :value (or (:username (:form login)) "")
                  :on-change #(e! (login-controller/->UpdateLoginForm (assoc (:form login)
                                                                             :username (-> % .-target .-value))))}]

      [TextField {:style {:width "300px"}
                  :label (tr [:login :password])
                  :value (or (:password (:form login)) "")
                  :type "password"
                  :on-change #(e! (login-controller/->UpdateLoginForm (assoc (:form login)
                                                                             :password (-> % .-target .-value))))
                  :on-key-down #(when (= "Enter" (.-key %))
                                  (e! (login-controller/->Login)))}]

      [layout/row {}
       [Button {:color "primary"
                :on-click #(e! (login-controller/->Login))}
        (tr [:login :login])]]]]]])
