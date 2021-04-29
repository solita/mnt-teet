(ns teet.contract.contract-view
  (:require [teet.common.common-styles :as common-styles]
            [herb.core :refer [<class]]
            [teet.ui.url :as url]
            [teet.ui.icons :as icons]
            [teet.ui.form :as form]
            [teet.ui.buttons :as buttons]
            [teet.ui.text-field :as text-field :refer [TextField]]
            [reagent.core :as r]
            teet.contract.contract-spec
            [teet.localization :refer [tr]]
            [teet.ui.date-picker :as date-picker]
            [teet.ui.select :as select]
            [teet.common.common-controller :as common-controller]
            [teet.contract.contract-model :as contract-model]))

(defn link-to-target
  [navigation-info]
  [url/Link navigation-info
   "LINK TO THIS target"])

(defn target-row
  [target]
  [:div
   (when-let [navigation-info (:navigation-info target)]
     [link-to-target navigation-info])
   [:p (pr-str target)]])

(defn target-table
  [targets]
  [:div
   (for [target targets]
     ^{:key (str (:db/id target))}
     [target-row target])])

(defn edit-contract-form
  [e! close-event form-atom]
  [form/form2 {:e! e!
               :value @form-atom
               :on-change-event (form/update-atom-event form-atom merge)
               :cancel-event close-event
               :spec :thk.contract/edit-contract-details
               :save-event #(common-controller/->SaveFormWithConfirmation
                              :thk.contract/edit-contract-details
                              {:form-data (common-controller/prepare-form-data (form/to-value @form-atom))}
                              (fn [_response]
                                (fn [e!]
                                  (e! (common-controller/->Refresh))
                                  (e! (close-event))))
                              (tr [:contract :contract-save-success]))}
   [:div {:class (<class common-styles/gray-container-style)}
    [form/field :thk.contract/number
     [TextField {}]]
    [form/field :thk.contract/external-link
     [TextField {}]]
    [form/field :ta/region
     [select/select-enum {:e! e!
                          :attribute :ta/region
                          :show-empty-selection? true}]]
    [form/field :thk.contract/signed-at
     [date-picker/date-input {}]]
    [form/field :thk.contract/start-of-work
     [date-picker/date-input {}]]
    [form/field :thk.contract/deadline
     [date-picker/date-input {}]]
    [form/field :thk.contract/extended-deadline
     [date-picker/date-input {}]]
    [form/field :thk.contract/warranty-period
     [TextField {:type :number}]]
    [form/field :thk.contract/cost
     [TextField {:type :number
                 :placeholder "0"
                 :step ".01"
                 :lang "et"
                 :min "0"
                 :end-icon (text-field/euro-end-icon)}]]]
   [form/footer2]])

(defn contract-page
  [e! app {:thk.contract/keys [targets] :as contract}]
  [:div
   [:div {:class (<class common-styles/margin-bottom 1)}
    [:div {:class (<class common-styles/flex-row-center)}
     [:h1 "contract page"]
     [form/form-modal-button {:modal-title (tr [:contract :edit-contract])
                              :max-width "sm"
                              :button-component
                              [buttons/button-primary {:size :small
                                                       :color :primary
                                                       :start-icon (r/as-element [icons/image-edit])}
                               (tr [:buttons :edit])]

                              :form-component [edit-contract-form e!]
                              :form-value (select-keys contract contract-model/contract-form-keys)}]]
    [:span (pr-str contract)]]
   [target-table targets]])
