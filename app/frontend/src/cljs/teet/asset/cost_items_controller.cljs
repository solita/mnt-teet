(ns teet.asset.cost-items-controller
  (:require [teet.common.common-controller :as common-controller]
            [tuck.core :as t]
            [teet.snackbar.snackbar-controller :as snackbar-controller]
            [teet.localization :refer [tr]]
            [teet.util.collection :as cu]
            [teet.log :as log]))


(defrecord SaveCostItem [form-data])
(defrecord SaveCostItemResponse [tempid response])
(defrecord DeleteComponent [fetched-cost-item-atom id])
(defrecord DeleteComponentResponse [fetched-cost-item-atom id response])
(defrecord SaveComponent [parent-id form-data])
(defrecord SaveComponentResponse [tempid response])

(defrecord SelectLocationOnMap [current-location on-change])
(defrecord FetchLocation [points result-callback])
(defrecord FetchLocationResponse [result-callback response])

(extend-protocol t/Event

  SaveCostItem
  (process-event [{form-data :form-data} app]
    (let [project-id (get-in app [:params :project])
          fclass (get-in form-data [:feature-group-and-class 1 :db/ident])
          asset (-> form-data
                    (dissoc :feature-group-and-class :asset/components)
                    (assoc :asset/fclass fclass))]
      (t/fx app
            {:tuck.effect/type :command!
             :command :asset/save-cost-item
             :payload {:asset asset
                       :project-id project-id}
             :result-event (partial ->SaveCostItemResponse (:db/id asset))})))

  SaveCostItemResponse
  (process-event [{:keys [tempid response]} app]
    (apply t/fx
           (snackbar-controller/open-snack-bar app
                                               (tr [:asset :cost-item-saved]))
           (remove nil?
                   [(when (string? tempid)
                      {:tuck.effect/type :navigate
                       :page :cost-items
                       :params (:params app)
                       :query {:id (str (get-in response [:tempids tempid]))}})
                    common-controller/refresh-fx])))

  DeleteComponent
  (process-event [{:keys [fetched-cost-item-atom id]} app]
    (let [project-id (get-in app [:params :project])]
      (t/fx app
            {:tuck.effect/type :command!
             :command :asset/delete-component
             :payload {:db/id id
                       :project-id project-id}
             :result-event (partial ->DeleteComponentResponse fetched-cost-item-atom id)})))

  DeleteComponentResponse
  (process-event [{:keys [fetched-cost-item-atom id]} app]
    (swap! fetched-cost-item-atom
           (fn [cost-item]
             (cu/without #(and (map? %) (= id (:db/id %))) cost-item)))
    (snackbar-controller/open-snack-bar
     app
     (tr [:asset :cost-item-component-deleted])))


  SaveComponent
  (process-event [{:keys [parent-id form-data]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :asset/save-component
           :payload {:project-id (get-in app [:params :project])
                     :parent-id parent-id
                     :component (dissoc form-data :component/components)}
           :result-event (partial ->SaveComponentResponse (:db/id form-data))}))

  SaveComponentResponse
  (process-event [{:keys [tempid response]} app]
    (apply t/fx app
           (remove nil?
                   [(when (string? tempid)
                      {:tuck.effect/type :navigate
                       :page :cost-items
                       :params (:params app)
                       :query (merge (:query app)
                                     {:component (get-in response [:tempids tempid])})})
                    common-controller/refresh-fx])))

  SelectLocationOnMap
  (process-event [{:keys [current-location on-change]} app]
    (common-controller/update-page-state
     app [:select-location]
     (constantly {:current-location current-location
                  :on-change on-change})))

  FetchLocation
  (process-event [{:keys [points result-callback]} app]
    (t/fx app
          (merge
           {:tuck.effect/type :query
            :result-event (partial ->FetchLocationResponse result-callback)}
           (if (> (count points) 1)
             {:query :road/by-2-geopoints
              :args {:start (first points)
                     :end (second points)
                     :distance 100}}
             {:query :road/by-geopoint
              :args {:point (first points)
                     :distance 100}}))))

  FetchLocationResponse
  (process-event [{:keys [result-callback response]} app]
    (def *r response)
    (let [road (first (sort-by :distance response))
          {:keys [road-nr carriageway start-m end-m geometry]} road
          g (some-> geometry js/JSON.parse)
          [start end] (case (aget g "type")
                        "LineString" [(first (aget g "coordinates"))
                                      (last (aget g "coordinates"))]
                        "Point" [(aget g "coordinates") nil]
                        [nil nil])]
      (result-callback [start end road-nr carriageway start-m end-m])
      (common-controller/update-page-state
       app [:select-location]
       merge {:road road
              :start start
              :end end
              :geojson g}))))

(defn save-asset-event [form-data]
  #(->SaveCostItem @form-data))
