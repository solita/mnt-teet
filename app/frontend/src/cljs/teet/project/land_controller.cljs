(ns teet.project.land-controller
  (:require [tuck.core :as t]
            [clojure.string :as str]
            [teet.util.collection :as cu]
            [teet.localization :refer [tr]]
            [teet.map.map-controller :as map-controller]
            [goog.math.Long]
            [cljs-time.core :as time]
            [cljs-time.coerce :as c]))

(defrecord SetCadastralInfo [response])
(defrecord ToggleLandUnit [unit])
(defrecord SearchOnChange [attribute value])
(defrecord UpdateFilteredUnitIDs [ids])
(defrecord SubmitLandPurchaseForm [form-data cadastral-id])
(defrecord FetchLandAcquisitions [project-id])

(defn toggle-selected-unit
  [id cad-units]
  (map
    (fn [unit]
      (assoc unit :selected? (and (= (:teet-id unit)
                                     id)
                                  (not (:selected? unit)))))
    cad-units))

(extend-protocol t/Event

  ToggleLandUnit
  (process-event [{unit :unit} app]
    (let [selected? (:selected? unit)]
      (if selected?
        (map-controller/zoom-on-layer "geojson_entities")
        (map-controller/zoom-on-feature "geojson_features_by_id" unit))
      (map-controller/update-features!
        "geojson_features_by_id"
        (fn [feature]
          (let [id (.get feature "teet-id")]
            (if (and (= id (:teet-id unit))
                     (not selected?))
              (.set feature "selected" true)
              (.set feature "selected" false)))))
      (update-in app
                 [:route :project :thk.project/related-cadastral-units-info :results]
                 (partial toggle-selected-unit (:teet-id unit)))))

  UpdateFilteredUnitIDs
  (process-event [_ app]
    (let [quality (get-in app [:route :project :land-acquisition-filters :quality :value])
          text (get-in app [:route :project :land-acquisition-filters :name-search-value])
          units (get-in app [:route :project :thk.project/related-cadastral-units-info :results])
          filtered-ids (->> units
                            (filter (fn [unit]
                                      (and
                                        (str/includes? (str/lower-case (get unit :L_AADRESS)) text)
                                        (if quality
                                          (= (:quality unit) quality)
                                          true))))
                            (mapv :teet-id)
                            set)]
      (assoc-in app [:route :project :thk.project/filtered-cadastral-units] filtered-ids)))

  SearchOnChange
  (process-event
    [{attribute :attribute
      value :value} app]
    (t/fx (assoc-in app [:route :project :land-acquisition-filters attribute] value)
          {:tuck.effect/type :debounce
           :timeout 600
           :id :change-cadastral-search
           :event ->UpdateFilteredUnitIDs}))

  FetchLandAcquisitions
  (process-event
    [{project-id :project-id} app]
    (t/fx app
          {:tuck.effect/type :query
           :query :land/fetch-land-acquisitions
           :args {:project-id project-id}
           :result-path [:route :project :land-acquisitions]}))

  SubmitLandPurchaseForm
  (process-event [{:keys [form-data cadastral-id]} app]
    (let [project-id (get-in app [:params :project])
          {:land-acquisition/keys [area-to-obtain pos-number]} form-data]
      (t/fx app
            {:tuck.effect/type :command!
             :command (if (:db/id form-data)
                        :land/update-land-acquisition
                        :land/create-land-acquisition)
             :success-message (tr [:land :land-acquisition-saved])
             :payload (merge form-data
                             {:cadastral-unit cadastral-id
                              :project-id project-id}
                             (when area-to-obtain
                               {:land-acquisition/area-to-obtain (js/parseFloat area-to-obtain)})
                             (when pos-number
                               {:land-acquisition/pos-number (js/parseFloat pos-number)}))
             :result-event (partial ->FetchLandAcquisitions project-id)})))

  SetCadastralInfo
  (process-event [{response :response} app]
    (let [page (:page app)

          data (->> response
                    :results
                    (mapv #(get % "properties"))
                    (mapv
                      #(cu/map-keys keyword %))
                    ;;Assoc teet-id for showing hovers on map
                    (mapv
                      #(assoc % :teet-id (str "2:" (:TUNNUS %))))
                    (mapv
                      (fn [{:keys [MOOTVIIS MUUDET] :as unit}]
                        (assoc unit :quality (cond
                                               (and (= MOOTVIIS "mõõdistatud, L-EST")
                                                    (not (time/before? (c/from-string MUUDET) (time/date-time 2018 01 01))))
                                               :good
                                               (and (= MOOTVIIS "mõõdistatud, L-EST")
                                                    (time/before? (c/from-string MUUDET) (time/date-time 2018 01 01)))
                                               :questionable
                                               :else
                                               :bad)))))]

      (assoc-in app [:route page :thk.project/related-cadastral-units-info :results] data))))
