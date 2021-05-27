(ns teet.asset.assets-controller
  (:require [teet.util.collection :as cu]
            [tuck.core :as t]
            [teet.common.common-controller :as common-controller]
            [ol.Geolocation]))

(defrecord UpdateSearchCriteria [criteria])
(defrecord Search []) ; execute search on backend
(defrecord SearchResults [results])

;; Set search area by current location
(defrecord SearchByCurrentLocation [])
(defrecord SetCurrentLocation []) ; called when location changes

(defn- to-set
  ([filters key] (to-set filters key :db/ident))
  ([filters key item-fn]
   (cu/update-in-if-exists
    filters [key]
    (fn [items]
      (let [set (into #{} (map item-fn) items)]
        (when-not (empty? set)
          set))))))

(defn assets-query [filters]
  (let [criteria (-> filters
                     (to-set :fclass (comp :db/ident second))
                     (to-set :common/status)
                     cu/without-nils)]
    (when (seq criteria)
      {:query :assets/search
       :args criteria})))

(extend-protocol t/Event
  UpdateSearchCriteria
  (process-event [{criteria :criteria} app]
    (t/fx (common-controller/update-page-state app [:criteria] merge criteria)
          {:tuck.effect/type :debounce
           :event ->Search
           :timeout 500}))

  Search
  (process-event [_ app]
    (println "SEARCHING " (common-controller/page-state app :criteria))
    (if-let [q (assets-query (common-controller/page-state app :criteria))]
      (t/fx (common-controller/assoc-page-state
             app
             [:query] q
             [:search-in-progress?] true
             [:results] nil)

            (merge {:tuck.effect/type :query
                    :result-event ->SearchResults} q))
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
