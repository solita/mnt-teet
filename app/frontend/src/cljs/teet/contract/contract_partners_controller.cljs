(ns teet.contract.contract-partners-controller
  (:require [tuck.core :as t]
            tuck.effect
            [teet.common.common-controller :as common-controller]
            [teet.localization :refer [tr]]
            [teet.routes :as routes]
            [teet.log :as log]))

(defrecord UpdateNewCompanyForm [form-data])
(defrecord ClearNewCompanyForm [])
(defrecord CancelAddNewCompany [])
(defrecord SearchBusinessRegistry [business-id])
(defrecord SearchBusinessRegistryError [error])
(defrecord SelectCompany [company])
(defrecord SearchBusinessRegistryResult [result])

(defmethod routes/on-navigate-event :contract-partners
  [{:keys [query]}]
  ;; set default value to form when navigating to add-partner page
  (when (= (:page query) "add-partner")
    (->ClearNewCompanyForm)))

(extend-protocol t/Event
  UpdateNewCompanyForm
  (process-event [{form-data :form-data} app]
    (update-in app [:forms :new-partner] merge form-data))

  ClearNewCompanyForm
  (process-event [{} app]
    (-> app
        (assoc-in [:forms :new-partner] {:company/country :ee})))

  CancelAddNewCompany
  (process-event [{} app]
    (t/fx app
          (fn [e!]
            (e! (->ClearNewCompanyForm)))
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
    (assoc-in app [:forms :new-partner] company)))
