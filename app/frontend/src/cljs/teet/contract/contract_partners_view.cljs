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
            [teet.ui.util :refer [mapc]]
            [teet.ui.select :as select]
            [teet.ui.common :as common]
            [teet.ui.validation :as validation]
            [teet.authorization.authorization-check :as authorization-check]
            [teet.user.user-model :as user-model]
            [teet.ui.format :as format]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.table :as table]
            [teet.ui.file-upload :as file-upload]
            [clojure.string :as str]
            [re-svg-icons.feather-icons :as fi]
            [teet.file.file-controller :as file-controller]))

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
  [display-data info-box-title info-box-variant]
  (let [info-column-data (mapv (fn [{label-key :label-key data :data}]
                                 {:label [typography/Text2Bold (tr label-key)]
                                  :data data})
                           display-data)]
    [:div {:class (<class common-styles/margin-bottom 1.5)}
     [common/info-box {:variant info-box-variant
                       :title info-box-title
                       :content [common/basic-information-column info-column-data]}]]))

(defn user-employee-info-column
  [display-data info-box-title info-box-variant]
  (let [info-column-data (mapv (fn [{label-key :label-key data :data}]
                                 {:label [typography/Text2Bold (tr label-key)]
                                  :data data})
                               display-data)]
    [:div {:class (<class common-styles/margin-bottom 1.5)}
     [common/info-box {:variant info-box-variant
                       :title info-box-title
                       :content [common/basic-information-column info-column-data]}]]))

(defn user-info
  [{:user/keys [person-id email phone-number ]} roles]
  (let [display-data [{:label-key [:fields :user/global-role] :data (str/join ", " (mapv #(tr-enum %) roles))}
                      {:label-key [:fields :user/person-id] :data person-id}
                      {:label-key [:fields :user/email] :data email}
                      {:label-key [:fields :user/phone-number] :data phone-number}]]
    [user-employee-info-column display-data "" :simple]))

(defmulti company-info (fn [company key] key))

(defmethod company-info :edit
  [{:company/keys [name country business-registry-code email phone-number ] :as info}]
  (let [display-data [{:label-key [:fields :company/name] :data name}
                      {:label-key [:fields :company/country] :data (tr [:countries country])}
                      {:label-key [:fields :company/business-registry-code] :data business-registry-code}
                      {:label-key [:fields :company/email] :data email}
                      {:label-key [:fields :company/phone-number] :data phone-number}]]
    [company-info-column display-data (tr [:contract :edit-company]) :simple]))

(defmethod company-info :info
  [{:company/keys [country business-registry-code email phone-number ] :as info}]
  (let [display-data [{:label-key [:fields :company/country] :data (tr [:countries country])}
                      {:label-key [:fields :company/business-registry-code] :data business-registry-code}
                      {:label-key [:fields :company/email] :data email}
                      {:label-key [:fields :company/phone-number] :data phone-number}]]
    [company-info-column display-data "" :simple]))

(defmethod company-info :information-found
  [{:company/keys [name country business-registry-code email phone-number] :as info}]
  (let [display-data [{:label-key [:fields :company/name] :data name}
                      {:label-key [:fields :company/country] :data (tr [:countries country])}
                      {:label-key [:fields :company/business-registry-code] :data business-registry-code}
                      {:label-key [:fields :company/email] :data email}
                      {:label-key [:fields :company/phone-number] :data phone-number}]]
    [company-info-column display-data (tr [:contract :information-found]) :success]))

(defn edit-company-footer
  [e! form-value {:keys [cancel delete validate disabled? delete-disabled-error-text]}]
  (let [search-disabled? (:search-in-progress? form-value)
        estonian-company? (= (:company/country form-value) :ee)]
    [:div {:class (<class form/form-buttons :flex-end)}
     [:div (when delete
             [common/popper-tooltip
              (when delete-disabled-error-text
                {:title delete-disabled-error-text})
              [buttons/delete-button-with-confirm
               {:action delete
                :underlined? :true
                :confirm-button-text (tr [:contract :delete-button-text])
                :disabled delete-disabled-error-text
                :cancel-button-text (tr [:contract :cancel-button-text])
                :modal-title (tr [:contract :are-you-sure-delete-partner])
                :modal-text (tr [:contract :confirm-delete-partner-text])}
               (tr [:buttons :remove-from-contract])]])]
     [:div {:style {:margin-left :auto
                    :text-align :center}}
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
         (tr [:buttons :save])])]]))

(defn new-company-footer
  [e! form-value {:keys [cancel validate disabled?]}]
  (let [search-disabled? (or (not
                               (validation/valid-estonian-business-registry-id?
                                 (:company/business-registry-code form-value)))
                             (:search-in-progress? form-value))
        estonia-selected? (= (:company/country form-value) :ee)
        search-success? (:search-success? form-value)
        existing-company? (:db/id form-value)]
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
       (if (and estonia-selected? (not search-success?) (not existing-company?))
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
        [:div {:style {:margin-bottom :8px}}
         [typography/Heading3 (tr [:contract :edit-company])]]
        [TextField {:label (tr [:fields :company/country])
                    :read-only? true
                    :value (tr [:countries (:company/country form-value)])}]
        [foreign-fields]]]
      [company-info form-value :edit])))

