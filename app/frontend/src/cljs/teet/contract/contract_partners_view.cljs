(ns teet.contract.contract-partners-view
  (:require [teet.common.common-styles :as common-styles]
            [teet.localization :refer [tr tr-enum]]
            [herb.core :refer [<class] :as herb]
            [teet.ui.icons :as icons]
            [teet.ui.text-field :refer [TextField]]
            [teet.contract.contract-common :as contract-common]
            [teet.contract.contract-style :as contract-style]
            [teet.ui.material-ui :refer [Grid Checkbox]]
            [teet.ui.typography :as typography]
            [teet.ui.buttons :as buttons]
            [reagent.core :as r]
            teet.contract.contract-spec
            [teet.common.common-controller :as common-controller]
            [teet.contract.contract-partners-controller :as contract-partners-controller]
            [teet.routes :as routes]
            [teet.ui.form :as form]
            [teet.ui.select :as select]
            [teet.ui.common :as common]
            [clojure.string :as str]
            [teet.ui.validation :as validation]))


(defn partner-listing
  [{:keys [params query]} contract-partners]
  [:div
   (doall
     (for [partner contract-partners
           :let [company-name (get-in partner [:company-contract/company :company/name])
                 identifier (when-let [teet-id (:teet/id partner)]
                              (str teet-id))
                 selected? (= identifier (:partner query))
                 lead-partner? (:company-contract/lead-partner? partner)]
           :when (and company-name identifier)]
       ^{:key identifier}
       [:div {:class (herb/join (<class common-styles/margin-bottom 1)
                                (<class common-styles/flex-align-center))}
        [common/Link
         {:class (<class common-styles/margin-right 0.5)
          :style {:font-weight (if selected?
                                 :bold
                                 :normal)}
          :href (routes/url-for {:page :contract-partners
                                 :params params
                                 :query {:page :partner-info
                                         :partner identifier}})}
         company-name]
        (when lead-partner?
          [common/primary-tag (tr [:contract :lead-partner])])]))])

(defn partner-right-panel
  [e! {:keys [params] :as app} contract]
  [:div
   [typography/Heading2
    {:class (<class common-styles/margin-bottom 2)}
    (tr [:contract :partner-information])]

   [buttons/small-button-secondary
    {:class (<class common-styles/margin-bottom 2)
     :start-icon (r/as-element [icons/content-add])
     :href (routes/url-for {:page :contract-partners
                            :params params
                            :query {:page :add-partner}})}
    (tr [:contract :add-company])]

   [partner-listing app (:company-contract/_contract contract)]])

(defn partner-search-result
  [partner]
  [:div
   [typography/Text {:class (herb/join (<class common-styles/margin-right 0.25)
                                       (<class common-styles/inline-block))}
    (:company/name partner)]
   [typography/Text3 {:class (<class common-styles/inline-block)}
    (:company/business-registry-code partner)]])

(defn company-info-column
  [{:company/keys [country name emails phone-numbers]}]
  [:div
   [common/basic-information-column
    [{:label [typography/TextBold (tr [:fields :company/country])]
      :data (tr [:countries country])}
     {:label [typography/TextBold (tr [:fields :company/name])]
      :data name}
     {:label [typography/TextBold (tr [:fields :company/emails])]
      :data (str/join ", " emails)}
     {:label [typography/TextBold (tr [:fields :company/phone-numbers])]
      :data (str/join ", " phone-numbers)}]]])

(defn selected-company-information
  [company]
  [:div
   [:div {:class (<class common-styles/margin-bottom 1.5)}
    [common/info-box {:variant :success
                      :title "Information found"
                      :content [company-info-column company]}]]])

(defn edit-company-footer
  [e! form-value {:keys [cancel validate disabled?]}]
  (let [search-disabled? (or (not
                               (validation/valid-estonian-business-registry-id?
                                 (:company/business-registry-code form-value)))
                           (:search-in-progress? form-value))
        estonian-company? (= (:company/country form-value) :ee)
        search-success? (:search-success? form-value)]
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
       (if (and estonian-company? (not search-success?))
         [buttons/button-primary {:disabled search-disabled?
                                  :on-click (e! contract-partners-controller/->SearchBusinessRegistry
                                              (:company/business-registry-code form-value))}
          (tr [:buttons :search])]
         (when validate
           [buttons/button-primary {:disabled disabled?
                                    :type :submit
                                    :class "submit"
                                    :on-click validate}
            (tr [:buttons :save])]))]]]))

(defn new-company-footer
  [e! form-value {:keys [cancel validate disabled?]}]
  (let [search-disabled? (or (not
                               (validation/valid-estonian-business-registry-id?
                                 (:company/business-registry-code form-value)))
                             (:search-in-progress? form-value))
        estonian-company? (= (:company/country form-value) :ee)
        search-success? (:search-success? form-value)]
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
       (if (and estonian-company? (not search-success?))
         [buttons/button-primary {:disabled search-disabled?
                                  :on-click (e! contract-partners-controller/->SearchBusinessRegistry
                                                (:company/business-registry-code form-value))}
          (tr [:buttons :search])]
         (when validate
           [buttons/button-primary {:disabled disabled?
                                    :type :submit
                                    :class "submit"
                                    :on-click validate}
            (tr [:buttons :save])]))]]]))

(defn foreign-fields
  []
  [:div
   ^{:key "foreign-code"}
   [form/field :company/business-registry-code
    [TextField {}]]
   [form/field :company/name
    [TextField {}]]
   [form/field :company/emails
    [TextField {}]]
   [form/field :company/phone-numbers
    [TextField {}]]])

