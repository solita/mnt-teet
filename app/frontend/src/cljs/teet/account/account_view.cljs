(ns teet.account.account-view
  (:require [teet.ui.typography :as typography]
            [teet.theme.theme-colors :as theme-colors]
            [teet.user.user-model :as user-model]
            [herb.core :refer [<class]]
            [reagent.core :as r]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.material-ui :refer [Fab Button Grid Checkbox]]
            [teet.common.common-styles :as common-styles]
            [teet.ui.form :as form]
            [teet.ui.validation :as validation]
            [teet.localization :refer [tr tr-enum] :as localization]
            [teet.ui.buttons :as buttons]
            [teet.account.account-controller :as account-controller]
            [teet.user.user-spec :as user-spec]))

(defn user-information-form-buttons
  [{:keys [disabled?]}]
  [:div {:style {:margin-top "1rem"}
         :class (<class common-styles/margin-bottom 1)}
   [buttons/button-primary
    {:type :submit
     :class "submit"
     :disabled disabled?}
    (tr [:account :save-information])]])

(defn user-information-form
  [e! user]
  (let [initial-state (select-keys user [:user/phone-number :user/email])]
    (r/with-let [form-state (r/atom initial-state)
                 form-change (form/update-atom-event form-state merge)]
      [:div
       [form/form2 {:e! e!
                    :on-change-event form-change
                    :save-event #(account-controller/->UpdateUser @form-state)
                    :disable-buttons? (= initial-state @form-state)
                    :value @form-state
                    :spec :account/update}
        [form/field :user/phone-number
         [TextField {:placeholder (tr [:fields :user/phone-number])}]]
        [form/field {:attribute :user/email
                     :validate validation/validate-email}
         [TextField {:placeholder (tr [:fields :user/email])}]]
        [form/footer2
         user-information-form-buttons]]])))

(defn user-information-card
  [e! user]
  [:div {:style {:background-color theme-colors/page-background-dark
                 :padding "1rem"
                 :margin "1rem"}}
   [typography/Heading2 {:class (<class common-styles/margin-bottom 0.5)}
    (user-model/user-name user)]
   [typography/TextBold {:class (<class common-styles/margin-bottom 1.5)}
    (:user/person-id user)]
   [typography/Text2 {:class (<class common-styles/margin-bottom 1)}
    (tr [:account :my-details])]
   [user-information-form e! user]])

(defn account-page [e!
                    _
                    account]
  [:div
   [typography/Heading1 {:style {:margin "1rem"}
                         :class "account-page-heading"}
    (tr [:account :my-account])]
   [Grid {:container true}
    [Grid {:item true
           :xs 12
           :lg 4}
     [user-information-card e! account]]
    [Grid {:item true
           :xs 12
           :lg 8}
     ;; Implement the rest of the account page functionality here
     ]]])
