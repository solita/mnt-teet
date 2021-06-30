(ns teet.contract.contract-partners-view
  (:require [teet.common.common-styles :as common-styles]
            [teet.localization :refer [tr tr-enum]]
            [herb.core :refer [<class] :as herb]
            [teet.ui.icons :as icons]
            [teet.ui.text-field :refer [TextField]]
            [teet.contract.contract-common :as contract-common]
            [teet.contract.contract-style :as contract-style]
            [teet.ui.material-ui :refer [Grid Checkbox Divider]]
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
            [teet.ui.validation :as validation]
            [teet.authorization.authorization-check :as authorization-check]
            [teet.user.user-model :as user-model]
            [teet.ui.format :as format]
            [teet.theme.theme-colors :as theme-colors]
            [teet.snackbar.snackbar-controller :as snackbar-controller]))


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

   [authorization-check/when-authorized
    :thk.contract/add-new-contract-partner-company contract
    [buttons/small-button-secondary
     {:class (<class common-styles/margin-bottom 2)
      :start-icon (r/as-element [icons/content-add])
      :href (routes/url-for {:page :contract-partners
                             :params params
                             :query {:page :add-partner}})}
     (tr [:contract :add-company])]]

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
  [{:company/keys [country name email phone-number]}]
  [:div
   [common/basic-information-column
    [{:label [typography/TextBold (tr [:fields :company/country])]
      :data (tr [:countries country])}
     {:label [typography/TextBold (tr [:fields :company/name])]
      :data name}
     {:label [typography/TextBold (tr [:fields :company/email])]
      :data email}
     {:label [typography/TextBold (tr [:fields :company/phone-number])]
      :data phone-number}]]])

(defn company-edit-info-column
  [{:company/keys [country name email phone-number business-registry-code] :as info}]
  [:div
   [common/basic-information-column
    [{:label [typography/TextBold (tr [:fields :company/name])]
      :data name}
     {:label [typography/TextBold (tr [:fields :company/country])]
      :data (tr [:countries country])}
     {:label [typography/TextBold (tr [:fields :company/business-registry-code])]
      :data business-registry-code}
     {:label [typography/TextBold (tr [:fields :company/email])]
      :data email}
     {:label [typography/TextBold (tr [:fields :company/phone-number])]
      :data phone-number}]]])

(defn selected-company-information
  [company]
  [:div
   [:div {:class (<class common-styles/margin-bottom 1.5)}
    [common/info-box {:variant :success
                      :title (tr [:contract :information-found])
                      :content [company-info-column company]}]]])

(defn edit-company-information
  [company]
  [:div
   [:div {:class (<class common-styles/margin-bottom 1.5)}
    [common/info-box {:variant :info
                      :title (tr [:contract :edit-company])
                      :content [company-edit-info-column company]}]]])

