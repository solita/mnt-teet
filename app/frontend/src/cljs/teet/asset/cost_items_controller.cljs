(ns teet.asset.cost-items-controller
  (:require [teet.common.common-controller :as common-controller]
            [tuck.core :as t]
            [teet.snackbar.snackbar-controller :as snackbar-controller]
            [teet.localization :refer [tr]]
            [teet.util.collection :as cu]
            [teet.log :as log]
            [clojure.string :as str]
            [teet.asset.asset-model :as asset-model]
            [teet.routes :as routes]))

(defrecord AddComponentCancel [id])

(defmethod routes/on-leave-event :cost-item [{:keys [query new-query]}]
  (when (and (:component query)
             (not (:component new-query)))
    ;; Navigated away from component add form, cleanup state
    (->AddComponentCancel (:component query))))

(defonce next-id (atom 0))

(defn- next-id! [prefix]
  (str prefix (swap! next-id inc)))

(def ^:private road-address
  #(cu/map-vals
    (fn [x]
      (when-not (str/blank? x)
        (common-controller/->long x)))
    (select-keys % [:location/road-nr :location/carriageway
                    :location/start-m :location/end-m])))

(defn- point [v]
  (if (vector? v)
    ;; already a vector of points
    v
    ;; parse "x, y"
    (let [nums (mapv #(js/parseFloat %) (str/split v #"\s*,\s*"))]
      (when (and (= (count nums) 2)
                 (every? #(and (number? %)
                               (not (js/isNaN %))) nums))
        nums))))

(defn point-geojson [[x y] & {:as props}]
  #js {:type "Feature"
       :geometry #js {:type "Point"
                      :coordinates #js [x y]}
       :properties (clj->js props)})

(defn feature-collection-geojson [& features]
  #js {:type "FeatureCollection"
       :features (into-array (remove nil? features))})

(defrecord SaveCostItem [])
(defrecord SaveCostItemResponse [response])
(defrecord DeleteComponent [id])
(defrecord SaveComponent [component-id])
(defrecord SaveComponentResponse [response])

(defrecord UpdateForm [form-data])

;; Response when fetching location based on road address
(defrecord FetchLocationResponse [address response])

;; Response when fetching road address based on geopoints
(defrecord FetchRoadResponse [start-end-points response])

(defrecord AddComponent [type])

(defrecord SaveCostGroupPrice [cost-group price])
(defrecord SaveCostGroupPriceResponse [response])

;; Locking and versioning
(defrecord SaveBOQVersion [type explanation])
(defrecord UnlockForEdits [])


(declare process-location-change)

(defn- form-component-id [app]
  (let [id (get-in app [:params :id])]
    (or
     ;; component query parameter specifies new component to add
     (get-in app [:query :component])

     ;; editing existing component itself
     (and (asset-model/component-oid? id)
          id))))

(defn- form-state
  "Return the current form state."
  [app]
  (if-let [component-id (form-component-id app)]
    ;; Get some nested component by id
    (let [by-id (fn by-id [{oid :asset/oid :as c}]
                  (if (= component-id oid)
                    c
                    (some by-id (:component/components c))))]
      (some by-id
            (common-controller/page-state app :cost-item :asset/components)))

    ;; Get asset itself
    (common-controller/page-state app :cost-item)))

(defn- update-component [components component-id update-fn args]
  (mapv (fn [{oid :asset/oid :as c}]
          (let [c (if (= component-id oid)
                    (apply update-fn c args)
                    c)]
            (if-let [sub-components (:component/components c)]
              (assoc c :component/components
                     (update-component sub-components component-id update-fn args))
              c))) components))

(defn- update-form
  "Update form state, either the main asset or a component by id."
  [app update-fn & args]

  (if-let [component-id (form-component-id app)]
    ;; Update some nested component by id
    (common-controller/update-page-state
     app [:cost-item :asset/components]
     update-component component-id update-fn args)

    ;; Update asset itself
    (apply common-controller/update-page-state
           app [:cost-item] update-fn args)))

