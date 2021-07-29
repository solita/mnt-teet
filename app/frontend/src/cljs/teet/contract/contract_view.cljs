(ns teet.contract.contract-view
  (:require [teet.common.common-styles :as common-styles]
            [herb.core :refer [<class] :as herb]
            [teet.authorization.authorization-check :as authorization-check]
            [teet.ui.url :as url]
            [teet.ui.form :as form]
            [teet.ui.buttons :as buttons]
            [teet.ui.text-field :as text-field :refer [TextField]]
            teet.contract.contract-spec
            [teet.localization :refer [tr]]
            [teet.ui.date-picker :as date-picker]
            [teet.ui.select :as select]
            [teet.common.common-controller :as common-controller]
            [teet.contract.contract-model :as contract-model]
            [teet.ui.typography :as typography]
            [teet.contract.contract-style :as contract-style]
            [teet.ui.table :as table]
            [teet.contract.contract-common :as contract-common]
            [teet.contract.contract-status :as contract-status]))

(defn target-table
  [targets]
  [:div
   [table/simple-table
    [[(tr [:contract :table-heading :project])]
     [(tr [:contract :table-heading :activity])]
     [(tr [:contract :table-heading :task])]
     [(tr [:contract :table-heading :project-manager])]]
    (for [target targets
          :let [task? (some? (get-in target [:target :task/type]))]]
      (if task?
        [[(get-in target [:project :thk.project/name])]
         [(tr [:enum (get-in target [:activity :activity/name])])]
         [[url/Link (:target-navigation-info target)
           (tr [:enum (get-in target [:target :task/type])])]]
         [(get-in target [:activity :activity/manager])]]
        [[(get-in target [:project :thk.project/name])]
         [[url/Link
           (merge (:target-navigation-info target)
                    {:component-opts {:data-cy "contract-related-link"}})
           (tr [:enum (get-in target [:target :activity/name])])]]
         [nil]
         [(get-in target [:activity :activity/manager])]]))]])

(defn related-contracts-table
  [related-contracts]
  [:div
   [table/simple-table
    [[(tr [:contract :table-heading :name])]
     [(tr [:contract :status])]]
    (for [related related-contracts]
      [[[url/Link {:page :contract
                   :params {:contract-ids (contract-model/contract-url-id related)}
                   :component-opts {:data-cy "project-related-contract-link"}}
         (contract-model/contract-name related)]]
       [[contract-status/contract-status {:show-label? true :size 17}
         (:thk.contract/status related)]]])]])

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
  [:div {:class (<class common-styles/flex-column-1)}
   [contract-common/contract-heading e! app contract]
   [:div {:class (<class contract-style/contract-page-container-style)}
    [:div {:class (<class common-styles/margin-bottom 1)}
     [:div {:class (herb/join (<class common-styles/flex-row-w100-space-between-center)
                              (<class common-styles/margin-bottom 2))}
      [typography/Heading1 {:data-cy "contract-information-heading"}
       (tr [:contract :contract-information-heading])]
      [authorization-check/when-authorized :thk.contract/edit-contract-details
       contract
       [form/form-modal-button {:modal-title (tr [:contract :edit-contract])
                                :max-width "sm"
                                :button-component
                                [buttons/button-secondary {}
                                 (tr [:buttons :edit])]

                                :form-component [edit-contract-form e!]
                                :form-value (select-keys contract contract-model/contract-form-keys)}]]]
     [contract-common/contract-information-row contract]]
    (let [related-contracts (filter
                              #(not (= (:db/id %) (:db/id contract)))
                              (:related-contracts contract))]
      (when
        (not-empty related-contracts)
        [:div {:class (<class common-styles/margin-bottom 4)}
         [typography/Heading4 {:class (<class common-styles/margin-bottom 2)}
          (tr [:contract :table-heading :related-contracts])]
         [related-contracts-table related-contracts]]))
    [:div
     [typography/Heading4 {:class (<class common-styles/margin-bottom 2)}
      (tr [:contract :contract-related-entities])]
     (if (not-empty targets)
       [target-table targets]
       [:span
        (tr [:contract :no-targets-for-contract])])]]])