(defn new-company-form-fields
  [form-value]
  (let [foreign-company? (not= :ee (:company/country form-value))
        business-search-failed? (and (:no-results? form-value)
                                     (not (:search-in-progress? form-value))
                                     ;; Clear error after business id is edited
                                     (= (:business-id-used-in-search form-value)
                                        (:company/business-registry-code form-value)))
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
        (user-model/user-name (get-in selected-company [:company-contract/company :meta/creator])))
      (str (format/date-time (:meta/modified-at selected-company)) " "
        (user-model/user-name (get-in selected-company [:company-contract/company :meta/modifier]))))))

(defn- active-employees [contract selected-company]
  (when (and selected-company contract)
    (->> contract
         :company-contract/_contract
         (filter (fn [company-contract]
                   (= selected-company (:db/id (:company-contract/company company-contract)))))
         first
         :company-contract/employees
         (filter :company-contract-employee/active?))))

(defn edit-partner-form
  [e! {:keys [query] :as app} contract selected-company]
  (e! (contract-partners-controller/->InitializeEditCompanyForm
        (merge
          (:company-contract/company selected-company)
          {:company-contract/lead-partner? (:company-contract/lead-partner? selected-company)})))
  (fn [e! {:keys [forms]} _]
    (r/with-let [on-change #(e! (contract-partners-controller/->UpdateEditCompanyForm %))]
      (let [form-state (:edit-partner forms)
            selected-company? (boolean (:db/id form-state))
            partner-save-command :thk.contract/edit-contract-partner-company
            time-icon [icons/action-schedule {:style {:color theme-colors/gray-light}}]
            search-success? (:search-success? form-state)
            search-disabled? (:search-in-progress? form-state)
            estonian-company? (= (get-in selected-company [:company-contract/company :company/country]) :ee)
            ;; NOTE: there is no way to remove company-contract-employees in the current specification.
            ;; Employees can only be deactivated.
            ;; Therefore the dev team decided to allow removing company-contract if it has no active
            ;; employees.
            delete-disabled? (seq (active-employees contract (:db/id form-state)))]
        [Grid {:container true}
         [Grid {:item true
                :xs 12
                :md 6}
          ;; Re-render the form when delete-disabled? changes
          ^{:key (str "edit-partner-form-" delete-disabled?)}
          [form/form2 (merge {:e! e!
                              :value form-state
                              :save-event #(common-controller/->SaveFormWithConfirmation
                                             partner-save-command
                                             {:form-data form-state
                                              :contract (select-keys (:company-contract/contract selected-company) [:db/id])}
                                             (fn [_]
                                               (fn [e!]
                                                 (e! (common-controller/->Refresh))
                                                 (e! (contract-partners-controller/->InitializeEditCompanyForm
                                                       (:company-contract/company selected-company)))
                                                 (e! (common-controller/map->NavigateWithExistingAsDefault
                                                       {:query {:page :partner-info :partner (:teet/id selected-company)}}))))
                                             (tr [:contract :partner-updated]))
                              :on-change-event on-change
                              :spec :contract-company/edit-company
                              :cancel-event #(common-controller/map->NavigateWithExistingAsDefault
                                               {:query (merge query
                                                              {:page :partner-info})})
                              }
                             (when (:db/id selected-company)
                               {:delete (contract-partners-controller/->DeletePartner
                                          {:partner selected-company
                                           :contract (select-keys (:company-contract/contract selected-company) [:db/id])})})
                             (when delete-disabled?
                               {:delete-disabled-error-text (tr [:contract :cannot-delete-company-with-persons])}))
           [edit-company-form-fields form-state]
           (when (or selected-company? search-success?)
             [form/field {:attribute :company-contract/lead-partner?}
              [select/checkbox {}]])
           [Grid {:container true
                  :direction :column
                  :justify :space-evenly
                  :alignItems :flex-start}
            [Grid {:item true :xs 6 :style {:margin-bottom :1rem}}
             (when (true? estonian-company?)
               [buttons/small-button-secondary
                {:disabled search-disabled?
                 :on-click (e! contract-partners-controller/->SearchBusinessRegistry
                               :edit-partner (subs
                                               (get-in selected-company
                                                       [:company-contract/company :company/business-registry-code]) 2))} ; skip 'EE' prefix
                (tr [:partner :update-business-registry-data])])]
            [Grid {:item true :xs 12}
             [Grid {:container true :direction :row :justify :space-evenly :style {:margin-bottom :1rem}}
              time-icon
              [typography/SmallGrayText (last-modified selected-company)]]]]
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
             [:div
              [company-info form-value :information-found]]
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

(defn key-person-approvals-panel
  [e! {:keys [params query] :as app} employees active?]
  [:div {:class (<class contract-style/personnel-table-style)}
   [:h4 (str "Approvals")]])

(defn employee-table
  [e! {:keys [params query] :as app} employees selected-partner active?]
  [:div {:class (<class contract-style/personnel-table-style)}
   [:h4 (str (if active?
               (tr [:contract :partner :active-persons])
               (tr [:contract :partner :deactive-persons]))
          " (" (count employees) ")")]
   [table/simple-table
    [[(tr [:common :name]) {:width "30%"}]
     [(tr [:user :role])]
     [(tr [:contract :partner :key-person]) {:width "10%"}]
     ["" {:align :right :width "10%"}]
     ["" {:align :right :width "10%"}]]
    (for [employee employees]
      [[(str (get-in employee [:company-contract-employee/user :user/family-name]) " "
             (get-in employee [:company-contract-employee/user :user/given-name]))]
       (if (not-empty (:company-contract-employee/role employee))
         [(str/join ", " (mapv #(tr-enum %) (:company-contract-employee/role employee)))]
         [])
       [] ;TODO implement key person functionality
       [[authorization-check/when-authorized
         :thk.contract/add-contract-employee selected-partner
         [buttons/button-with-confirm
          {:action (e! contract-partners-controller/->ChangePersonStatus (:db/id employee) (not active?))
           :modal-title (str (if active? (tr [:contract :partner :deactivate]) (tr [:contract :partner :activate])) "?")
           :modal-text (tr [:contract :partner :change-status-text])
           :close-on-action? true
           :confirm-button-text (if active? (tr [:contract :partner :deactivate]) (tr [:contract :partner :activate]))
           :confirm-button-style (if active? buttons/button-warning buttons/button-green)}
          [(if active? buttons/button-warning buttons/button-green)
           {:id (str "person-status-btn-" (:db/id employee))
            :size :small
            :class (<class contract-style/personnel-activation-link-style active?)}
           (if active? (tr [:contract :partner :deactivate]) (tr [:contract :partner :activate]))]]]]
       [[buttons/small-button-secondary
         {:href (routes/url-for {:page :contract-partners
                                 :params (merge
                                           params
                                           {:employee employee})
                                 :query (merge
                                          query
                                          {:page :personnel-info
                                           :user-id (get-in employee [:company-contract-employee/user :user/id])})})}
         (tr [:common :view-more-info])]]])]])

(defn personnel-section
  [e! {:keys [params query] :as app} selected-partner]
  [:div {:class (<class contract-style/personnel-section-style)}
   [:div {:class (<class contract-style/personnel-section-header-style)}
    [typography/Heading2 (tr [:contract :persons])]
    [authorization-check/when-authorized
     :thk.contract/add-contract-employee selected-partner
     [buttons/button-secondary {:start-icon (r/as-element [icons/content-add])
                                :href (routes/url-for {:page :contract-partners
                                                       :params params
                                                       :query (merge
                                                                query
                                                                {:page :add-personnel})})}
      (tr [:contract :add-person])]]]
   [employee-table e! app (filterv #(:company-contract-employee/active? %)
                                   (:company-contract/employees selected-partner))
    selected-partner true]
   [employee-table e! app (filterv #(not (:company-contract-employee/active? %))
                                   (:company-contract/employees selected-partner))
    selected-partner false]])

(defn info-personnel-section
  [e! {:keys [params query] :as app} selected-partner employee]
  [:div {:class (<class contract-style/personnel-section-style)}
   [:div {:class (<class contract-style/personnel-section-header-style)}
    [authorization-check/when-authorized
     :thk.contract/add-contract-employee selected-partner
     [buttons/button-secondary {:start-icon (r/as-element [icons/content-add])
                                :href (routes/url-for {:page :contract-partners
                                                       :params params
                                                       :query (merge
                                                                query
                                                                {:page :assign-key-person})})}
      (tr [:buttons :assign-as-key-person])]]]])

(defn key-person-files
  "Displays the file list for the key person"
  [e! employee]
  [:div {:id (str "key-person-" (:db/id employee))}
   [:div {:class (<class common-styles/flex-row-w100-space-between-center)}
    [:h1 (tr [:contract :partner :key-person-files])]]
   [:div
    (mapc (fn [file]
            [teet.file.file-view/file-row2 {:delete-action (fn [file]
                                                             (e! (contract-partners-controller/->RemoveFileLink
                                                                   (:db/id employee)
                                                                   (:db/id file))))} file])
          (:company-contract-employee/attached-files employee))]
   [file-upload/FileUploadButton
    {:id "keyperson-files-field"
     :drag-container-id (str "key-person-" (:db/id employee))
     :color :secondary
     :button-attributes {:size :small :margin-top "1rem" :margin-bottom "1rem"}
     :on-drop #(e! (file-controller/map->UploadFiles
                     {:files %
                      :project-id nil
                      :user-attachment? true
                      :attach-to (:db/id employee)
                      :on-success common-controller/->Refresh}))}
    (str "+ " (tr [:buttons :upload]))]])

(defn key-person-assignment-section
  [e! {:keys [params query] :as app} selected-partner employee]
  [:div {:class (<class contract-style/personnel-files-section-style)}
   [:div {:class (<class contract-style/personnel-files-section-header-style)}
    [key-person-files e! employee]]
   [:div {:class (<class contract-style/personnel-files-section-header-style)}] ;; TODO: Licenses section here
   [:div
    [authorization-check/when-authorized
     :thk.contract/add-contract-employee selected-partner
     [buttons/delete-button-with-confirm
      {:action (e! contract-partners-controller/->AssignKeyPerson (:db/id employee) false)
       :underlined? :true
       :confirm-button-text (tr [:contract :delete-button-text])
       :cancel-button-text (tr [:contract :cancel-button-text])
       :modal-title (tr [:contract :are-you-sure-remove-key-person-assignment])
       :modal-text (tr [:contract :confirm-remove-key-person-text])}
      (tr [:buttons :remove-key-person-assignment])]]]])

(defn user-info-column
  [{:user/keys [person-id email phone-number] :as user}]
  [:div
   [common/basic-information-column
    [{:label [typography/Text2Bold (tr [:common :name])]
      :data (user-model/user-name user)}
     {:label [typography/Text2Bold (tr [:fields :user/person-id])]
      :data person-id}
     {:label [typography/Text2Bold (tr [:fields :user/email])]
      :data email}
     {:label [typography/Text2Bold (tr [:fields :user/phone-number])]
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
         [buttons/button-primary {:disabled disabled?
                                  :type :submit
                                  :class "submit"
                                  :on-click validate}
          (tr [:buttons :save])])]]]))

(defn new-person-form-fields [e! form-value & [personal-info-disabled?]]
  [:div
   [form/field {:attribute :user/given-name
                :required? true}
    [TextField {:disabled personal-info-disabled?}]]
   [form/field {:attribute :user/family-name
                :required? true}
    [TextField {:disabled personal-info-disabled?}]]
   ^{:key "person-id"}
   [form/field {:attribute :user/person-id
                :validate (fn [value]
                            (when-not (str/blank? value)
                              (validation/validate-person-id value)))
                :required? :true}
    [TextField {:disabled personal-info-disabled?}]]
   [form/field {:attribute :user/email
                :validate validation/validate-email-optional
                :required? :true}
    [TextField {:disabled personal-info-disabled?}]]
   [form/field :user/phone-number
    [TextField {:disabled personal-info-disabled?}]]
   [form/field {:attribute :company-contract-employee/role}
    [select/select-user-roles-for-contract
     {:e! e!
      :error-text (tr [:contract :role-required])
      :placeholder (tr [:contract :select-user-roles])
      :no-results (tr [:contract :no-matching-roles])
      :show-empty-selection? true
      :clear-value [nil nil]}]]])

(defn add-personnel-form
  [e! query selected-partner]
  (r/with-let [form-atom (r/atom {})
               add-new-person? (r/atom false)
               add-new-person #(reset! add-new-person? true)]
    (let [selected-user (:company-contract-employee/user @form-atom)
          user-selected? (boolean selected-user)]
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
                     :cancel-event #(common-controller/map->NavigateWithExistingAsDefault
                                      {:query (merge query
                                                     {:page :partner-info})})
                     :spec :thk.contract/add-contract-employee
                     :save-event #(common-controller/->SaveFormWithConfirmation
                                    (cond
                                      @add-new-person?
                                      :thk.contract/add-new-contract-employee
                                      :else
                                      :thk.contract/add-contract-employee)
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
           (cond
             @add-new-person?
             [new-person-form-fields e! (:form-value @form-atom)]
             :else
             [:div
              [form/field {:attribute :company-contract-employee/user
                           :required? :true}
               [select/select-search
                {:e! e!
                 :query (fn [text]
                          {:args {:search text
                                  :company-contract-id (:db/id selected-partner)}
                           :query :contract/possible-partner-employees})
                 :format-result select/user-search-select-result
                 :no-results (tr [:contract :user-not-found-or-already-added])
                 :after-results-action {:title (tr [:contract :add-person-not-in-teet])
                                        :on-click add-new-person
                                        :icon [icons/content-add]}}]]]))
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

(defn edit-personnel-form
  [e! {:keys [query] :as app} selected-partner selected-person]
  (let [user (:company-contract-employee/user selected-person)
        person-id (:user/person-id user)
        email (:user/email user)
        phone-number (:user/phone-number user)
        given-name (:user/given-name user)
        family-name (:user/family-name user)
        user-id (:user/id user)
        roles (set (:company-contract-employee/role selected-person))
        personal-info-disabled? (:user/last-login user)]
    (r/with-let
      [form-atom (r/atom {:user/person-id person-id
                          :user/email email
                          :user/phone-number phone-number
                          :user/given-name given-name
                          :user/family-name family-name
                          :user/id user-id
                          :company-contract-employee/role roles})]
      [Grid {:container true}
       [Grid {:itme true :xs 12 :md 6}]]
      [form/form2 {:e! e!
                   :value @form-atom
                   :on-change-event (form/update-atom-event form-atom (fn [old new]
                                                                        (if (= {:company-contract-employee/role #{}} new)
                                                                          (dissoc old :company-contract-employee/role)
                                                                          (merge old new))))
                   :cancel-event #(common-controller/map->NavigateWithExistingAsDefault
                                    {:query (merge query
                                                   {:page :personnel-info
                                                    :user-id user-id})})
                   :spec :thk.contract/edit-contract-employee
                   :save-event #(common-controller/->SaveFormWithConfirmation :thk.contract/edit-contract-employee
                                  {:form-value @form-atom
                                   :company-contract-eid (:db/id selected-partner)}
                                  (fn [_response]
                                    (fn [e!]
                                      (e! (common-controller/->Refresh))
                                      (e! (common-controller/map->NavigateWithExistingAsDefault
                                            {:query (merge
                                                      query
                                                      {:page :personnel-info
                                                       :user-id user-id})}))))
                                  (tr [:contract :partner :person-updated]))}
       [typography/Heading1 {:class (<class common-styles/margin-bottom 1.5)}
        (tr [:contract :partner :edit-person])]
       [new-person-form-fields e! (:form-value @form-atom) personal-info-disabled?]
       [form/footer2 (partial contract-personnel-form-footer @form-atom)]])))

(defn partner-info-header
  [partner params]
  (let [partner-name (get-in partner [:company-contract/company :company/name])
        lead-partner? (:company-contract/lead-partner? partner)
        teet-id (:teet/id partner)]
    [:div {:class (<class contract-style/partner-info-header)}
     [:h1 partner-name]
     (when lead-partner?
       [common/primary-tag (tr [:contract :lead-partner])])
     [authorization-check/when-authorized
      :thk.contract/edit-contract-partner-company partner
      [buttons/button-secondary
       {:href (routes/url-for {:page :contract-partners
                               :params params
                               :query {:page :edit-partner
                                       :partner teet-id}})}
       (tr [:buttons :edit])]]]))

(defn employee-info-header
  [employee params selected-partner]
  (let [employee-name (str (get-in employee [:company-contract-employee/user :user/given-name]) " "
                           (get-in employee [:company-contract-employee/user :user/family-name]))
        teet-id (:teet/id selected-partner)
        user-id (get-in employee [:company-contract-employee/user :user/id])]
    [:div {:class (<class contract-style/partner-info-header)}
     [:h1 employee-name]
     [authorization-check/when-authorized
      :thk.contract/edit-contract-partner-company employee
      [buttons/button-secondary
       {:href (routes/url-for {:page :contract-partners
                               :params (merge
                                         params
                                         {:employee employee})
                               :query {:page :edit-personnel
                                       :partner teet-id
                                       :user-id user-id}})}
       (tr [:buttons :edit])]]]))

(defn partner-info
  [e! {:keys [params] :as app} selected-partner]
  [:div
   [partner-info-header selected-partner params]
   [company-info (:company-contract/company selected-partner) :info]
   [Divider {:class (<class common-styles/margin 1 0)}]
   [personnel-section e! app selected-partner]])

(defn employee-info
  [e! {:keys [params] :as app} selected-partner employee]
  [:div
   [employee-info-header employee params selected-partner]
   [user-info (:company-contract-employee/user employee) (:company-contract-employee/role employee)]
   [Divider {:class (<class common-styles/margin 1 0)}]
   [info-personnel-section e! app selected-partner employee]])

(defn key-employee-info
  [e! {:keys [params] :as app} selected-partner employee]
  [:div
   [employee-info-header employee params selected-partner]
   [user-info (:company-contract-employee/user employee) (:company-contract-employee/role employee)]
   [Divider {:class (<class common-styles/margin 1 0)}]
   [key-person-assignment-section e! app selected-partner employee]])

(defn partners-page-router
  [e! {:keys [query params] :as app} contract]
  (let [selected-partner-id (:partner query)
        selected-partner (->> (:company-contract/_contract contract)
                           (filter
                             (fn [partner]
                               (= (str (:teet/id partner)) selected-partner-id)))
                           first)

        selected-person-id (:user-id query)
        employee (->> (:company-contract/employees selected-partner)
                      (filter (fn [employee]
                                (= (str (:user/id (:company-contract-employee/user employee))) selected-person-id)))
                      first)]
    (case (keyword (:page query))
      :add-partner
      [new-partner-form e! contract (get-in app [:forms :new-partner])]
      :partner-info
      [partner-info e! app selected-partner]
      :edit-partner
      [edit-partner-form e! app contract selected-partner]
      :add-personnel
      [authorization-check/when-authorized
       :thk.contract/add-contract-employee selected-partner
       [add-personnel-form e! (:query app) selected-partner]]
      :personnel-info
      [employee-info e! app selected-partner employee]
      :assign-key-person
      [key-employee-info e! app selected-partner employee]
      :edit-personnel
      [authorization-check/when-authorized
       :thk.contract/add-contract-employee selected-partner
       [edit-personnel-form e! app selected-partner employee]]
      [partners-default-view params contract])))

;; navigated to through routes.edn from route /contracts/*****/partners
(defn partners-page
  [e! app contract]
  [:div {:class (<class common-styles/flex-column-1)}
   [contract-common/contract-heading e! app contract]
   [:div {:class (<class contract-style/partners-page-container)}
    [Grid {:container true}
     [Grid {:item true
            :xs 3
            :class (herb/join (<class common-styles/padding 1.5)
                              (<class contract-style/contract-partners-panel-style))}
      [partner-right-panel e! app contract]]
     [Grid {:item true
            :xs :auto
            :class (herb/join (<class common-styles/padding 1.5)
                              (<class common-styles/flex-1))}
      [partners-page-router e! app contract]]]]])