(defn edit-company-footer
  [e! form-value {:keys [cancel validate disabled?]}]
  (let [search-disabled? (:search-in-progress? form-value)
        estonian-company? (= (:company/country form-value) :ee)]
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
          (tr [:buttons :save])])]]]))

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
                                  :on-click (e! contract-partners-controller/->SearchBusinessRegistry :new-partner
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
   [form/field {:attribute :company/email
                :validate validation/validate-email-optional}
    [TextField {}]]
   [form/field :company/phone-number
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
  (let [foreign-company? (not= :ee (:company/country form-value))
        business-search-failed? (:no-results? form-value)
        exception-in-xroad? (:exception-in-xroad? form-value)]
    (if foreign-company?
      [:div
       [:span
        {:style {:pointer-events :none}}
        [TextField {:label (tr [:fields :company/country])
                    :read-only? true
                    :value (tr [:countries (:company/country form-value)])}]]
       [foreign-fields]]
      (edit-company-information form-value))))

(defn new-company-form-fields
  [form-value]
  (let [foreign-company? (not= :ee (:company/country form-value))
        business-search-failed? (:no-results? form-value)
        exception-in-xroad? (:exception-in-xroad? form-value)]
    [:div
     [form/field :company/country
      [select/country-select {:show-empty-selection? true}]]
     (if foreign-company?
       [foreign-fields]
       [estonian-form-section business-search-failed? exception-in-xroad?])]))

(defn- last-modified [selected-company]
  (str (tr [:common :last-modified]) " "
    (if (nil? (:meta/modified-at selected-company))
      (str (format/date-time (:meta/created-at selected-company)) " "
        (get-in selected-company [:company-contract/company :meta/creator :user/given-name])
        " "
        (get-in selected-company [:company-contract/company :meta/creator :user/family-name]))
      (str (format/date-time (:meta/modified-at selected-company)) " "
        (get-in selected-company [:company-contract/company :meta/modifier :user/given-name])
        " "
        (get-in selected-company [:company-contract/company :meta/modifier :user/family-name])))))

(defn edit-partner-form
  [e! _ selected-company]
  (e! (contract-partners-controller/->InitializeEditCompanyForm
        (merge
          (:company-contract/company selected-company)
          {:company-contract/lead-partner? (:company-contract/lead-partner? selected-company)})))
  (fn [e! {:keys [forms]} _]
    (r/with-let [on-change #(e! (contract-partners-controller/->UpdateEditCompanyForm %))]
      (let [form-state (:edit-partner forms)
            selected-company? (boolean (:db/id form-state))
            partner-save-command :thk.contract/save-contract-partner-company
            time-icon [icons/action-schedule {:style {:color theme-colors/gray-light}}]
            search-success? (:search-success? form-state)
            search-disabled? (:search-in-progress? form-state)
            estonian-company? (= (get-in selected-company [:company-contract/company :company/country]) :ee)]
        [Grid {:container true}
         [Grid {:item true
                :xs 12
                :md 6}
          [form/form2 {:e! e!
                       :value form-state
                       :save-event #(common-controller/->SaveFormWithConfirmation
                                      partner-save-command
                                      {:form-data form-state
                                       :contract (select-keys (:company-contract/contract selected-company) [:db/id])}
                                      (fn [response]
                                        (fn [e!]
                                          (e! (common-controller/->Refresh))
                                          (e! (contract-partners-controller/->InitializeEditCompanyForm
                                                (:company-contract/company selected-company)))
                                          (e! (common-controller/map->NavigateWithExistingAsDefault
                                                {:query {:page :partner-info :partner (:teet/id selected-company)}}))))
                                      (tr [:contract :partner-saved]))
                       :on-change-event on-change
                       :spec :contract-company/edit-company
                       :cancel-event contract-partners-controller/->CancelAddNewCompany}
           [edit-company-form-fields form-state]
           (when (or selected-company? search-success?)
             [form/field {:attribute :company-contract/lead-partner?}
              [select/checkbox {}]])
           [Grid {:container true
                  :direction :row
                  :justify :left
                  :alignItems  :left}
            time-icon
            [typography/SmallGrayText (last-modified selected-company)]]
           (when (true? estonian-company?)
             [buttons/button-primary {:disabled search-disabled?
                                      :on-click (e! contract-partners-controller/->SearchBusinessRegistry :edit-partner
                                                  (subs
                                                    (get-in selected-company
                                                      [:company-contract/company :company/business-registry-code] ) 2))} ; skip 'EE' prefix
              (tr [:partner :update-business-registry-data])])
           [form/footer2 (r/partial edit-company-footer e! form-state)]]]]))))

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
  [params contract]
  [:div {:class (<class common-styles/flex-1-align-center-justify-center)}
   [:div {:style {:text-align :center}}
    [icons/action-manage-accounts-outlined {:style {:font-size "3rem"}
                                            :class (<class common-styles/margin-bottom 1)}]
    [typography/Heading2 {:class (<class common-styles/margin-bottom 1)}
     (tr [:contract :partner-information])]
    [typography/Text {:class (<class common-styles/margin-bottom 2)}
     (tr [:contract :partner-information-text])]
    [authorization-check/when-authorized
     :thk.contract/add-new-contract-partner-company contract
     [buttons/button-primary
      {:start-icon (r/as-element [icons/content-add])
       :href (routes/url-for {:page :contract-partners
                              :params params
                              :query {:page :add-partner}})}
      (tr [:contract :add-company])]]]])

(defn personnel-section
  [e! {:keys [params query] :as app} selected-partner]
  [:div {:style {:display :flex
                 :justify-content :space-between
                 :align-items :center}}
   [typography/Heading2 (tr [:contract :persons])]
   [authorization-check/when-authorized
    :thk.contract/add-contract-employee selected-partner
    [buttons/button-secondary {:start-icon (r/as-element [icons/content-add])
                               :href (routes/url-for {:page :contract-partners
                                                      :params params
                                                      :query (merge
                                                               query
                                                               {:page :add-personnel})})}
     (tr [:contract :add-person])]]])

(defn user-info-column
  [{:user/keys [person-id email phone-number] :as user}]
  [:div
   [common/basic-information-column
    [{:label [typography/TextBold (tr [:common :name])]
      :data (user-model/user-name user)}
     {:label [typography/TextBold (tr [:fields :user/person-id])]
      :data person-id}
     {:label [typography/TextBold (tr [:fields :user/email])]
      :data email}
     {:label [typography/TextBold (tr [:fields :user/phone-number])]
      :data phone-number}]]])

