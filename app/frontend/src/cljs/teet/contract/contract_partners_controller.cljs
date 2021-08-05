(ns teet.contract.contract-partners-controller
  (:require [tuck.core :as t]
            tuck.effect
            [teet.common.common-controller :as common-controller]
            [teet.log :as log]
            [teet.localization :refer [tr tr-enum]]
            [teet.snackbar.snackbar-controller :as snackbar-controller]))

(defn- is-first-partner? [app]
  (-> app
    (get-in [:route :contract-partners :company-contract/_contract])
    count
    (= 0)))

(defn- init-form [app key company]
  (let [lead-partner? (or
                        (is-first-partner? app)
                        (:company-contract/lead-partner? company))]
    (assoc-in app [:forms key :company-contract/lead-partner?] lead-partner?)
    (assoc-in app [:forms key] company)))

(defrecord UpdateNewCompanyForm [form-data])
(defrecord UpdateEditCompanyForm [form-data])
(defrecord CancelAddNewCompany [])
(defrecord InitializeNewCompanyForm [])
(defrecord InitializeEditCompanyForm [company])
(defrecord DeletePartner [company])
(defrecord DeletePartnerSuccess [])
(defrecord SearchBusinessRegistry [form-key business-id])
(defrecord SearchBusinessRegistryError [form-key error])
(defrecord SelectCompany [company])
(defrecord SearchBusinessRegistryResult [form-key result])
(defrecord ChangePersonStatus [employee-id active?])
(defrecord PersonStatusChangeSuccess [])
(defrecord AssignKeyPerson [employee-id key-person?])
(defrecord AssignKeyPersonSuccess [key-person? result])

(extend-protocol t/Event
  PersonStatusChangeSuccess
  (process-event [_ app]
    (t/fx app
          (fn [e!]
            (e! (common-controller/map->NavigateWithExistingAsDefault
                  {:page :contract-partners
                   :query (:query app)})))
          (fn [e!]
            (common-controller/refresh-fx e!))))

  ChangePersonStatus
  (process-event [{employee-id :employee-id
                   active? :active?} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :thk.contract/change-person-status
           :payload {:employee-id employee-id
                     :active? active?}
           :success-message (if active?
                              (tr [:contract :partner :person-activated])
                              (tr [:contract :partner :person-deactivated]))
           :result-event ->PersonStatusChangeSuccess}))

  AssignKeyPersonSuccess
  (process-event [{:keys [key-person? _]} app]
    (t/fx app
          (fn [e!]
            (common-controller/refresh-fx e!))
          (fn [e!]
            (e! (common-controller/map->NavigateWithExistingAsDefault
                  {:page :contract-partners
                   :query (merge
                            (:query app)
                            {:page (if key-person?
                                     :assign-key-person
                                     :personnel-info)})})))))

  AssignKeyPerson
  (process-event [{:keys [employee-id key-person?]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :thk.contract/assign-key-person
           :payload {:employee-id employee-id
                     :key-person? key-person?}
           :success-message (if key-person?
                              (tr [:contract :partner :key-person-assigned])
                              (tr [:contract :partner :key-person-unassigned]))
           :result-event (partial ->AssignKeyPersonSuccess key-person?)}))

  UpdateNewCompanyForm
  (process-event [{form-data :form-data} app]
    (update-in app [:forms :new-partner] merge form-data))

  UpdateEditCompanyForm
  (process-event [{form-data :form-data} app]
    (update-in app [:forms :edit-partner] merge form-data))

  InitializeNewCompanyForm
  (process-event [{} app]
    (init-form app :new-partner {:company/country :ee}))

  InitializeEditCompanyForm
  (process-event [{company :company} app]
    (init-form app :edit-partner company))

  CancelAddNewCompany
  (process-event [{} app]
    (t/fx app
          (fn [e!]
            (e! (->InitializeNewCompanyForm)))
          (fn [e!]
            (e! (common-controller/map->NavigateWithExistingAsDefault
                  {:page :contract-partners
                   :query {}})))))

  SearchBusinessRegistryResult
  (process-event [{:keys [form-key result]} app]
    (if result
      (-> app
        (update-in [:forms form-key] merge result)
        (assoc-in [:forms form-key :search-success?] true)
        (update-in [:forms form-key] dissoc :search-in-progress?)
        (snackbar-controller/open-snack-bar (tr [:partner :business-registry-data-successfully-updated])))
      (-> app
          (assoc-in [:forms form-key :no-results?] true)
          (update-in [:forms form-key] dissoc :search-in-progress?))))

  SearchBusinessRegistryError
  (process-event [{:keys [form-key error]} app]
    (log/warn "Business registry search failed on error: " error)
    (-> app
        (assoc-in [:forms form-key :exception-in-xroad?] true)
        (update-in [:forms form-key] dissoc :search-in-progress?)
        (snackbar-controller/open-snack-bar (tr [:partner :business-registry-search-failed]) :error)))

  SearchBusinessRegistry
  (process-event [{:keys [form-key business-id]} app]
    (t/fx (-> app
              (assoc-in [:forms form-key :search-in-progress?] true)
              (assoc-in [:forms form-key :business-id-used-in-search] business-id))
      {:tuck.effect/type :query
       :query :company/business-registry-search
       :args {:business-id business-id}
       :error-event (partial ->SearchBusinessRegistryError form-key)
       :result-event (partial ->SearchBusinessRegistryResult form-key)}))

  SelectCompany
  (process-event [{company :company} app]
    (update-in app [:forms :new-partner] merge company))

  DeletePartnerSuccess
  (process-event [_ app]
    (t/fx app
          (fn [e!]
            (e! (common-controller/map->NavigateWithExistingAsDefault
                  {:page :contract-partners
                   :query {}})))
          (fn [e!]
            (common-controller/refresh-fx e!))))

  DeletePartner
  (process-event [{company :company} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :thk.contract/delete-existing-company-from-contract-partners
           :payload {:company company}
           :success-message (tr [:contract :partner-deleted])
           :result-event ->DeletePartnerSuccess})))

