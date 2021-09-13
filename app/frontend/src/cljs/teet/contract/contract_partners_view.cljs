(ns teet.contract.contract-partners-view
  (:require [clojure.string :as str]
            [herb.core :refer [<class] :as herb]
            [reagent.core :as r]
            [taoensso.timbre :as log]
            [teet.app-state :as app-state]
            [teet.authorization.authorization-check :as authorization-check]
            [teet.common.common-controller :as common-controller]
            [teet.common.common-styles :as common-styles]
            [teet.contract.contract-common :as contract-common]
            [teet.contract.contract-partners-controller :as contract-partners-controller]
            teet.contract.contract-spec
            [teet.contract.contract-style :as contract-style]
            [teet.file.file-controller :as file-controller]
            [teet.file.file-view :as file-view]
            [teet.localization :refer [tr tr-enum]]
            [teet.routes :as routes]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.buttons :as buttons]
            [teet.ui.common :as common]
            [teet.ui.date-picker :as date-picker]
            [teet.ui.file-upload :as file-upload]
            [teet.ui.form :as form]
            [teet.ui.format :as format]
            [teet.ui.icons :as icons]
            [teet.ui.material-ui :refer [Grid Divider]]
            [teet.ui.select :as select]
            [teet.ui.table :as table]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.typography :as typography]
            [teet.ui.util :refer [mapc]]
            [teet.ui.validation :as validation]
            [teet.user.user-model :as user-model]
            [teet.util.date :as date]
            [teet.util.datomic :as du]))

(defn partner-listing
  [{:keys [params query]} contract-partners]
  [:div {:class (<class common-styles/margin-top 2)}
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

(defn- add-company-button [button-component params contract]
  [authorization-check/when-authorized
   :thk.contract/add-new-contract-partner-company contract
   [button-component
    {:start-icon (r/as-element [icons/content-add])
     :href (routes/url-for {:page :contract-partners
                            :params params
                            :query {:page :add-partner}})}
    (tr [:contract :add-company])]])

(defn partner-right-panel
  [e! {:keys [params] :as app} contract]
  [:div
   [typography/Heading2
    {:class (<class common-styles/margin-bottom 2)}
    (tr [:contract :partner-information])]
   [add-company-button buttons/small-button-secondary params contract]
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
                :underlined? true
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
         [buttons/button-secondary {:disabled disabled?
                                    :class "cancel"
                                    :on-click cancel}
          (tr [:buttons :cancel])])
       (if (and estonia-selected? (not search-success?) (not existing-company?))
         (when (not search-disabled?)
           [buttons/button-primary {:style {:margin-left "1rem"}
                                    :on-click (e! contract-partners-controller/->SearchBusinessRegistry :new-partner
                                                                             (:company/business-registry-code form-value))}
                                       (tr [:buttons :search])])
         (when validate
           [buttons/button-primary {:style {:margin-left "1rem"}
                                    :disabled disabled?
                                    :type :submit
                                    :class "submit"
                                    :on-click validate}
            (tr [:buttons :save])]))]]]))

(defn foreign-fields
  []
  [:div
   ^{:key "foreign-code"}
   [form/field {:attribute :company/business-registry-code
                :validate validation/not-empty?}
    [TextField {}]]
   [form/field {:attribute :company/name
                :validate validation/not-empty?}
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
    [add-company-button buttons/button-primary params contract]]])

(defn key-person-approvals-panel
  [e! {:keys [params query] :as app} employees active?]
  [:div {:class (<class contract-style/personnel-table-style)}
   [:h4 (str "Approvals")]])

(defn- get-personnel-info-page [employee]
  (let [key-person? (get-in employee [:company-contract-employee/key-person?])]
    (if key-person?
      :assign-key-person
      :personnel-info)))

(def ^:private key-person-status->icon-data
  {:key-person.status/rejected {:i18n-key [:contract :employee :key-person-rejected]
                                :icon [icons/red-rejected]
                                :color theme-colors/red
                                :bg-color theme-colors/red-lightest}
   :key-person.status/approved {:i18n-key [:contract :employee :key-person-approved]
                                :icon [icons/green-check]
                                :color theme-colors/green
                                :bg-color theme-colors/mint-cream}
   :key-person.status/assigned {:i18n-key [:contract :employee :key-person-not-submitted]
                                :icon [icons/key-person]
                                :color theme-colors/text-disabled
                                :bg-color theme-colors/black-coral-1}
   :key-person.status/approval-requested {:i18n-key [:contract :employee :key-person-is-waiting-approvals]
                                          :icon [icons/key-person theme-colors/orange]
                                          :color theme-colors/orange
                                          :bg-color theme-colors/dark-tangerine-1}})

