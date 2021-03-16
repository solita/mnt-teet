(ns teet.asset.cost-items-controller
  (:require [teet.common.common-controller :as common-controller]
            [tuck.core :as t]
            [teet.snackbar.snackbar-controller :as snackbar-controller]
            [teet.localization :refer [tr]]))


(defrecord SaveCostItem [form-data])
(defrecord SaveCostItemResponse [response])

(extend-protocol t/Event

  SaveCostItem
  (process-event [{form-data :form-data} app]
    (let [project-id (get-in app [:params :project])
          fclass (get-in form-data [:feature-group-and-class 1 :db/ident])
          asset (-> form-data
                    (dissoc :feature-group-and-class)
                    (assoc :asset/fclass fclass))]
      (t/fx app
            {:tuck.effect/type :command!
             :command :asset/save-cost-item
             :payload {:asset asset
                       :project-id project-id}
             :result-event ->SaveCostItemResponse})))

  SaveCostItemResponse
  (process-event [{response :response} app]
    (t/fx
     (snackbar-controller/open-snack-bar app
                                         (tr [:asset :cost-item-saved]))
     common-controller/refresh-fx)))

(defn save-asset-event [form-data]
  #(->SaveCostItem @form-data))
