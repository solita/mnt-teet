(ns teet.asset.cost-items-controller
  (:require [teet.common.common-controller :as common-controller]
            [tuck.core :as t]
            [teet.snackbar.snackbar-controller :as snackbar-controller]
            [teet.localization :refer [tr]]
            [teet.util.collection :as cu]
            [teet.log :as log]
            [teet.routes :as routes]
            [clojure.string :as str]))

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
(defrecord SaveCostItemResponse [tempid response])
(defrecord DeleteComponent [fetched-cost-item-atom id])
(defrecord DeleteComponentResponse [fetched-cost-item-atom id response])
(defrecord SaveComponent [parent-id form-data])
(defrecord SaveComponentResponse [tempid response])


(defrecord NewCostItem []) ; start creating new cost item
(defrecord FetchCostItem [id]) ; fetch cost item for editing
(defrecord FetchCostItemResponse [response])

(defrecord UpdateForm [form-data])

;; Response when fetching location based on road address
(defrecord FetchLocationResponse [address response])

;; Response when fetching road address based on geopoints
(defrecord FetchRoadResponse [start-end-points response])

(declare process-location-change)

(defmethod routes/on-navigate-event :cost-items [{:keys [query current-app]}]
  (println "on-navigate-event :Cost-items " query)
  (cond
    (= "new" (:id query))
    (->NewCostItem)

    (:id query)
    (->FetchCostItem (:id query))))

(extend-protocol t/Event

  SaveCostItem
  (process-event [_ app]
    (let [form-data (common-controller/page-state app :form)
          project-id (get-in app [:params :project])
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

  UpdateForm
  (process-event [{form-data :form-data} app]
    (let [old-form (common-controller/page-state app :form)
          new-form (cu/deep-merge old-form form-data)]
      (process-location-change
       (common-controller/assoc-page-state app [:form] new-form)
       old-form new-form)))

  NewCostItem
  (process-event [_ app]
    (common-controller/assoc-page-state app [:form]
                                        {:db/id (next-id! "costitem")}))

  FetchCostItem
  (process-event [{id :id} app]
    (t/fx (common-controller/assoc-page-state app [:form] :loading)
          {:tuck.effect/type :query
           :query :asset/cost-item
           :args {:db/id (common-controller/->long id)}
           :result-event ->FetchCostItemResponse}))

  FetchCostItemResponse
  (process-event [{response :response} app]
    (let [atl (common-controller/page-state app :asset-type-library)
          fclass-ident (:asset/fclass response)
          fclass= (fn [fc] (= (:db/ident fc) fclass-ident))
          fgroup (cu/find-> (:fgroups atl)
                            #(cu/find-> (:fclass/_fgroup %) fclass=))
          fclass (cu/find-> (:fclass/_fgroup fgroup) fclass=)]
      (println "fgroup/fclass"  fgroup " / " fclass)
      (common-controller/assoc-page-state
       app [:form]
       (assoc response :feature-group-and-class [fgroup fclass]))))


  ;; When road address was changed on the form, update geometry
  ;; and start/end points from the fetched response
  FetchLocationResponse
  (process-event [{:keys [address response]} app]
    (if-not (= address (road-address (common-controller/page-state app :form)))
      (do
        (log/debug "Stale response for " address " => " response)
        app)

      (common-controller/update-page-state
       app [:form]
       (fn [form]
         ;; Set start/end points and GeoJSON geometry
         (if (:location/end-m address)
           ;; Line geometry
           (do
             (println "RESPONSE LINE" response)
             (assoc form
                    :location/start-point (first response)
                    :location/end-point (last response)
                    :location/geojson
                    (feature-collection-geojson
                     #js {:type "LineString"
                          :coordinates (clj->js response)}
                     (point-geojson (first response) "start/end" "start")
                     (point-geojson (last response) "start/end" "end"))))
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
               (select-keys (common-controller/page-state app :form)
                            [:location/start-point :location/end-point]))
      (do
        (log/debug "Stale response for " start-end-points " => " response)
        app)

      (common-controller/update-page-state
       (if (empty? response)
         (snackbar-controller/open-snack-bar app (tr [:asset :location :no-road-found-for-points]) :warning)
         app) [:form]
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
                         :location/end-m end-m}))))))))))

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
          (common-controller/update-page-state app [:form] dissoc :geojson)))

      :else
      app)))