(defn key-person-icon
  "Displays the key-person icon based on the status"
  ([key-person-status] (key-person-icon key-person-status nil))
  ([key-person-status text]
   (let [{:keys [i18n-key icon color bg-color]} (key-person-status->icon-data key-person-status)]
     [common/popper-tooltip {:title (tr i18n-key)
                             :variant :info}
      [:div {:class (<class contract-style/key-person-icon-style bg-color)}
       [:icon {:style {:line-height 0}}
        icon]
       (when text
         [:span {:style {:color color}} text])]])))

(defn- toggle-employee-active-button [e! partner-company employee active?]
  (let [action-text (tr [:contract :partner (if active? :deactivate :activate)])]
    [authorization-check/when-authorized
     :thk.contract/add-contract-employee (:company-contract/company partner-company)
     [buttons/button-with-confirm
      {:action (e! contract-partners-controller/->ChangePersonStatus
                   (:db/id employee) (not active?))
       :modal-title (str action-text "?")
       :modal-text (tr [:contract :partner :change-status-text])
       :close-on-action? true
       :confirm-button-text action-text
       :confirm-button-style (if active? buttons/button-warning buttons/button-green)}
      [(if active? buttons/button-warning buttons/button-green)
       {:id (str "person-status-btn-" (:db/id employee))
       :size :small
       :class (<class contract-style/personnel-activation-link-style active?)}
      (if active? (tr [:contract :partner :deactivate]) (tr [:contract :partner :activate]))]]]))

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
    (for [employee employees
          :let [key-person? (:company-contract-employee/key-person? employee)
                key-person-status (-> employee :company-contract-employee/key-person-status :key-person/status)]]
      [[(-> employee :company-contract-employee/user user-model/user-name)]
       (if (not-empty (:company-contract-employee/role employee))
         [(str/join ", " (mapv #(tr-enum %) (:company-contract-employee/role employee)))]
         [])
       [(if key-person?
          [:div {:style {:display :flex}}
           [key-person-icon key-person-status]]
          [:span])]
       [[toggle-employee-active-button e! selected-partner employee active?]]
       [[buttons/small-button-secondary
         {:href (routes/url-for {:page :contract-partners
                                 :params (merge
                                           params
                                           {:employee employee})
                                 :query (merge
                                          query
                                          {:page (get-personnel-info-page employee)
                                           :user-id (get-in employee [:company-contract-employee/user :user/id])})})}
         (tr [:common :view-more-info])]]])]])

(defn add-contract-employee-button
  [partner-company params query]
  [authorization-check/when-authorized
   :thk.contract/add-contract-employee (:company-contract/company partner-company)
   [buttons/button-secondary {:start-icon (r/as-element [icons/content-add])
                              :href (routes/url-for {:page :contract-partners
                                                     :params params
                                                     :query (merge
                                                             query
                                                             {:page :add-personnel})})}
    (tr [:contract :add-person])]])

(defn personnel-section
  [e! {:keys [params query] :as app} selected-partner]
  [:div {:class (<class contract-style/personnel-section-style)}
   [:div {:class (<class contract-style/personnel-section-header-style)}
    [typography/Heading2 (tr [:contract :persons])]
    [add-contract-employee-button selected-partner params query]]
   [employee-table e! app (filterv #(:company-contract-employee/active? %)
                                   (:company-contract/employees selected-partner))
    selected-partner true]
   [employee-table e! app (filterv #(not (:company-contract-employee/active? %))
                                   (:company-contract/employees selected-partner))
    selected-partner false]])

