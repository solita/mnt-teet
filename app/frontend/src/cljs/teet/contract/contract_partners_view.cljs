(ns teet.contract.contract-partners-view
  (:require [teet.common.common-styles :as common-styles]
            [teet.localization :refer [tr tr-enum]]
            [herb.core :refer [<class] :as herb]
            [teet.ui.icons :as icons]
            [teet.ui.text-field :refer [TextField]]
            [teet.contract.contract-common :as contract-common]
            [teet.contract.contract-style :as contract-style]
            [teet.ui.material-ui :refer [Grid]]
            [teet.ui.typography :as typography]
            [teet.ui.buttons :as buttons]
            [reagent.core :as r]
            teet.contract.contract-spec
            [teet.common.common-controller :as common-controller]
            [teet.contract.contract-partners-controller :as contract-partners-controller]
            [teet.routes :as routes]
            [teet.ui.form :as form]
            [teet.ui.select :as select]
            [teet.ui.common :as common]))


(defn partner-listing
  [e! {:keys [params query] :as app} contract-partners]
  [:div
   (for [partner contract-partners
         :let [company-name (get-in partner [:company-contract/company :company/name])
               identifier (str (:teet/id partner))
               selected? (= identifier (:partner query))]]
     ^{:key identifier}
     [:div
      [common/Link
       {:style {:font-weight (if selected?
                               :bold
                               :normal)}
        :href (routes/url-for {:page :contract-partners
                               :params params
                               :query {:page :partner-info
                                       :partner identifier}})}
       company-name]])])

(defn partner-right-panel
  [e! {:keys [params] :as app} contract]
  [:div
   [typography/Heading2
    {:class (<class common-styles/margin-bottom 2)}
    (tr [:contract :partner-information])]

   [buttons/small-button-secondary
    {:start-icon (r/as-element [icons/content-add])
     :href (routes/url-for {:page :contract-partners
                            :params params
                            :query {:page :add-partner}})}
    (tr [:contract :add-company])]

   [partner-listing e! app (:company-contract/_contract contract)]])

(defn partner-search-result
  [partner]
  [:div
   [typography/Text {:class (herb/join (<class common-styles/margin-right 0.25)
                                       (<class common-styles/inline-block))}
    (:company/name partner)]
   [typography/Text3 {:class (<class common-styles/inline-block)}
    (:company/business-registry-code partner)]])

(defn selected-company-confirmation
  [e! company]
  [:div
   [:span (pr-str company)]
   [buttons/button-primary {}
    "Confirm company"]])

(defn new-company-footer
  [{:keys [cancel validate disabled?]}]
  [:div {:class (<class form/form-buttons)}
   [:div {:style {:margin-left :auto
                  :text-align :center}}
    [:div {:class (<class common-styles/margin-bottom 1)}
     (when cancel
       [buttons/button-secondary {:style {:margin-right "1rem"}
                                  :disabled disabled?
                                  :class "cancel"
                                  :on-click cancel}
        (tr [:buttons :cancel])])
     (when validate
       [buttons/button-primary {:disabled disabled?
                                :type :submit
                                :class "submit"
                                :on-click validate}
        (tr [:buttons :save])])]]])

(defn new-company-form
  [e! contract form-value]
  (r/with-let [on-change #(e! (contract-partners-controller/->UpdateNewCompanyForm %))]
    (let [foreign-company? (not= :ee (:company/country form-value))]
      [:div
       [form/form2 {:e! e!
                    :value form-value
                    :save-event #(common-controller/->SaveFormWithConfirmation
                                   :thk.contract/add-contract-partner
                                   {:form-data form-value
                                    :contract (select-keys contract [:db/id])}
                                   (fn [response]
                                     (fn [e!]
                                       (e! (common-controller/->Refresh))
                                       (e! (contract-partners-controller/->ClearNewCompanyForm))
                                       (e! (common-controller/map->NavigateWithExistingAsDefault
                                             {:query {:page :partner-info
                                                      :partner (:company-contract-id response)}}))))
                                   (tr [:contract :contract-partner-saved]))
                    :cancel-event contract-partners-controller/->CancelAddNewCompany
                    :on-change-event on-change
                    :spec :contract-company/new-company}
        [form/field :company/country
         [select/country-select {:show-empty-selection? false}]]
        [form/field :company/business-registry-code
         [TextField {}]]
        (when foreign-company?
          [:<>
           [form/field :company/name
            [TextField {}]]
           [form/field :company/emails
            [TextField {}]]
           [form/field :company/phone-numbers
            [TextField {}]]])
        [form/footer2 new-company-footer]]])))


(defn new-partner-form
  [e! contract form-value]
  (r/with-let [add-new-company? (r/atom false)
               selected-company (r/atom nil)
               add-new-company #(reset! add-new-company? true)
               select-company #(reset! selected-company %)]
    [Grid {:container true}
     [Grid {:item true
            :xs 12
            :md 6}
      [:<>
       [typography/Heading2 {:class (<class common-styles/margin-bottom 2)}
        (tr [:contract :add-company])]
       (cond
         @add-new-company?
         [new-company-form e! contract form-value]
         (some? @selected-company)
         [selected-company-confirmation e! @selected-company]
         :else
         [select/select-search
          {:e! e!
           :label (tr [:contract :company-search-label])
           :placeholder (tr [:contract :company-search-placeholder])
           :no-results (tr [:contract :no-companies-matching-search])
           :query (fn [text]
                    {:args {:search-term text
                            :contract-eid (:db/id contract)}
                     :query :company/search})
           :on-change select-company

           :format-result partner-search-result
           :after-results-action {:title (tr [:contract :add-company-not-in-teet])
                                  :on-click add-new-company
                                  :icon [icons/content-add]}}])]]]))

(defn partners-default-view
  []
  [:div {:class (<class common-styles/flex-1-align-center-justify-center)}
   [:div {:style {:text-align :center}}
    [icons/action-manage-accounts-outlined {:font-size :large}]
    [typography/Heading2 (tr [:contract :partner-information])]
    [typography/Text (tr [:contract :partner-information-text])]]])

(defn partner-info
  [e! app selected-partner]
  [:h1 (pr-str selected-partner)])

(defn partners-page-router
  [e! {:keys [query forms] :as app} contract]
  (let [selected-partner-id (:partner query)
        selected-partner (->> (:company-contract/_contract contract)
                              (filter
                                (fn [contract]
                                  (= (str (:teet/id contract)) selected-partner-id)))
                              first)]
    (case (keyword (:page query))
      :add-partner
      [new-partner-form e! contract (:new-partner forms)]
      :partner-info
      [partner-info e! app selected-partner]
      [partners-default-view])))

;; navigated to through routes.edn from route /contracts/*****/partners
(defn partners-page
  [e! app {:thk.contract/keys [targets] :as contract}]
  [:div {:class (<class common-styles/flex-column-1)}
   [contract-common/contract-heading e! app contract]
   [:div {:class (<class contract-style/partners-page-container)}
    [Grid {:container true}
     [Grid {:item true
            :xs 3
            :class (herb/join (<class common-styles/padding 2)
                              (<class contract-style/contract-partners-panel-style))}
      [partner-right-panel e! app contract]]
     [Grid {:item true
            :xs :auto
            :class (herb/join (<class common-styles/padding 2)
                              (<class common-styles/flex-1))}
      [partners-page-router e! app contract]]]]])


