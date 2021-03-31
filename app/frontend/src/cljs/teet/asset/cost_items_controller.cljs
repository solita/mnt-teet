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
(defrecord DeleteComponent [id])
(defrecord DeleteComponentResponse [fetched-cost-item-atom id response])
(defrecord SaveComponent [component-id])
(defrecord SaveComponentResponse [tempid response])

(defrecord UpdateForm [form-data])

;; Response when fetching location based on road address
(defrecord FetchLocationResponse [address response])

;; Response when fetching road address based on geopoints
(defrecord FetchRoadResponse [start-end-points response])

(defrecord AddComponent [type])

(declare process-location-change)



(defn- form-state
  "Return the current form state."
  [app]
  (let [component-id (get-in app [:query :component])]
    (if (nil? component-id)
      ;; Update asset itself
      (common-controller/page-state app :cost-item)

      ;; Update some nested component by id
      (let [by-id (fn by-id [{id :db/id :as c}]
                    (if (= component-id (str id))
                      c
                      (some by-id (:component/components c))))]
        (some by-id
              (common-controller/page-state app :cost-item :asset/components))))))

(defn- update-component [components component-id update-fn args]
  (mapv (fn [{id :db/id :as c}]
          (let [c (if (= component-id (str id))
                    (apply update-fn c args)
                    c)]
            (if-let [sub-components (:component/components c)]
              (assoc c :component/components
                     (update-component sub-components component-id update-fn args))
              c))) components))

(defn- update-form
  "Update form state, either the main asset or a component by id."
  [app update-fn & args]
  (let [component-id (get-in app [:query :component])]
    (if (nil? component-id)
      ;; Update asset itself
      (apply common-controller/update-page-state
             app [:cost-item] update-fn args)

      ;; Update some nested component by id
      (common-controller/update-page-state
       app [:cost-item :asset/components]
       update-component component-id update-fn args))))

(defn find-component-path
  "Return vector containing all parents of component from asset to the component.
  For example:
  [a c1 c2 c3]
  where a is the asset, that has component c1
  c1 has child component c2
  and c2 has child component c3 (the component we want)"
  [asset component-id]
  (let [component-id (str component-id)
        containing
        (fn containing [path here]
          (let [cs (concat (:asset/components here)
                           (:component/components here))]
            (if-let [c (some #(when (= component-id (str (:db/id %)))
                                %) cs)]
              ;; we found the component at this level
              (into path [here c])

              ;; not found here, recurse
              (first
               (for [sub cs
                     :let [sub-path (containing (conj path here) sub)]
                     :when sub-path]
                 sub-path)))))]

    (containing [] asset)))

(extend-protocol t/Event

  SaveCostItem
  (process-event [_ {page :page :as app}]
    (let [form-data (form-state app)
          project-id (get-in app [:params :project])
          id (if (= page :new-cost-item)
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
             :result-event (partial ->SaveCostItemResponse (:db/id asset))})))

  SaveCostItemResponse
  (process-event [{:keys [tempid response]} app]
    (apply t/fx
           (snackbar-controller/open-snack-bar app
                                               (tr [:asset :cost-item-saved]))
           (remove nil?
                   [(when (string? tempid)
                      {:tuck.effect/type :navigate
                       :page :cost-item
                       :params (merge
                                (:params app)
                                {:id (str (get-in response [:tempids tempid]))})})
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
  (process-event [_ app]
    (let [form-data (form-state app)
          {parent-id :db/id} (-> (common-controller/page-state app :cost-item)
                                 (find-component-path (:db/id form-data))
                                 butlast last)]
      (t/fx app
            {:tuck.effect/type :command!
             :command :asset/save-component
             :payload {:project-id (get-in app [:params :project])
                       :parent-id parent-id
                       :component (dissoc form-data :component/components)}
             :result-event (partial ->SaveComponentResponse (:db/id form-data))})))

  SaveComponentResponse
  (process-event [{:keys [tempid response]} app]
    (apply t/fx app
           (remove nil?
                   [(when (string? tempid)
                      {:tuck.effect/type :navigate
                       :page :cost-items
                       :params (merge (:params app)
                                      {:component (get-in response [:tempids tempid])})
                       :query {}})
                    common-controller/refresh-fx])))

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
    (if-not (= address (road-address (common-controller/page-state app :cost-item)))
      (do
        (log/debug "Stale response for " address " => " response)
        app)

      (common-controller/update-page-state
       app [:cost-item]
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
               (select-keys (common-controller/page-state app :cost-item)
                            [:location/start-point :location/end-point]))
      (do
        (log/debug "Stale response for " start-end-points " => " response)
        app)

      (common-controller/update-page-state
       (if (empty? response)
         (snackbar-controller/open-snack-bar app (tr [:asset :location :no-road-found-for-points]) :warning)
         app) [:cost-item]
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
                                      :component/ctype type}))))
       {:tuck.effect/type :navigate
        :params params
        :page page
        :query (merge query {:component new-id})}))))

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