(defn info-personnel-section
  [e! _app selected-partner employee]
  [:div {:class (<class contract-style/personnel-section-style)}
   [Grid {:container true
          :direction :row-reverse
          :justify-content :flex-start
          :align-items :center}
    [:div {:style {:padding-top :1rem}}
     [authorization-check/when-authorized
      :thk.contract/assign-key-person (:company-contract/company selected-partner)
      [buttons/button-secondary {:start-icon (r/as-element [icons/content-add])
                                 :on-click (e!
                                             contract-partners-controller/->AssignKeyPerson
                                             (:db/id employee)
                                             true)}
       (tr [:buttons :assign-as-key-person])]]]]])

(defn key-person-files
  "Displays the file list for the key person"
  [e! employee selected-partner]
  [:div {:id (str "key-person-" (:db/id employee))
         :style {:max-width "800px"}}
   [:div {:class (<class common-styles/flex-row-w100-space-between-center)}
    [:h3 {:class (<class common-styles/margin-top 3)}
     (tr [:contract :partner :key-person-files])]]
   [:div
    (mapc (fn [file]
            [file-view/file-row2
             {:attached-to [:company-contract-employee (:db/id employee)]
              :title-downloads? true
              :delete-when-authorized-wrapper (r/partial
                                                authorization-check/when-authorized
                                                :thk.contract/add-contract-employee
                                                (:company-contract/company selected-partner))
              :delete-action (fn [file]
                               (e! (contract-partners-controller/->RemoveFileLink
                                     (:db/id employee)
                                     (:db/id file))))}
             file])
          (:company-contract-employee/attached-files employee))]
   [:div {:class (<class common-styles/margin 1 0 1 0)}
    [authorization-check/when-authorized :thk.contract/edit-contract-employee
     (:company-contract/company selected-partner)
     [file-upload/FileUploadButton
      {:id "keyperson-files-field"
       :drag-container-id (str "key-person-" (:db/id employee))
       :color :secondary
       :button-attributes {:size :small}
       :on-drop #(e! (file-controller/map->UploadFiles
                       {:files %
                        :project-id nil
                        :user-attachment? true
                        :attach-to (:db/id employee)
                        :on-success common-controller/->Refresh}))}
      (str "+ " (tr [:buttons :upload]))]]]])

(defn- approval-form
  [e! employee-eid save-event required? close-event form-atom]
  [form/form {:e! e!
              :value @form-atom
              :on-change-event (form/update-atom-event form-atom merge)
              :cancel close-event
              :save-event (save-event employee-eid close-event form-atom)}
   ^{:attribute :key-person/approval-comment
     :required? required?}
   [TextField {:multiline true}]])

(defn approval-actions
  [e! employee]
  [:div
   (when-not (contract-partners-controller/contract-employee-approved? employee)
     [form/form-modal-button {:form-component [approval-form e! (:db/id employee)
                                               contract-partners-controller/approve-key-person
                                               false ;; comment not required
                                               ]
                              :form-value {:employee-id (:db/id employee)}
                              :modal-title (tr [:contract :partner :approve-person-modal-title])
                              :button-component [buttons/button-green {:style {:margin-right "1rem"}}
                                                 (tr [:contract :partner :approve-person-button])]}])
   (when-not (contract-partners-controller/contract-employee-rejected? employee)
     [form/form-modal-button {:form-component [approval-form e! (:db/id employee)
                                               contract-partners-controller/reject-key-person
                                               true ;; comment required
                                               ]
                              :form-value {:employee-id (:db/id employee)}
                              :modal-title (tr [:contract :partner :reject-person-modal-title])
                              :button-component [buttons/button-warning {}
                                                 (tr [:contract :partner :reject-person-button])]}])])

(defn key-person-approvals-status [status comment modification-meta key-person?]
  [:div
   [:div.person-appproval {:class (<class common-styles/flex-row)}
    [:div.person-appproval-approver {:class (<class common-styles/flex-table-column-style 30)}

     [:span
      (tr [:contract :partner :tram-approver])]]
    [:div.person-appproval-status {:class (<class common-styles/flex-table-column-style 70 :space-between)}
     (if key-person?
       [:<>
        [key-person-icon status]
        [:span {:style {:margin "0 0.4rem"}}
         (tr-enum status)]]
       [:<>
        [:span]
        [:span {:style {:margin "0 0.4rem"}}
         (tr [:contract :partner :key-person-assignment-removed])]])]]

   [:div.person-appproval-comment {:class (<class common-styles/margin-bottom 1)}
    (when comment
      [:<>
       [:strong (tr [:comment :comment]) ":"]
       [:p {:style {:margin-bottom "0.5rem"}}
        comment]])
    (let [[time user] modification-meta]
      [typography/SmallGrayText
       (user-model/user-name user) " "
       (tr [:contract :partner :on]) " "
       (format/date-time-with-seconds time)])]])

