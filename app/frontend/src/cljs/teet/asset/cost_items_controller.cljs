(ns teet.asset.cost-items-controller
  (:require [teet.common.common-controller :as common-controller]
            [tuck.core :as t]
            [teet.snackbar.snackbar-controller :as snackbar-controller]
            [teet.localization :refer [tr]]
            [teet.util.collection :as cu]
            [teet.log :as log]))


(defrecord SaveCostItem [form-data])
(defrecord SaveCostItemResponse [response])
(defrecord DeleteComponent [id])
(defrecord DeleteComponentResponse [id response])
(defrecord SaveComponent [parent-id form-data])
(defrecord SaveComponentResponse [response])

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
     common-controller/refresh-fx))

  DeleteComponent
  (process-event [{id :id} app]
    (let [project-id (get-in app [:params :project])]
      (t/fx app
            {:tuck.effect/type :command!
             :command :asset/delete-component
             :payload {:db/id id
                       :project-id project-id}
             :result-event (partial ->DeleteComponentResponse id)})))

  DeleteComponentResponse
  (process-event [{id :id} app]
    (snackbar-controller/open-snack-bar
     (cu/without #(and (map? %) (= id (:db/id %))) app)
     (tr [:asset :cost-item-component-deleted])))


  SaveComponent
  (process-event [{:keys [parent-id form-data]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :asset/save-component
           :payload {:project-id (get-in app [:params :project])
                     :parent parent-id
                     :component form-data}
           :result-event ->SaveComponentResponse}))

  SaveComponentResponse
  (process-event [response app]
    (log/info "save component response: " response)
    app))

(defn save-asset-event [form-data]
  #(->SaveCostItem @form-data))