(defn estonian-form-section
  [business-search-failed? exception-in-xroad?]
  [:div
   ^{:key "estonian-code"}
   [form/field {:attribute :company/business-registry-code
                :validate validation/validate-estonian-business-registry-id}
    [TextField {}]]
   (cond
     business-search-failed?
     [common/info-box {:variant :error
                       :title (tr [:contract :no-company-found-title])
                       :content [:span (tr [:contract :no-company-found-content])]}]
     exception-in-xroad?
     [common/info-box {:variant :error
                       :title (tr [:contract :xroad-exception-title])
                       :content [:span (tr [:contract :xroad-exception-content])]}]
     :else
     [common/info-box {:variant :info
                       :content [:span (tr [:contract :search-companies-business-registry])]}])])

(defn edit-company-form-fields
  [form-value]
  ; @TODO Fix!
  (println "edit-company-form-fields")
  [foreign-fields])

(defn new-company-form-fields
  [form-value]
  (let [foreign-company? (not= :ee (:company/country form-value))
        business-search-failed? (:no-results? form-value)
        exception-in-xroad? (:exception-in-xroad? form-value)]
    [:div
     [form/field :company/country
      [select/country-select {:show-empty-selection? true}]]
     (if true
       [foreign-fields]
       [estonian-form-section business-search-failed? exception-in-xroad?])]))

(defn edit-partner-form
  [e! app selected-company]
  (fn [e! contract {:keys [search-success?] :as form-value}]
    (r/with-let [on-change #(e! (contract-partners-controller/->UpdateNewCompanyForm %))]
      [Grid {:container true}
       [Grid {:item true
              :xs 12
              :md 6}
        [form/form2 {:e! e!
                     :value form-value}

         [typography/Heading2 {:class (<class common-styles/margin-bottom 2)}
          (tr [:contract :edit-company])]
         (edit-company-form-fields form-value)
         [form/footer2 (r/partial edit-company-footer e! form-value)]]]]
      )))

(defn new-partner-form
  [e! _ _]
  (e! (contract-partners-controller/->InitializeNewCompanyForm))
  (fn [e! contract {:keys [search-success?] :as form-value}]
    (r/with-let [add-new-company? (r/atom false)
                 add-new-company #(reset! add-new-company? true)
                 select-company #(e! (contract-partners-controller/->SelectCompany %))
                 on-change #(e! (contract-partners-controller/->UpdateNewCompanyForm %))]
      (let [selected-company? (boolean (:db/id form-value))
            partner-save-command (if selected-company?
                                   :thk.contract/add-existing-company-as-partner
                                   :thk.contract/add-new-contract-partner-company)]
        [Grid {:container true}
         [Grid {:item true
                :xs 12
                :md 6}
          [form/form2 {:e! e!
                       :value form-value
                       :save-event #(common-controller/->SaveFormWithConfirmation
                                      partner-save-command
                                      {:form-data form-value
                                       :contract (select-keys contract [:db/id])}
                                      (fn [response]
                                        (fn [e!]
                                          (e! (common-controller/->Refresh))
                                          (e! (contract-partners-controller/->InitializeNewCompanyForm))
                                          (e! (common-controller/map->NavigateWithExistingAsDefault
                                                {:query {:page :partner-info
                                                         :partner (:company-contract-id response)}}))))
                                      (tr [:contract :partner-saved]))
                       :cancel-event contract-partners-controller/->CancelAddNewCompany
                       :on-change-event on-change
                       :spec :contract-company/new-company}
           [typography/Heading2 {:class (<class common-styles/margin-bottom 2)}
            (tr [:contract :add-company])]
           (cond
             (or selected-company? search-success?)
             [selected-company-information form-value]
             @add-new-company?
             [new-company-form-fields form-value]
             :else
             [:div
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
                                       :icon [icons/content-add]}}]])
           (when (or selected-company? search-success? @add-new-company?)
             [form/field {:attribute :company-contract/lead-partner?}
              [select/checkbox {}]])
           [form/footer2 (r/partial new-company-footer e! form-value)]]]]))))

(defn partners-default-view
  [params]
  [:div {:class (<class common-styles/flex-1-align-center-justify-center)}
   [:div {:style {:text-align :center}}
    [icons/action-manage-accounts-outlined {:style {:font-size "3rem"}
                                            :class (<class common-styles/margin-bottom 1)}]
    [typography/Heading2 {:class (<class common-styles/margin-bottom 1)}
     (tr [:contract :partner-information])]
    [typography/Text {:class (<class common-styles/margin-bottom 2)}
     (tr [:contract :partner-information-text])]
    [buttons/button-primary
     {:start-icon (r/as-element [icons/content-add])
      :href (routes/url-for {:page :contract-partners
                             :params params
                             :query {:page :add-partner}})}
     (tr [:contract :add-company])]]])

(defn partner-info-header
  [partner-name params]
  [:<>
   [:h1 partner-name]
   [buttons/button-secondary
    {:edit-icon (r/as-element [icons/image-edit])
     :href (routes/url-for {:page :contract-partners
                            :params params
                            :query {:page :edit-partner}})}
    (tr [:buttons :edit])]])

(defn partner-info
  [e! {:keys [params] :as app} selected-partner]
  [:<>
   [partner-info-header (get-in selected-partner [:company-contract/company :company/name]) params]
   [:p (pr-str selected-partner)]])

(defn partners-page-router
  [e! {:keys [query params] :as app} contract]
  (let [selected-partner-id (:partner query)
        selected-partner (->> (:company-contract/_contract contract)
                              (filter
                                (fn [contract]
                                  (= (str (:teet/id contract)) selected-partner-id)))
                              first)]
    (case (keyword (:page query))
      :add-partner
      [new-partner-form e! contract (get-in app [:forms :new-partner])]
      :partner-info
      [partner-info e! app selected-partner]
      :edit-partner
      [edit-partner-form e! app selected-partner]
      [partners-default-view params])))

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


