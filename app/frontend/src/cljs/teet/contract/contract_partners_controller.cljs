(ns teet.contract.contract-partners-controller
  (:require [tuck.core :as t]
            tuck.effect
            [teet.common.common-controller :as common-controller]))

(defrecord UpdateNewCompanyForm [form-data])
(defrecord ClearNewCompanyForm [])
(defrecord CancelAddNewCompany [])
(defrecord SelectCompany [company])

(extend-protocol t/Event
  UpdateNewCompanyForm
  (process-event [{form-data :form-data} app]
    (update-in app [:route :contract-partners :new-partner] merge form-data))

  ClearNewCompanyForm
  (process-event [{} app]
    (-> app
        (assoc-in [:route :contract-partners :new-partner] {:company/country :ee})))

  CancelAddNewCompany
  (process-event [{} app]
    (t/fx app
          (fn [e!]
            (e! (->ClearNewCompanyForm)))
          (fn [e!]
            (e! (common-controller/map->NavigateWithExistingAsDefault
                  {:page :contract-partners
                   :query {}})))))

  SelectCompany
  (process-event [{company :company} app]
    (assoc-in app [:route :contract-partners :new-partner] company)))
