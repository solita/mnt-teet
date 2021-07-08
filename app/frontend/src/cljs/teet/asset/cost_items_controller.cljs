(ns teet.asset.cost-items-controller
  (:require [teet.common.common-controller :as common-controller]
            [tuck.core :as t]
            [teet.snackbar.snackbar-controller :as snackbar-controller]
            [teet.localization :refer [tr]]
            [teet.util.collection :as cu]
            [teet.log :as log]
            [clojure.string :as str]
            [teet.asset.asset-model :as asset-model]
            [teet.routes :as routes]
            cljs.reader
            [teet.asset.asset-type-library :as asset-type-library]
            [reagent.core :as r]))

(defrecord AddComponentCancel [id])
(defrecord AddMaterialCancel [id])

(defrecord MaybeFetchAssetTypeLibrary []
  t/Event
  (process-event [_ app]
    (if (:asset-type-library app)
      app
      (t/fx app
            {:tuck.effect/type :query
             :query :asset/type-library
             :args {}
             :result-path [:asset-type-library]}))))

;; Register routes that need asset type library to be in app state
;; and launch event to fetch it when navigating.
(doseq [r [:cost-item :cost-items :cost-items-totals :materials-and-products :asset-type-library :assets]]
  (defmethod routes/on-navigate-event r [_] (->MaybeFetchAssetTypeLibrary)))


(defmethod routes/on-leave-event :cost-item [{:keys [query new-query]}]
  [(when (and (:component query)
              (not (:component new-query)))
     ;; Navigated away from component add form, cleanup state
     (->AddComponentCancel (:component query)))
   (when (and (:material query)
              (not (:material new-query)))
     (->AddMaterialCancel (:material query)))])

(defonce next-id (atom 0))

(defn- next-id! [prefix]
  (str prefix (swap! next-id inc)))

(def ^:private road-address
  #(into {}
         (select-keys % [:location/road-nr :location/carriageway
                         :location/start-km :location/start-offset-m
                         :location/end-km :location/end-offset-m])))

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
(defrecord DeleteMaterial [id])
(defrecord SaveMaterial [material-id])
(defrecord SaveMaterialResponse [response])
(defrecord AddMaterial [type])
(defrecord UpdateMaterialForm [form-data])
(defrecord ResetMaterialForm [form-data])

(defrecord UpdateForm [form-data])

;; Response when fetching location based on road address
(defrecord FetchLocationResponse [address response])

;; Response when fetching road address based on geopoints
(defrecord FetchRoadResponse [start-end-points response])

;; initialize map (when opening map and editing existing asset)
(defrecord InitMap [])

(defrecord AddComponent [type])

(defrecord SaveCostGroupPrice [finish-saving! cost-group price])
(defrecord SaveCostGroupPriceResponse [finish-saving! response])
(defrecord SaveCostGroupPriceError [finish-saving! error])

;; Locking and versioning
(defrecord SaveBOQVersion [callback form-data])
(defrecord SaveBOQVersionResponse [callback response])
(defrecord UnlockForEdits [callback])
(defrecord UnlockForEditsResponse [callback response])

(defrecord ToggleOpenTotals [ident]) ; toggle open/closed totals ident
(defrecord SetTotalsRoadFilter [road])

(declare process-location-change)

(defn- form-component-id [app]
  (let [id (get-in app [:params :id])]
    (or
     ;; component query parameter specifies new component to add
     (get-in app [:query :component])

     ;; editing existing component itself
     (and (asset-model/component-oid? id)
          id))))

(defn- form-material-id [app]
  (let [id (get-in app [:params :id])]
    (or
     ;; component query parameter specifies new component to add
     (get-in app [:query :material])
     ;; or existing material
     (when (asset-model/material-oid? id)
       id))))

(defn- children-by-key [asset-or-component k]
  (when-let [children (not-empty (k asset-or-component))]
    [k children]))