(extend-protocol t/Event

  SaveCostItem
  (process-event [_ {page :page :as app}]
    (let [form-data (form-state app)
          project-id (get-in app [:params :project])
          id (if (= "new" (get-in app [:params :id]))
               (next-id! "costitem")
               (:db/id form-data))
          asset (-> form-data
                    (assoc :db/id id)
                    (dissoc :fgroup :location/geojson))]
      (t/fx app
            {:tuck.effect/type :command!
             :command :asset/save-cost-item
             :payload {:asset asset
                       :project-id project-id}
             :result-event ->SaveCostItemResponse})))

  SaveCostItemResponse
  (process-event [{:keys [response]} {params :params :as app}]
    (let [oid (:asset/oid response)]
      (apply t/fx
             (snackbar-controller/open-snack-bar app
                                                 (tr [:asset :cost-item-saved]))
             (remove nil?
                     [(when (not= oid (:id params))
                        {:tuck.effect/type :navigate
                         :page :cost-item
                         :params (merge
                                  (:params app)
                                  {:id oid})})
                      common-controller/refresh-fx]))))

  DeleteComponent
  (process-event [{:keys [fetched-cost-item-atom id]} app]
    (let [project-id (get-in app [:params :project])]
      (t/fx app
            {:tuck.effect/type :command!
             :command :asset/delete-component
             :payload {:db/id id
                       :project-id project-id}
             :result-event common-controller/->Refresh})))

  SaveComponent
  (process-event [_ app]
    (let [form-data (form-state app)
          {parent-id :asset/oid}
          (-> (common-controller/page-state app :cost-item)
              (asset-model/find-component-path (:asset/oid form-data))
              butlast last)]
      (t/fx app
            {:tuck.effect/type :command!
             :command :asset/save-component
             :payload {:project-id (get-in app [:params :project])
                       :parent-id parent-id
                       :component (dissoc form-data
                                          :component/components
                                          :location/geojson)}
             :result-event ->SaveComponentResponse})))

  SaveComponentResponse
  (process-event [{:keys [response]} {params :params :as app}]
    (let [oid (:asset/oid response)]
      (apply t/fx app
             (remove nil?
                     [(when (not= oid (params :id))
                        {:tuck.effect/type :navigate
                         :page :cost-item
                         :params (merge (:params app)
                                        {:id oid})
                         :query nil})
                      common-controller/refresh-fx]))))

  UpdateForm
  (process-event [{:keys [form-data]} app]
    (let [old-form (form-state app)
          new-form (cu/deep-merge old-form form-data)]
      (process-location-change
       (update-form app (constantly new-form))
       old-form new-form)))

  ;; When road address was changed on the form, update geometry
  ;; and start/end points from the fetched response
  FetchLocationResponse
  (process-event [{:keys [address response]} app]
    (if-not (= address (road-address (form-state app)))
      (do
        (log/debug "Stale response for " address " => " response)
        app)

      (update-form
       app
       (fn [form]
         ;; Set start/end points and GeoJSON geometry
         (if (:location/end-m address)
           ;; Line geometry
           (assoc form
                  :location/start-point (first response)
                  :location/end-point (last response)
                  :location/geojson
                  (feature-collection-geojson
                   #js {:type "LineString"
                        :coordinates (clj->js response)}
                   (point-geojson (first response) "start/end" "start")
                   (point-geojson (last response) "start/end" "end")))
           ;; Point geometry
           (-> form
               (assoc :location/start-point response
                      :location/geojson
                      (feature-collection-geojson
                       (point-geojson response "start/end" "start")))
               (dissoc :location/end-point)))))))

  FetchRoadResponse
  (process-event [{:keys [start-end-points response]} app]
    (if-not (= start-end-points
               (select-keys (form-state app)
                            [:location/start-point :location/end-point]))
      (do
        (log/debug "Stale response for " start-end-points " => " response)
        app)

      (update-form
       (if (empty? response)
         (snackbar-controller/open-snack-bar app (tr [:asset :location :no-road-found-for-points]) :warning)
         app)
       (fn [form]
         (if (empty? response)
           (-> form
               (dissoc :location/road-nr :location/carriageway :location/start-m :location/end-m)
               (merge {:location/geojson (let [{:location/keys [start-point end-point]}
                                               (cu/map-vals point start-end-points)]
                                           (feature-collection-geojson
                                            (clj->js {:type "LineString"
                                                      :coordinates [start-point end-point]})
                                            (point-geojson start-point "start/end" "start")
                                            (point-geojson end-point "start/end" "end")))}))
           (let [{:keys [geometry road-nr carriageway start-m end-m m]}
                 (first (sort-by :distance response))]

             (when geometry
               (let [geojson (js/JSON.parse geometry)
                     start (when start-m (aget geojson "coordinates" 0))
                     end (when end-m (last (aget geojson "coordinates")))]
                 (log/debug "RECEIVED NEW START/END start: " start ", end: " end )

                 (merge form
                        (when (and start end)
                          {:location/start-point (vec start)
                           :location/end-point (vec end)})
                        {:location/geojson
                         #js {:type "FeatureCollection"
                              :features
                              (if end-m
                                #js [geojson
                                     (point-geojson start "start/end" "start")
                                     (point-geojson end "start/end" "end")]
                                #js [geojson])}
                         :location/road-nr road-nr
                         :location/carriageway carriageway
                         :location/start-m (or m start-m)
                         :location/end-m end-m})))))))))

  AddComponent
  (process-event [{type :type} {:keys [page params query] :as app}]
    ;; Can add subcomponent as well, add to correct path
    (let [new-id (next-id! "c")]
      (t/fx
       (update-form app
                    (fn [parent]
                      (update parent (if (:asset/fclass parent)
                                       :asset/components
                                       :component/components)
                              #(conj (or % [])
                                     {:db/id new-id
                                      :asset/oid new-id
                                      :component/ctype type}))))
       {:tuck.effect/type :navigate
        :params params
        :page page
        :query (merge query {:component new-id})})))

  AddComponentCancel
  (process-event [{id :id} app]
    (update-form
     app
     (fn [parent]
       (update parent (if (:asset/fclass parent)
                        :asset/components
                        :component/components)
               (fn [cs]
                 (filterv #(not= (:asset/oid %) id) cs)))))))

(defn- process-location-change
  "Check if location fields have been edited and need to retrigger
  Teeregister API calls."
  [app old-form new-form]
  (let [start-end-points #(select-keys % [:location/start-point :location/end-point])]

    (cond
      ;; Manually edited start/end points, refetch road section
      (not= (start-end-points old-form)
            (start-end-points new-form))
      (let [start (point (:location/start-point new-form))
            end (point (:location/end-point new-form))]
        (log/debug "START/END POINTS CHANGED" "start: " start ", end: " end)
        (cond
          ;; 2 geopoints specified
          (and start end)
          (t/fx app
                {:tuck.effect/type :query
                 :query :road/by-2-geopoints
                 :args {:distance 100 :start start :end end}
                 :result-event (partial ->FetchRoadResponse (start-end-points new-form))})

          start
          (t/fx app
                {:tuck.effect/type :query
                 :query :road/by-geopoint
                 :args {:distance 100 :point start}
                 :result-event (partial ->FetchRoadResponse (start-end-points new-form))})


          ;; No valid points, don't fetch anything and remove road address
          :else
          (dissoc app :location/road-nr :location/carriageway :location/start-m :location/end-m)))

      ;; Manually edited road location, refetch geometry and points
      (not= (road-address old-form)
            (road-address new-form))
      (let [{:location/keys [road-nr carriageway start-m end-m]} (road-address new-form)]
        (log/debug "ROAD ADDRESS CHANGED")
        (cond
          ;; All values present, fetch line geometry
          (and road-nr carriageway start-m end-m)
          (t/fx app
                {:tuck.effect/type :query
                 :query :road/line-by-road
                 :args {:road-nr road-nr
                        :carriageway carriageway
                        :start-m start-m
                        :end-m end-m}
                 :result-event (partial ->FetchLocationResponse (road-address new-form))})

          ;; Valid start point, fetch a point geometry
          (and road-nr carriageway start-m)
          (t/fx app
                {:tuck.effect/type :query
                 :query :road/point-by-road
                 :args {:road-nr road-nr
                        :carriageway carriageway
                        :start-m start-m}
                 :result-event (partial ->FetchLocationResponse (road-address new-form))})

          ;; No valid road address
          :else
          (common-controller/update-page-state app [:cost-item] dissoc :geojson)))

      :else
      app)))


;; Cost group related events
(extend-protocol t/Event

  SaveCostGroupPrice
  (process-event [{:keys [cost-group price]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :asset/save-cost-group-price
           :payload {:project-id (get-in app [:params :project])
                     :cost-group (dissoc cost-group
                                         :quantity :count
                                         :cost-per-quantity-unit
                                         :total-cost
                                         :quantity-unit :type)
                     :price price}
           :result-event ->SaveCostGroupPriceResponse}))

  SaveCostGroupPriceResponse
  (process-event [{response :response} app]
    (println "Response: " response)
    (t/fx app
          common-controller/refresh-fx)))
