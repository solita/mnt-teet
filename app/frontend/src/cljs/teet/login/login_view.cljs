(ns teet.login.login-view
  "Login page"
  (:require [teet.login.login-controller :as login-controller]
            [teet.ui.material-ui :refer [Select Container Card CardHeader CardContent TextField Grid Button]]
            [teet.ui.layout :as layout]
            [teet.ui.itemlist]
            [teet.localization :refer [tr]]
            [teet.ui.icons :as icons]
            [taoensso.timbre :as log]))


(def mock-users-map {:consultant "Carla Consultant (ACME Road Consulting, Ltd.)"
                     :design-manager "Danny Design-Manager (Maanteeamet)"
                     :boss "Benjamin Boss (Maanteeamet, department head)"})

(defn login-page [e! {login :login}]
  [Container {:maxWidth "xs"}
   [Card
    [CardHeader {:title "Maanteeamet TEET"}]
    [CardContent
     [layout/column {:content-style {:padding-bottom "2em"}}
      [teet.ui.itemlist/LinkList
       {:title "Available users"}
       [{:link "/select-user/carla-consultant@acme"
         :name "Carla Consultant (ACME Road Consulting, Ltd.)"}
        {:link "/select-user/danny.design-manager@maanteamet"
         :name "Danny Design-Manager (Maanteeamet)"}]
       (fn on-click-handler [evt]
         (.preventDefault evt)
         (let [href (-> evt .-target .-href)
               role (last (clojure.string/split href "/"))]         
           (js/console.log "selected role is" role)           
           (e! (login-controller/->UpdateLoginForm
                (assoc (:form login)
                       :username role)))
           ))]
      #_[teet.ui.select/select-with-action {:items (vec (keys mock-users-map))
                                          :item-label mock-users-map
                                          :name "Select user"
                                          
                                          :on-select #(e! (login-controller/->UpdateLoginForm
                                                           (do
                                                             (js/console.log (str "username: " (pr-str  (-> % .-target .-value))))
                                                             (assoc  (:form login) :username (-> % .-target .-value))
                                                             )))                                       
                                          }]
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