(defn- children
  "Find children of the asset or component, returning [<chilren-key> <children>],
   eg. [:component/materials <materials>]"
  [asset-or-component]
  (->> [:asset/components :component/components :component/materials]
       (map (partial children-by-key asset-or-component))
       (some identity)))

(defn- form-state
  "Return the current form state."
  [app]
  (if-let [target-id (or (form-material-id app)
                         (form-component-id app))]
    ;; Get some nested component by id
    (let [by-id (fn by-id [{oid :asset/oid :as c}]
                  (if (= target-id oid)
                    c
                    (let [[_ cs] (children c)]
                      (some by-id cs))))]
      (some by-id
            (common-controller/page-state app :cost-item :asset/components)))

    ;; Get asset itself
    (common-controller/page-state app :cost-item)))

(defn- update-component [components component-id update-fn args]
  (mapv (fn [{oid :asset/oid :as c}]
          (let [c (if (= component-id oid)
                    (apply update-fn c args)
                    c)]
            (if-let [[subcomponent-key sub-components] (children c)]
              (assoc c subcomponent-key
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

(defn- update-material-form
  "Update material form state."
  [app update-fn & args]
  ;; Update some nested component by id
  (common-controller/update-page-state
   app [:cost-item :asset/components]
   update-component (form-material-id app) update-fn args))


(def locked?
  "Check from app state if BOQ version is locked."
  (comp asset-model/locked?
        :version
        common-controller/page-state))

(defn carriageways-for-road
  "Get the carriageways of the road with `road-nr` from `relevant-roads`"
  [road-nr relevant-roads]
  (->> relevant-roads
       (some #(when (= (:road-nr %) road-nr) %))
       :carriageways))

(defn- default-carriageway
  "When changing road number, also change the carriageway if the current
  value is invalid"
  [form-update-data old-form-data relevant-roads]
  (if (and (:location/road-nr form-update-data)
           (not (:location/carriageway form-update-data))
           ;; 1 is always an allowed carriageway value, no need to
           ;; check if still valid
           (not= (:location/carriageway old-form-data) 1))
    (if-let [selected-road-carriageways (carriageways-for-road (:location/road-nr form-update-data)
                                                               relevant-roads)]
      ;; If the currently selected carriageway was NOT found for the newly selected road...
      (if-not (some #(= (:location/carriageway old-form-data) %)
                    selected-road-carriageways)
        ;; ... use the smallest available carriageway ...
        (assoc form-update-data
               :location/carriageway
               (apply min selected-road-carriageways))
        ;; ... otherwise no need to change the currently selected one.
        form-update-data)
      form-update-data)
    form-update-data))

(declare project-relevant-roads prepare-location)

(extend-protocol t/Event

  SaveCostItem
  (process-event [_ {page :page :as app}]
    (if (locked? app)
      (do
        (log/debug "Not saving, BOQ version is locked.")
        app)
      (let [form-data (prepare-location (form-state app))
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
               :result-event ->SaveCostItemResponse}))))

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
    (if (locked? app)
      (do
        (log/debug "Not deleting component, BOQ version is locked.")
        app)
      (let [project-id (get-in app [:params :project])]
        (t/fx app
              {:tuck.effect/type :command!
               :command :asset/delete-component
               :payload {:db/id id
                         :project-id project-id}
               :result-event common-controller/->Refresh}))))

  SaveComponent
  (process-event [_ app]
    (if (locked? app)
      (do
        (log/debug "Not saving, BOQ version is locked.")
        app)
      (let [form-data (prepare-location (form-state app))
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
               :result-event ->SaveComponentResponse}))))

  SaveComponentResponse
  (process-event [{:keys [response]} {params :params :as app}]
    (let [oid (:asset/oid response)]
      (apply t/fx
             (snackbar-controller/open-snack-bar app (tr [:asset :component-saved]))
             (remove nil?
                     [(when (not= oid (params :id))
                        {:tuck.effect/type :navigate
                         :page :cost-item
                         :params (merge (:params app)
                                        {:id oid})
                         :query nil})
                      common-controller/refresh-fx]))))

  AddMaterial
  (process-event [{:keys [type component-oid]} {:keys [page params query] :as app}]
    (let [new-id (next-id! "m")]
      (t/fx
       (update-form app
                    (fn [parent]
                      (update parent :component/materials
                              #(conj (or % [])
                                     {:db/id new-id
                                      :asset/oid new-id
                                      :material/type type}))))
       {:tuck.effect/type :navigate
        :params params
        :page page
        :query (merge query {:material new-id})})))

  DeleteMaterial
  (process-event [{:keys [fetched-cost-item-atom id]} app]
    (if (locked? app)
      (do
        (log/debug "Not deleting material, BOQ version is locked.")
        app)
      (let [project-id (get-in app [:params :project])]
        (t/fx app
              {:tuck.effect/type :command!
               :command :asset/delete-material
               :payload {:db/id id
                         :project-id project-id}
               :result-event common-controller/->Refresh}))))

  SaveMaterial
  (process-event [_ app]
    (if (locked? app)
      (do
        (log/debug "Not saving, BOQ version is locked.")
        app)
      (let [form-data (form-state app)
            {parent-id :asset/oid}
            (-> (common-controller/page-state app :cost-item)
                (asset-model/find-component-path (:asset/oid form-data))
                butlast last)]
        (t/fx app
              {:tuck.effect/type :command!
               :command :asset/save-material
               :payload {:project-id (get-in app [:params :project])
                         :parent-id parent-id
                         :material (dissoc form-data
                                           :material/materials
                                           :location/geojson)}
               :result-event ->SaveMaterialResponse}))))

  SaveMaterialResponse
  (process-event [{:keys [response]} {params :params :as app}]
    (let [oid (:asset/oid response)]
      (apply t/fx
             (snackbar-controller/open-snack-bar app (tr [:asset :material-saved]))
             (remove nil?
                     [(when (not= oid (params :id))
                        {:tuck.effect/type :navigate
                         :page :cost-item
                         :params (merge (:params app)
                                        {:id oid})
                         :query nil})
                      common-controller/refresh-fx]))))

  UpdateMaterialForm
  (process-event [{:keys [form-data]} app]
    (if (locked? app)
      (do
        (log/debug "Not editing form, BOQ version is locked.")
        app)
      (update-material-form app #(merge % form-data))))

  ResetMaterialForm
  (process-event [{:keys [form-data]} app]
    (if (locked? app)
      (do
        (log/debug "Not editing form, BOQ version is locked.")
        app)
      (update-material-form app (constantly form-data))))

  UpdateForm
  (process-event [{:keys [form-data]} app]
    (if (locked? app)
      (do
        (log/debug "Not editing form, BOQ version is locked.")
        app)
      (if (= (list :location/single-point?) (keys form-data))
        ;; Only changing single point on/off, don't refetch location
        (update-form app (fn [form]
                           (let [single? (:location/single-point? form-data)
                                 remove-if-single #(if single? nil %)]
                             (-> form
                                 (assoc :location/single-point? single?)
                                 (cu/update-in-if-exists [:location/end-point] remove-if-single)
                                 (cu/update-in-if-exists [:location/end-km] remove-if-single)
                                 (cu/update-in-if-exists [:location/end-offset-m] remove-if-single)))))
        ;; Other form change, maybe refetch location
        (let [old-form (form-state app)
              new-form (cu/deep-merge
                        old-form
                        (default-carriageway form-data
                                             old-form
                                             (project-relevant-roads (get-in app [:params :project]))))]
          (process-location-change
           (update-form app (constantly new-form))
           old-form new-form)))))

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
         (if (:location/end-km address)
           ;; Line geometry
           (let [{:keys [road-line start-point end-point]} response]
             (assoc form
                    :location/start-point start-point
                    :location/end-point end-point
                    :location/geojson
                    (feature-collection-geojson
                     #js {:type "LineString"
                          :coordinates (clj->js road-line)}
                     (point-geojson start-point "start/end" "start")
                     (point-geojson end-point "start/end" "end"))))
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

      (let [{:keys [road start-point end-point start-offset-m end-offset-m]}
            response]
        (println "RESPONSE: " (pr-str response))
        (update-form
         (if (empty? response)
           (snackbar-controller/open-snack-bar app (tr [:asset :location :no-road-found-for-points]) :warning)
           app)
         (fn [form]
           (if (nil? road)
             (-> form
                 (dissoc :location/road-nr :location/carriageway
                         :location/start-km :location/end-km
                         :location/start-offset-m :location/end-offset-m)
                 (merge {:location/geojson
                         (feature-collection-geojson
                          (clj->js {:type "LineString"
                                    :coordinates [start-point end-point]})
                          (point-geojson start-point "start/end" "start")
                          (point-geojson end-point "start/end" "end"))}))
             (let [{:keys [geometry road-nr carriageway start-km end-km km]} road]
               (when geometry
                 (let [geojson (js/JSON.parse geometry)]
                   (merge form
                          {:location/start-point start-point
                           :location/start-offset-m start-offset-m
                           :location/end-point end-point
                           :location/end-offset-m end-offset-m
                           :location/geojson
                           #js {:type "FeatureCollection"
                                :features
                                (if end-km
                                  #js [geojson
                                       (point-geojson start-point "start/end" "start")
                                       (point-geojson end-point "start/end" "end")]
                                  #js [(point-geojson start-point "start/end" "start")])}
                           :location/road-nr road-nr
                           :location/carriageway carriageway
                           :location/start-km (or km start-km)
                           :location/end-km end-km}))))))))))

  InitMap
  (process-event [_ app]
    (let [{:location/keys [start-point end-point]} (form-state app)
          start (some-> start-point point)
          end (some-> end-point point)]
      (if (or start end)
        (update-form app merge
                     {:location/geojson
                      #js {:type "FeatureCollection"
                           :features
                           (if end
                             #js [(point-geojson start "start/end" "start")
                                  (point-geojson end "start/end" "end")]
                             #js [(point-geojson start "start/end" "start")])}})
        app)))

  AddComponent
  (process-event [{type :type} {:keys [page params query] atl :asset-type-library :as app}]
    ;; Can add subcomponent as well, add to correct path
    (let [new-id (next-id! "c")

          ;; If not inheriting location, find road+carriageway from some parent
          ;; so we can autofill that. Components are usually on the same road.
          ctype (asset-type-library/item-by-ident atl type)
          current-oid (:id params)
          asset (common-controller/page-state app :cost-item)
          location (when (not (:component/inherits-location? ctype))
                     (some #(when (contains? % :location/road-nr)
                              (select-keys % [:location/road-nr :location/carriageway]))
                           (if (asset-model/asset-oid? current-oid)
                             ;; direct child of asset
                             [asset]
                             ;; at some component, find location from parent chain
                             (reverse
                              (asset-model/find-component-path
                               asset
                               current-oid)))))

          ;; Add new component to current parent (asset or component)
          app (update-form
               app
               (fn [parent]
                 (update parent (if (:asset/fclass parent)
                                  :asset/components
                                  :component/components)
                         #(conj (or % [])
                                (merge
                                 location
                                 {:db/id new-id
                                  :asset/oid new-id
                                  :component/ctype type})))))]
      (t/fx app
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
                 (filterv #(not= (:asset/oid %) id) cs))))))

  AddMaterialCancel
  (process-event [{id :id} app]
    (update-form
     app
     (fn [parent]
       (update parent :component/materials
               (fn [ms]
                 (filterv #(not= (:asset/oid %) id) ms)))))))

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
          (dissoc app :location/road-nr :location/carriageway :location/start-km :location/end-km)))

      ;; Manually edited road location, refetch geometry and points
      (not= (road-address old-form)
            (road-address new-form))
      (let [{:location/keys [road-nr carriageway
                             start-km start-offset-m
                             end-km end-offset-m]} (road-address new-form)]
        (log/debug "ROAD ADDRESS CHANGED")
        (cond
          ;; All values present, fetch line geometry
          (and road-nr carriageway start-km end-km)
          (t/fx app
                {:tuck.effect/type :query
                 :query :road/line-by-road
                 :args {:road-nr road-nr
                        :carriageway carriageway
                        :start-km start-km
                        :start-offset-m start-offset-m
                        :end-km end-km
                        :end-offset-m end-offset-m}
                 :result-event (partial ->FetchLocationResponse (road-address new-form))})

          ;; Valid start point, fetch a point geometry
          (and road-nr carriageway start-km)
          (t/fx app
                {:tuck.effect/type :query
                 :query :road/point-by-road
                 :args {:road-nr road-nr
                        :carriageway carriageway
                        :start-km start-km}
                 :result-event (partial ->FetchLocationResponse (road-address new-form))})

          ;; No valid road address
          :else
          (common-controller/update-page-state app [:cost-item] dissoc :geojson)))

      :else
      app)))


;; Cost group related events
(extend-protocol t/Event

  SaveCostGroupPrice
  (process-event [{:keys [cost-group price finish-saving!]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :asset/save-cost-group-price
           :payload {:project-id (get-in app [:params :project])
                     :cost-group (dissoc cost-group
                                         :quantity :count
                                         :cost-per-quantity-unit
                                         :total-cost
                                         :quantity-unit :type
                                         :ui/group)
                     :price (if (str/blank? price) nil price)}
           :result-event (partial ->SaveCostGroupPriceResponse finish-saving!)
           :error-event (partial ->SaveCostGroupPriceError finish-saving!)}))

  SaveCostGroupPriceResponse
  (process-event [{:keys [finish-saving! response]} app]
    (println "Response: " response)
    (finish-saving!)
    (t/fx app
          common-controller/refresh-fx))

  SaveCostGroupPriceError
  (process-event [{:keys [finish-saving! error]} app]
    (println "Error: " error)
    (finish-saving!)
    (common-controller/on-server-error error app))

  ToggleOpenTotals
  (process-event [{ident :ident} app]
    (common-controller/update-page-state
     app [:closed-totals] cu/toggle ident))

  SetTotalsRoadFilter
  (process-event [{road :road} {:keys [page params query] :as app}]
    (t/fx app
          {:tuck.effect/type :navigate
           :page page
           :params params
           :query (if road
                    (assoc query :road road)
                    (dissoc query :road))})))

(extend-protocol t/Event
  SaveBOQVersion
  (process-event [{:keys [callback form-data]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :asset/lock-version
           :payload (merge
                     {:boq-version/project (get-in app [:params :project])}
                     (select-keys form-data [:boq-version/type :boq-version/explanation]))
           :result-event (partial ->SaveBOQVersionResponse callback)}))

  SaveBOQVersionResponse
  (process-event [{:keys [callback response]} app]
    (callback)
    (t/fx app
          common-controller/refresh-fx))

  UnlockForEdits
  (process-event [{:keys [callback]} app]
    (if-not (locked? app)
      (do
        (log/debug "Shouldn't get here, can't unlock what is not locked")
        app)
      (t/fx app
            {:tuck.effect/type :command!
             :command :asset/unlock-for-edits
             :payload {:boq-version/project (get-in app [:params :project])}
             :result-event (partial ->UnlockForEditsResponse callback)})))

  UnlockForEditsResponse
  (process-event [{:keys [callback response]} app]
    (callback)
    (t/fx app
          common-controller/refresh-fx)))

(defn filtered-cost-group-totals
  "Return ATL item that is being filtered by and cost group totals that match
  the filter.

  Adds :ui/group to each item that contains a vector of type hierarchy.
  The group is used in view code to group the rows into sections.

  For example if URL query parameter `:filter` has `:fgroup/foo`
  the first item will be the ATL definition of the feature group
  and the second item will a vector of all the rows in `cost-group-totals`
  where the type hierarchy contains the fgroup. So all rows for any
  component in any fclass under the fgroup.
  "

  [app atl cost-group-totals]
  (let [kw (some-> app (get-in [:query :filter])
                   cljs.reader/read-string) ; only reads edn, not arbitrary code

        filter-pred (if kw
                      #(some (fn [{t :db/ident}] (= t kw))
                             (:ui/group %))
                      identity)]
    [(some->> kw (asset-type-library/item-by-ident atl))
     (into []
           (comp
            (map #(assoc % :ui/group
                         (asset-type-library/type-hierarchy atl (:type %))))

            (filter filter-pred))
           cost-group-totals)]))

(defn material-used-in-fgroup-fclass-or-ctype?
  "Is the material used for the given feature group, feature class or
  component type? It is used if there is a component in the project that
  - is of the given component type or
  - the component belongs to the given feature class or feature group"
  [fgroup-fclass-or-ctype material]
  (->> material
       :component/_materials
       (map (comp (partial map :db/ident)
                  (juxt :fgroup :fclass :component/ctype)))
       (some (partial some (partial = fgroup-fclass-or-ctype)))))

(defn remove-nonmatching-components
  "Removes components that don't belong to the given feature group,
  feature class or component type"
  [fgroup-fclass-or-ctype material]
  (if (nil? fgroup-fclass-or-ctype)
    material
    (update material
           :component/_materials
           #(filter (fn [component]
                      (->> ((juxt :fgroup :fclass :component/ctype) component)
                           (map :db/ident)
                           (some (partial = fgroup-fclass-or-ctype))))
                    %))))

(defn filtered-materials-and-products
  "Returns a structure similar to `filtered-cost-group-totals`. Here the
  `:ui/group` path is built manually using the material's `:fgroup` and `:fclass`
  values."
  [app atl materials-and-products]
  (let [kw (some-> app (get-in [:query :filter])
                   cljs.reader/read-string)
        filter-pred (if kw
                      (partial material-used-in-fgroup-fclass-or-ctype? kw)
                      identity)]

    [(some->> kw (asset-type-library/item-by-ident atl))
     (into []
           (comp
            (filter filter-pred)
            (map (partial remove-nonmatching-components kw)))
           materials-and-products)]))

(def relevant-road-cache
  "Atom to cache relevant roads by project."
  (r/atom {}))

(defn- project-relevant-roads
  "Get relevant roads for project that have been fetched previously."
  [project-id]
  (get @relevant-road-cache project-id))

(defrecord FetchRelevantRoadsResponse [project-id response]
  t/Event
  (process-event [_ app]
    (swap! relevant-road-cache assoc project-id response)
    app))

(defrecord FetchRelevantRoads [project-id]
  t/Event
  (process-event [_ app]
    (if (contains? @relevant-road-cache project-id)
      app
      (do
        (swap! relevant-road-cache assoc project-id [])
        (t/fx app
              {:tuck.effect/type :query
               :query :asset/project-relevant-roads
               :args {:thk.project/id project-id}
               :result-event (partial ->FetchRelevantRoadsResponse project-id)})))))

(defn- prepare-location
  "Prepare location for saving."
  [form-data]
  (dissoc form-data :location/map-open? :location/geojson))

(def location-form-keys [:location/start-point :location/end-point
                         :location/road-nr :location/carriageway
                         :location/start-km :location/end-km
                         :location/geojson :location/single-point?])

(def location-form-value
  #(select-keys % location-form-keys))


(defn location-form-change
  [value]
  (->UpdateForm value))

(defonce map-open? (r/atom true))

(defrecord ToggleMapOpen []
  t/Event
  (process-event [_ app]
    (swap! map-open? not)
    app))

(defrecord SetMapOpen [open?]
  t/Event
  (process-event [{open? :open?} app]
    (reset! map-open? open?)
    app))
