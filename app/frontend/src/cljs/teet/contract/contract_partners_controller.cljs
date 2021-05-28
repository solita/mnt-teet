(ns teet.contract.contract-partners-controller
  (:require [tuck.core :as t]
            tuck.effect
            [teet.common.common-controller :as common-controller]
            [teet.localization :refer [tr]]
            [teet.snackbar.snackbar-controller :as snackbar-controller]))

(defrecord UpdateNewCompanyForm [form-data])
(defrecord ClearNewCompanyForm [])
(defrecord CancelAddNewCompany [])

(extend-protocol t/Event
  UpdateNewCompanyForm
  (process-event [{form-data :form-data} app]
    (update-in app [:forms :new-partner] merge form-data))

  ClearNewCompanyForm
  (process-event [{} app]
    (assoc-in app [:forms :new-partner] {:company/country :ee}))

  CancelAddNewCompany
  (process-event [{} app]
    (t/fx app
          (fn [e!]
            (e! (->ClearNewCompanyForm)))
          (fn [e!]
            (e! (common-controller/map->NavigateWithExistingAsDefault
                  {:page :contract-partners
                   :query {}}))))))