(defn- edit-license-form [e! employee-id close-event form-atom]
  [form/form {:e! e!
              :value @form-atom
              :on-change-event (form/update-atom-event form-atom merge)
              :save-event #(contract-partners-controller/->SaveLicense
                            employee-id @form-atom (close-event))
              :cancel-event close-event
              :delete (contract-partners-controller/->DeleteLicense employee-id @form-atom close-event)}
   ^{:attribute :user-license/name :required? true}
   [TextField {}]

   ^{:attribute :user-license/expiration-date}
   [date-picker/date-input {}]

   ^{:attribute :user-license/link}
   [TextField {}]])

(defn- key-person-licenses [e! {licenses :company-contract-employee/attached-licenses :as employee}
                            selected-partner]
  (r/with-let [show-history? (r/atom false)]
    [:div {:class (<class common-styles/margin-bottom 1)}
     [typography/Heading3 {:class (<class common-styles/margin-bottom 1)}
      (tr [:contract :partner :key-person-licenses])]

     [:div {:class (<class common-styles/margin-bottom 1)}

      [:div {:class (<class common-styles/flex-row-space-between)}
       [:div {:class (<class common-styles/flex-table-column-style 60 :flex-start 0 nil)}
        [typography/BoldGrayText (tr [:fields :user-license/name])]]
       [:div {:class (<class common-styles/flex-table-column-style 30 :flex-start 0 nil)}
        [typography/BoldGrayText (tr [:fields :user-license/expiration-date])]]
       [:div {:class (<class common-styles/flex-table-column-style 10 :flex-start 0 nil)}]]

      (mapc
       (fn [{:user-license/keys [name expiration-date link] :as license}]
         [:div {:id (str "user-license-" (:db/id license))
                :class (<class common-styles/flex-row-space-between)}
          [:div {:class (<class common-styles/flex-table-column-style 60)}
           (if (str/blank? link)
             name
             [common/Link {:href link} name])]
          [:div {:class (<class common-styles/flex-table-column-style 30)}
           (format/date expiration-date)]
          [:div {:class (<class common-styles/flex-table-column-style 10)}
           [authorization-check/when-authorized :thk.contract/save-license (:company-contract/company selected-partner)
            [form/form-modal-button
             {:form-component [edit-license-form e! (:db/id employee)]
              :form-value license
              :modal-title (tr [:contract :partner :edit-license-title])
              :button-component [buttons/link-button-with-icon {:icon [icons/content-create]}
                                 (tr [:buttons :edit])]}]]]])

       ;; Show licenses in chronological order, latest expiration first,
       ;; removing expired if not showing history
       (->> licenses
            (sort-by :user-license/expiration-date
                     (fn [a b]
                       (cond
                         (nil? a) -1
                         (nil? b) 1
                         (> (.getTime a) (.getTime b)) -1
                         (< (.getTime a) (.getTime b)) 1
                         :else 0)))
            (remove (if @show-history?
                      (constantly false)
                      (fn [{exp :user-license/expiration-date}]
                        (and (some? exp)
                             (date/date-before-today? exp)))))))]
     [:div {:class (<class common-styles/flex-row-end)
            :style {:display :flex :flex-direction :row
                    :justify-content "flex-end"}}
      [buttons/button-secondary {:on-click #(swap! show-history? not) :size :small
                                 :style {:margin-right "1em"}}
       (tr [:contract :partner (if @show-history?
                                 :hide-license-history
                                 :view-license-history)])]
      [authorization-check/when-authorized :thk.contract/save-license
       (:company-contract/company selected-partner)
       [form/form-modal-button
        {:form-component [edit-license-form e! (:db/id employee)]
         :modal-title (tr [:contract :partner :add-license-title])
         :button-component [buttons/button-secondary {:size :small}
                            (tr [:contract :partner :add-license])]}]]]]))

