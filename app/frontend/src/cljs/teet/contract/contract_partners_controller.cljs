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
(defrecord SearchBusinessRegistry [form-key business-id])
(defrecord SearchBusinessRegistryError [form-key error])
(defrecord SelectCompany [company])
(defrecord SearchBusinessRegistryResult [form-key result])

(extend-protocol t/Event
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
    (t/fx (assoc-in app [:forms form-key :search-in-progress?] true)
      {:tuck.effect/type :query
       :query :company/business-registry-search
       :args {:business-id business-id}
       :error-event (partial ->SearchBusinessRegistryError form-key)
       :result-event (partial ->SearchBusinessRegistryResult form-key)}))

  SelectCompany
  (process-event [{company :company} app]
    (update-in app [:forms :new-partner] merge company))

  DeletePartner
  (process-event [{company :company} app]
    (cljs.pprint/pprint (str "Delete should be here for " company))))