(defn selected-user-information
  [user]
  [:div {:class (<class common-styles/margin-bottom 1.5)}
   [common/info-box {:variant :success
                     :title (tr [:contract :information-found])
                     :content [user-info-column user]}]])

(defn contract-personnel-form-footer
  [form-value {:keys [cancel validate disabled?]}]
  (let [save-disabled? (not (boolean (:company-contract-employee/user form-value)))]
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
         [buttons/button-primary {:disabled (or save-disabled? disabled?)
                                  :type :submit
                                  :class "submit"
                                  :on-click validate}
          (tr [:buttons :save])])]]]))

(defn add-personnel-form
  [e! {:keys [query] :as app} selected-partner]
  (r/with-let [form-atom (r/atom {})]
    (let [selected-user (:company-contract-employee/user @form-atom)
          user-selected? (boolean
                           selected-user)]
      [Grid {:container true}
       [Grid {:item true
              :xs 12
              :md 6}
        [form/form2 {:e! e!
                     :autocomplete-off? true
                     :value @form-atom
                     :on-change-event (form/update-atom-event form-atom (fn [old new]
                                                                          (if (= {:company-contract-employee/role #{}} new)
                                                                            (dissoc old :company-contract-employee/role)
                                                                            (merge old new))))
                     :spec :thk.contract/add-contract-employee
                     :cancel-event #(common-controller/map->NavigateWithExistingAsDefault
                                      {:query (merge query
                                                     {:page :partner-info})})
                     :save-event #(common-controller/->SaveFormWithConfirmation
                                    :thk.contract/add-contract-employee
                                    {:form-value @form-atom
                                     :company-contract-eid (:db/id selected-partner)}
                                    (fn [_response]
                                      (fn [e!]
                                        (e! (common-controller/->Refresh))
                                        (e! (common-controller/map->NavigateWithExistingAsDefault
                                              {:query (merge
                                                        query
                                                        {:page :partner-info})}))))
                                    (tr [:contract :employee-added]))}
         [typography/Heading1 {:class (<class common-styles/margin-bottom 1.5)}
          (tr [:contract :add-person])]
         (if selected-user
           [selected-user-information selected-user]
           [form/field :company-contract-employee/user
            [select/select-search
             {:e! e!
              :query (fn [text]
                       {:args {:search text
                               :company-contract-id (:db/id selected-partner)}
                        :query :contract/possible-partner-employees})
              :format-result select/user-search-select-result}]])
         (when user-selected?
           [form/field {:attribute :company-contract-employee/role}
            [select/select-user-roles-for-contract
             {:e! e!
              :error-text (tr [:contract :role-required])
              :placeholder (tr [:contract :select-user-roles])
              :no-results (tr [:contract :no-matching-roles])
              :show-empty-selection? true
              :clear-value [nil nil]}]])
         [form/footer2 (partial contract-personnel-form-footer @form-atom)]]]])))

(defn partner-info-header
  [_ partner params]
  (let [partner-name (get-in partner [:company-contract/company :company/name])
        teet-id (:teet/id partner)]
    [:<>
     [:h1 partner-name]
     [buttons/button-secondary
      {:href (routes/url-for {:page :contract-partners
                              :params params
                              :query {:page :edit-partner
                                      :partner teet-id}})}
      (tr [:buttons :edit])]]))

(defn partner-info
  [e! {:keys [params] :as app} selected-partner]
  [:div
   [partner-info-header app selected-partner params]
   [:p (pr-str selected-partner)]
   [Divider {:class (<class common-styles/margin 1 0)}]
   [personnel-section e! app selected-partner]])

(defn partners-page-router
  [e! {:keys [query params] :as app} contract]
  (let [selected-partner-id (:partner query)
        selected-partner (->> (:company-contract/_contract contract)
                              (filter
                                (fn [partner]
                                  (= (str (:teet/id partner)) selected-partner-id)))
                              first)]
    (case (keyword (:page query))
      :add-partner
      [new-partner-form e! contract (get-in app [:forms :new-partner])]
      :partner-info
      [partner-info e! app selected-partner]
      :edit-partner
      [edit-partner-form e! app selected-partner]
      :add-personnel
      [authorization-check/when-authorized
       :thk.contract/add-contract-employee selected-partner
       [add-personnel-form e! app selected-partner]]
      [partners-default-view params contract])))

;; navigated to through routes.edn from route /contracts/*****/partners
(defn partners-page
  [e! {:keys [user] :as app} {:thk.contract/keys [targets] :as contract}]
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