(defn- remove-key-person-assignment-button [e! selected-partner employee]
  [authorization-check/when-authorized
   :thk.contract/assign-key-person (:company-contract/company selected-partner)
   [buttons/delete-button-with-confirm
    {:action (e! contract-partners-controller/->AssignKeyPerson (:db/id employee) false)
     :underlined? true
     :confirm-button-text (tr [:contract :delete-button-text])
     :cancel-button-text (tr [:contract :cancel-button-text])
     :modal-title (tr [:contract :are-you-sure-remove-key-person-assignment])
     :modal-text (tr [:contract :confirm-remove-key-person-text])}
    (tr [:buttons :remove-key-person-assignment])]])

(defn- submit-key-person-button [e! employee]
  [buttons/button-secondary
   {:onClick (e! contract-partners-controller/->SubmitKeyPerson (:db/id employee))
    :confirm-button-text (tr [:contract :delete-button-text])
    :cancel-button-text (tr [:contract :cancel-button-text])
    :modal-title (tr [:contract :are-you-sure-remove-key-person-assignment])
    :modal-text (tr [:contract :confirm-remove-key-person-text])}
   (tr [:buttons :submit-key-person])])

(defn key-person-assignment-section
  [e! _ selected-partner employee]
  (r/with-let [show-history? (r/atom false)]
    (let [status-entity (:company-contract-employee/key-person-status employee)
          status (du/enum->kw (:key-person/status status-entity))
          comment (:key-person/approval-comment status-entity)
          modification-meta [(:meta/modified-at status-entity) (:meta/modifier status-entity)]]
      [:div {:class ""}
       [:div {:class (<class contract-style/key-person-assignment-header)}
        [typography/Heading1 (tr [:contract :employee :key-person-approvals])]
        [remove-key-person-assignment-button e! selected-partner employee]]
       [key-person-files e! employee selected-partner]
       [key-person-licenses e! employee selected-partner]
       [:div {:class (<class common-styles/margin 1 0 1 0)
              :style {:max-width "800px"}}
        [:div {:class (<class common-styles/flex-row-w100-space-between-center)
               :style {:margin-bottom "0.5rem"}}
         [:h3 (tr [:contract :employee :approvals])]
         (when (seq (:company-contract-employee/key-person-status-history employee))
           [:div
            [buttons/button-secondary {:size :small
                                       :on-click #(swap! show-history? not)}
             (if @show-history?
               (tr [:admin :hide-history])
               (tr [:admin :show-history]))]])]
        (if @show-history?
          ;; Show all kps entries
          [:div
           (mapc
            (fn [{:company-contract-employee/keys [key-person-status key-person?]
                  :meta/keys [modified-at modifier]}]
              (let [status (du/enum->kw (:key-person/status key-person-status))
                    comment (:key-person/approval-comment key-person-status)
                    modification-meta (if key-person?
                                        [(:meta/modified-at key-person-status) (:meta/modifier key-person-status)]
                                        [modified-at modifier])]
                [key-person-approvals-status status comment modification-meta key-person?]))
            (:company-contract-employee/key-person-status-history employee))]

          ;; Show only the current
          [key-person-approvals-status status comment modification-meta true])
        [:div {:class (<class common-styles/flex-row-space-between)}
         [authorization-check/when-authorized :thk.contract/submit-key-person (:company-contract/company selected-partner)
          [:div {:class (<class contract-style/key-person-assignment-header)}
           (when (#{:key-person.status/assigned :key-person.status/rejected} status)
             [submit-key-person-button e! employee])]]
         (when (#{:key-person.status/approval-requested :key-person.status/rejected :key-person.status/approved} status)
           [authorization-check/when-authorized :thk.contract/approve-key-person (:company-contract/company selected-partner)
            [approval-actions e! employee]])]]])))

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
  (let [save-disabled? (and
                         (not (boolean (:company-contract-employee/user form-value)))
                         (not (boolean (:company-contract-employee/role form-value)))
                         (not (boolean (:user/given-name form-value)))
                         (not (boolean (:user/family-name form-value)))
                         (not (boolean (:user/email form-value)))
                         (not (boolean (:user/person-id form-value))))]
    [:div {:class (<class form/form-buttons)}
     [:div {:style {:margin-left :auto
                    :text-align :center}}
      [:div {:class (<class common-styles/margin-bottom 1)}
       (when cancel
         [buttons/button-secondary {:disabled disabled?
                                    :class "cancel"
                                    :on-click cancel}
          (tr [:buttons :cancel])])
       (when validate
         [buttons/button-primary {:style {:margin-left "1rem"
                                          :display (if save-disabled?
                                                     :none
                                                     :inline-block)}
                                  :disabled disabled?
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
                :required? true}
    [TextField {:disabled personal-info-disabled?}]]
   [form/field {:attribute :user/email
                :required? true
                :validate validation/validate-email-optional}
    [TextField {:disabled personal-info-disabled?}]]
   [form/field :user/phone-number
    [TextField {:disabled personal-info-disabled?}]]
   [form/field {:attribute :company-contract-employee/role
                :required? true}
    [select/select-user-roles-for-contract
     {:e! e!
      :error-text (tr [:contract :role-required])
      :required true
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
                           :required? true}
               [select/select-search
                {:e! e!
                 :query (fn [text]
                          {:args {:search (clojure.string/lower-case text)
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
              :required true
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
        roles (when-let [roles (:company-contract-employee/role selected-person)]
                (set roles))
        key-person? (:company-contract-employee/key-person? selected-person)
        personal-info-disabled? (:user/last-login user)]
    (r/with-let
      [form-atom (r/atom (merge {:user/person-id person-id
                                 :user/email email
                                 :user/phone-number phone-number
                                 :user/given-name given-name
                                 :user/family-name family-name
                                 :user/id user-id}
                                (when roles
                                  {:company-contract-employee/role roles})))]
      [Grid {:container true}
       [Grid {:item true :xs 12 :md 6}
        [form/form2 {:e! e!
                     :value @form-atom
                     :on-change-event (form/update-atom-event form-atom (fn [old new]
                                                                          (if (= {:company-contract-employee/role #{}} new)
                                                                            (dissoc old :company-contract-employee/role)
                                                                            (merge old new))))
                     :cancel-event #(common-controller/map->NavigateWithExistingAsDefault
                                     {:query (merge query
                                                    {:page (if key-person?
                                                             :assign-key-person
                                                             :personnel-info)
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
                                                                                                  {:page (if key-person?
                                                                                                           :assign-key-person
                                                                                                           :personnel-info)
                                                                                                   :user-id user-id})}))))
                                                                                (tr [:contract :partner :person-updated]))}
         [typography/Heading1 {:class (<class common-styles/margin-bottom 1.5)}
          (tr [:contract :partner :edit-person])]
         [new-person-form-fields e! (:form-value @form-atom) personal-info-disabled?]
         [form/footer2 (partial contract-personnel-form-footer @form-atom)]]]])))

(defn partner-info-header
  [partner params]
  (let [partner-name (get-in partner [:company-contract/company :company/name])
        lead-partner? (:company-contract/lead-partner? partner)
        teet-id (:teet/id partner)
        company (:company-contract/company partner)]
    [:div {:class (<class contract-style/partner-info-header)}
     [:h1 partner-name]
     (when lead-partner?
       [common/primary-tag (tr [:contract :lead-partner])])
     [authorization-check/when-authorized
      :thk.contract/edit-contract-partner-company company
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
        user-id (get-in employee [:company-contract-employee/user :user/id])
        key-person? (get-in employee [:company-contract-employee/key-person?])
        key-person-status (get-in employee [:company-contract-employee/key-person-status :key-person/status])]
    [:div {:class (<class contract-style/partner-info-header)}
     [Grid
      {:container true :direction :row :justify-content :flex-start :align-items :center}
      [Grid {:style {:padding-right :1em}}
       [:h1 employee-name]]
      (if key-person?
        [key-person-icon key-person-status (tr [:contract :employee :key-person])]
        [:span])]
     [authorization-check/when-authorized
      :thk.contract/edit-contract-employee (:company-contract/company selected-partner)
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
       :thk.contract/add-contract-employee (:company-contract/company selected-partner)
       [add-personnel-form e! (:query app) selected-partner]]
      :personnel-info
      [employee-info e! app selected-partner employee]
      :assign-key-person
      [key-employee-info e! app selected-partner employee]
      :edit-personnel
      [authorization-check/when-authorized
       :thk.contract/add-contract-employee (:company-contract/company selected-partner)
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
