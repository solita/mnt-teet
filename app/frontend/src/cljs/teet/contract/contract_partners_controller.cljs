(ns teet.contract.contract-partners-controller
  (:require [tuck.core :as t]
            tuck.effect
            [teet.common.common-controller :as common-controller]
            [teet.log :as log]))

(defrecord UpdateNewCompanyForm [form-data])
(defrecord UpdateEditCompanyForm [form-data])
(defrecord CancelAddNewCompany [])
(defrecord InitializeNewCompanyForm [])
(defrecord InitializeEditCompanyForm [])
(defrecord SearchBusinessRegistry [business-id])
(defrecord SearchBusinessRegistryError [error])
(defrecord SelectCompany [company])
(defrecord SearchBusinessRegistryResult [result])

(extend-protocol t/Event
  UpdateNewCompanyForm
  (process-event [{form-data :form-data} app]
    (update-in app [:forms :new-partner] merge form-data))

  UpdateEditCompanyForm
  (process-event [{form-data :form-data} app]
    ; @TODO: implement
    (println "UpdateEditCompanyForm" form-data))

  InitializeNewCompanyForm
  (process-event [{} app]
    (let [first-partner? (-> app
                             (get-in [:route :contract-partners :company-contract/_contract])
                             count
                             (= 0))]
      (if first-partner?
        (-> app
            (assoc-in [:forms :new-partner] {:company/country :ee})
            (assoc-in [:forms :new-partner :company-contract/lead-partner?] true))
        (assoc-in app [:forms :new-partner] {:company/country :ee}))))

  InitializeEditCompanyForm
  (process-event [{} app]
    ; @TODO: implement
    (println "Edit Company Form initialized"))

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
  (process-event [{result :result} app]
    (if result
      (-> app
          (update-in [:forms :new-partner] merge result)
          (assoc-in [:forms :new-partner :search-success?] true)
          (update-in [:forms :new-partner] dissoc :search-in-progress?))
      (-> app
          (assoc-in [:forms :new-partner :no-results?] true)
          (update-in [:forms :new-partner] dissoc :search-in-progress?))))

  SearchBusinessRegistryError
  (process-event [{error :error} app]
    (log/warn "Business registry search failed on error: " error)
    (-> app
        (assoc-in [:forms :new-partner :exception-in-xroad?] true)
        (update-in [:forms :new-partner] dissoc :search-in-progress?)))

  SearchBusinessRegistry
  (process-event [{business-id :business-id} app]
    (t/fx (assoc-in app [:forms :new-partner :search-in-progress?] true)
          {:tuck.effect/type :query
           :query :company/business-registry-search
           :args {:business-id business-id}
           :error-event ->SearchBusinessRegistryError
           :result-event ->SearchBusinessRegistryResult}))

  SelectCompany
  (process-event [{company :company} app]
    (update-in app [:forms :new-partner] merge company)))
