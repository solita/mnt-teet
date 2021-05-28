(ns teet.contract.contract-view
  (:require [teet.common.common-styles :as common-styles]
            [herb.core :refer [<class] :as herb]
            [teet.ui.url :as url]
            [teet.ui.icons :as icons]
            [teet.ui.form :as form]
            [teet.ui.buttons :as buttons]
            [teet.ui.text-field :as text-field :refer [TextField]]
            [reagent.core :as r]
            teet.contract.contract-spec
            [teet.localization :refer [tr]]
            [teet.ui.date-picker :as date-picker]
            [teet.contract.contract-menu :as contract-menu]
            [teet.ui.select :as select]
            [teet.common.common-controller :as common-controller]
            [teet.ui.material-ui :refer [MenuList MenuItem
                                         IconButton ClickAwayListener Paper
                                         Popper]]
            [teet.contract.contract-model :as contract-model]
            [teet.ui.typography :as typography]
            [teet.ui.common :as common]
            [clojure.string :as str]
            [teet.environment :as environment]
            [teet.common.responsivity-styles :as responsivity-styles]
            [teet.ui.format :as format]
            [teet.util.euro :as euro]
            [teet.contract.contract-style :as contract-style]
            [teet.contract.contract-status :as contract-status]
            [teet.ui.table :as table]))

(defn contract-procurement-link
  [{:thk.contract/keys [procurement-number]}]
  [common/external-contract-link {:href (str (environment/config-value :contract :state-procurement-url) procurement-number)}
   (str (tr [:contracts :state-procurement-link]) " " procurement-number)])

(defn contract-external-link
  [{:thk.contract/keys [external-link procurement-number]}]
  (when external-link
    [common/external-contract-link {:href external-link}
     (str (tr [:contracts :external-link]) " " procurement-number)]))

(defn contract-thk-link
  [{:thk.contract/keys [procurement-id]}]
  [common/external-contract-link {:href (str (environment/config-value :contract :thk-procurement-url) procurement-id)}
   (str (tr [:contracts :thk-procurement-link]) " " procurement-id)])

(defn contract-external-links
  [contract]
  [:div {:class (herb.core/join (<class common-styles/flex-row)
                                (<class responsivity-styles/visible-desktop-only))}
   [contract-procurement-link contract]
   [contract-external-link contract]
   [contract-thk-link contract]])

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
         [[url/Link (:target-navigation-info target)
           (tr [:enum (get-in target [:target :activity/name])])]]
         [nil]
         [(get-in target [:activity :activity/manager])]]))]])

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


(defn contract-heading
  [e! app contract]
  [:div {:class (<class common-styles/margin 0 1 1 1)}
   [:div {:class (<class common-styles/flex-row-space-between)}
    [:div {:class (<class common-styles/flex-row-center)}
     [contract-menu/contract-menu e! app contract]
     [typography/TextBold {:class (<class common-styles/margin-left 0.5)}
      (contract-model/contract-name contract)]]
    [contract-external-links contract]]])

(defn contract-information-row
  [{:thk.contract/keys [type signed-at start-of-work deadline extended-deadline
                        warranty-end-date cost] :as contract}]
  [common/basic-information-row
   {:right-align-last? false
    :font-size "0.875rem"}
   [[(tr [:contract :status])
     [contract-status/contract-status {:show-label? true :size 15}
      (:thk.contract/status contract)]]
    (when-let [region (:ta/region contract)]
      [(tr [:fields :ta/region])
       [typography/Paragraph (tr [:enum region])]])
    (when type
      [(tr [:contract :thk.contract/type])
       [typography/Paragraph (tr [:enum type])]])
    (when signed-at
      [(tr [:fields :thk.contract/signed-at])
       [typography/Paragraph (format/date signed-at)]])
    (when start-of-work
      [(tr [:fields :thk.contract/start-of-work])
       [typography/Paragraph (format/date start-of-work)]])
    (when deadline
      [(tr [:fields :thk.contract/deadline])
       [typography/Paragraph (format/date deadline)]])
    (when extended-deadline
      [(tr [:fields :thk.contract/extended-deadline])
       [typography/Paragraph (format/date extended-deadline)]])
    (when warranty-end-date
      [(tr [:contract :thk.contract/warranty-end-date])
       [typography/Paragraph (format/date warranty-end-date)]])
    (when cost
      [(tr [:fields :thk.contract/cost])
       [typography/Paragraph (euro/format cost)]])]])

(defn contract-page
  [e! app {:thk.contract/keys [targets] :as contract}]
  [:div {:class (<class common-styles/flex-column-1)}
   [contract-heading e! app contract]
   [:div {:class (<class contract-style/contract-page-container-style)}
    [:div {:class (<class common-styles/margin-bottom 1)}
     [:div {:class (herb/join (<class common-styles/flex-row-w100-space-between-center)
                              (<class common-styles/margin-bottom 2))}
      [typography/Heading1 (tr [:contract :contract-information-heading])]
      [form/form-modal-button {:modal-title (tr [:contract :edit-contract])
                               :max-width "sm"
                               :button-component
                               [buttons/button-secondary {}
                                (tr [:buttons :edit])]

                               :form-component [edit-contract-form e!]
                               :form-value (select-keys contract contract-model/contract-form-keys)}]]
     [contract-information-row contract]]
    [:div
     [typography/Heading4 {:class (<class common-styles/margin-bottom 2)}
      (tr [:contract :contract-related-entities])]
     (if (not-empty targets)
       [target-table targets]
       [:span
        (tr [:contract :no-targets-for-contract])])]]])

(defn partner-page
  [e! app {:thk.contract/keys [targets] :as contract}]
  [:div {:class (<class common-styles/flex-column-1)}
   [contract-heading e! app contract]
   [:div
    [:div]]])
