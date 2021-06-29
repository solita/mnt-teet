(ns teet.asset.assets-controller
  (:require [teet.util.collection :as cu]
            [tuck.core :as t]
            [teet.common.common-controller :as common-controller]
            [ol.Geolocation]
            [teet.log :as log]
            [teet.map.openlayers :as openlayers]))

(defrecord UpdateSearchCriteria [criteria])
(defrecord Search []) ; execute search on backend
(defrecord SearchResults [results])

(defrecord HighlightResult [result]) ; higlight a result item
(defrecord ShowDetails [result]) ; show details for result
(defrecord BackToListing []) ; go back from details to listing

;; Set search area by current location
(defrecord SearchBy [search-by])
(defrecord SetCurrentLocation []) ; called when location changes

(defrecord AddRoadAddress [address])
(defrecord RemoveRoadAddress [address])

;; Build search criteria by key
(defmulti search-criteria (fn [_out _criteria key] key))

(defmethod search-criteria :default [out _ _] out)

(defn- to-set
  [items item-fn]
  (when (seq items)
    (into #{} (map item-fn) items)))

(defmethod search-criteria :fclass [out {fclass :fclass} _]
  (if-let [s (to-set fclass (comp :db/ident second))]
    (assoc out :fclass s)
    out))

(defmethod search-criteria :common/status [out {status :common/status} _]
  (if-let [s (to-set status :db/ident)]
    (assoc out :common/status s)
    out))

(defmethod search-criteria :location [out {[x y] :location
                                           :keys [radius search-by]}]
  (if (= search-by :current-location)
    (assoc out :current-location [x y radius])
    out))

(defmethod search-criteria :road-address [out {addr :road-address
                                               search-by :search-by}]
  (if (and (= search-by :road-address)
           (seq addr))
    (assoc out :road-address addr)
    out))

(defmethod search-criteria :region [out {:keys [region search-by]}]
  (if (and (= search-by :region) (seq region))
    (assoc out :region (into #{} (map :id) region))
    out))

(defn assets-query [criteria]
  (let [args (reduce (fn [out key]
                       (search-criteria out criteria key))
                     {} (keys criteria))]
    (when (seq args)
      {:query :assets/search
       :args args})))

(defn- debounced-search [new-app]
  (t/fx new-app
        {:tuck.effect/type :debounce
         :event ->Search
         :timeout 500}))

(extend-protocol t/Event
  UpdateSearchCriteria
  (process-event [{criteria :criteria} app]
    (debounced-search
     (common-controller/update-page-state app [:criteria] merge criteria)))

  Search
  (process-event [_ app]
    (if-let [q (assets-query (common-controller/page-state app :criteria))]
      (do
        (log/debug "Assets query: " q)
        (t/fx (common-controller/assoc-page-state
               app
               [:query] q
               [:search-in-progress?] true
               [:results] nil)

              (merge {:tuck.effect/type :query
                      :result-event ->SearchResults} q)))
      (common-controller/assoc-page-state
       app
       [:search-in-progress?] false
       [:results] nil)))

  SearchResults
  (process-event [{results :results} app]
    (common-controller/assoc-page-state
     app
     [:search-in-progress?] false
     [:results] results))

  SearchBy
  (process-event [{search-by :search-by} app]
    (debounced-search
     (common-controller/update-page-state
      app [:criteria]
      (fn [{cleanup :cleanup :as criteria}]
        (let [criteria (if cleanup
                         (cleanup criteria)
                         criteria)]
          (merge
           criteria
           {:search-by search-by}
           (case search-by
             :current-location
             {:radius 10
              :ol-geolocation
              (doto (ol.Geolocation.
                     #js {:projection "EPSG:3301"
                          :trackingOptions #js {:enableHighAccuracy true}})
                (.on "change" (t/send-async! ->SetCurrentLocation))
                (.setTracking true))
              :cleanup (fn [{g :ol-geolocation :as criteria}]
                         (.setTracking g false)
                         (dissoc criteria :radius :ol-geolocation :cleanup))}

             :road-address
             {:road-address []
              :cleanup #(dissoc % :road-address :cleanup)}

             :region
             {:region #{}})))))))

  SetCurrentLocation
  (process-event [{location :location} app]
    (common-controller/update-page-state
     app [:criteria]
     (fn [{^ol.Geolocation g :ol-geolocation :as state}]
       (assoc state :location (.getPosition g)))))

  HighlightResult
  (process-event [{result :result} app]
    (common-controller/assoc-page-state
     app
     [:results :highlight-oid] (:asset/oid result)))

  AddRoadAddress
  (process-event [{address :address} app]
    (debounced-search
     (common-controller/update-page-state
      app [:criteria :road-address] conj address)))

  RemoveRoadAddress
  (process-event [{address :address} app]
    (debounced-search
     (common-controller/update-page-state
      app [:criteria :road-address]
      (fn [addrs] (filterv #(not= % address) addrs)))))

  ShowDetails
  (process-event [{result :result} app]
    (t/fx app
          {:tuck.effect/type :navigate
           :page (:page app)
           :params (:params app)
           :query {:details (:asset/oid result)}}))

  BackToListing
  (process-event [_ app]
    (openlayers/fit-map-to-layer! "teet-source" "asset-results")
    (t/fx app
          {:tuck.effect/type :navigate
           :page (:page app)
           :params (:params app)
           :query (dissoc (:query app) :details)})))
