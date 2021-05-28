(ns teet.asset.assets-controller
  (:require [teet.util.collection :as cu]
            [tuck.core :as t]
            [teet.common.common-controller :as common-controller]
            [ol.Geolocation]
            [teet.log :as log]))

(defrecord UpdateSearchCriteria [criteria])
(defrecord Search []) ; execute search on backend
(defrecord SearchResults [results])

;; Set search area by current location
(defrecord SearchByCurrentLocation [])
(defrecord SetCurrentLocation []) ; called when location changes

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

(defn assets-query [criteria]
  (let [args (reduce (fn [out key]
                       (search-criteria out criteria key))
                     {} (keys criteria))]
    (when (seq args)
      {:query :assets/search
       :args args})))

(extend-protocol t/Event
  UpdateSearchCriteria
  (process-event [{criteria :criteria} app]
    (t/fx (common-controller/update-page-state app [:criteria] merge criteria)
          {:tuck.effect/type :debounce
           :event ->Search
           :timeout 500}))

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

  SearchByCurrentLocation
  (process-event [_ app]
    (common-controller/update-page-state
     app [:criteria] merge
     {:search-by :current-location
      :radius 10
      :ol-geolocation
      (doto (ol.Geolocation.
             #js {:projection "EPSG:3301"})
        (.on "change" (t/send-async! ->SetCurrentLocation))
        (.setTracking true))}))

  SetCurrentLocation
  (process-event [{location :location} app]
    (common-controller/update-page-state
     app [:criteria]
     (fn [{^ol.Geolocation g :ol-geolocation :as state}]
       (assoc state :location (.getPosition g))))))
