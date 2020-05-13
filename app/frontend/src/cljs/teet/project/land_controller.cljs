(ns teet.project.land-controller
  (:require [tuck.core :as t]
            [clojure.string :as str]
            [teet.util.collection :as cu]
            [teet.localization :refer [tr]]
            [teet.map.map-controller :as map-controller]
            [goog.math.Long]
            [cljs-time.core :as time]
            [cljs-time.coerce :as c]
            [teet.common.common-controller :as common-controller]
            [teet.snackbar.snackbar-controller :as snackbar-controller]))

(defrecord ToggleLandUnit [unit])
(defrecord SearchOnChange [attribute value])
(defrecord UpdateFilteredUnitIDs [attribute ids])
(defrecord SubmitLandPurchaseForm [form-data cadastral-id])
(defrecord FetchLandAcquisitions [project-id])
(defrecord LandAcquisitionFetchSuccess [result])
(defrecord FetchEstateInfos [estate-ids retry-count])
(defrecord FetchEstateResponse [response])
(defrecord FetchRelatedEstatesResponse [response])
(defrecord FetchRelatedEstates [])
(defrecord ToggleOpenEstate [estate-id])
(defrecord SubmitEstateCompensationForm [form-data estate-id])

(defrecord UpdateOwnerCompensationForm [owner-set form-data])
(defrecord UpdateEstateForm [owner-set estate-id form-data])
(defrecord UpdateCadastralForm [owner-set cadastral-id form-data])


(defn toggle-selected-unit
  [id cad-units]
  (map
    (fn [unit]
      (assoc unit :selected? (and (= (:teet-id unit)
                                     id)
                                  (not (:selected? unit)))))
    cad-units))


(defn field-includes? [s substr]
  ;; like str/includes?, but:
  ;; case is ignored, substr is whitespace-trimmed first and nil/"" are handled to suit our needs

  (if (str/blank? s)
    false
    (str/includes? (str/trim (str/lower-case s)) (str/lower-case (or substr "")))))

(defn any-includes? [s-seq substr]
  (some #(field-includes? % substr) s-seq))

(defn owner-filter-fn [query unit]
  (let [owners (get-in unit [:estate :omandiosad])
        match? (fn [owner]
                 (let [fvals (vals (select-keys owner [:nimi :eesnimi :r_kood]))]
                   ;; (println "any-includes?" fvals query)
                   (any-includes? fvals query)))]

    (if (and (not-empty owners)
             (some match? owners))
      (do
        ;; (println "owner filter: TRUE for " (mapv (juxt :nimi :eesnimi) owners) "queried for" query)
        ;; (println "owners:" owners)
        true)
      (do
        ;; (println "owner filter: FALSE for " (mapv (juxt :nimi :eesnimi) owners) "queried for" query)
        false))))

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
                 [:route :project :land/units]
                 (partial toggle-selected-unit (:teet-id unit)))))

  ToggleOpenEstate
  (process-event [{estate-id :estate-id} app]
    (update-in app [:route :project :land/open-estates] cu/toggle estate-id))

  UpdateFilteredUnitIDs
  (process-event [{attribute :attribute value :value} app]
    ;; (.log js/console "UFUI: a" (pr-str attribute) "v" (pr-str value))
    (let [f-value #(get-in app [:route :project :land-acquisition-filters %])
          f-select-value #(:value (f-value %)) ;; use f-select-value with form-select, but not with select-enum
          filters [(fn est [unit] ; name-search filter (estate address really)
                     #_(println "l_aadress" (:L_AADRESS unit))
                     (let [r  (field-includes? (or (:L_AADRESS unit) "") (f-value :estate-search-value))]
                       #_(println "aaddress match?" (boolean r) (f-value :estate-search-value))
                       r))
                   (partial owner-filter-fn (f-value :owner-search-value))
                   (fn cad [unit] ;; cadastral
                     (field-includes? (:TUNNUS unit) (f-value :cadastral-search-value)))
                   (fn quality [unit] ; quality filter
                     (if-let [q (f-select-value :quality)]
                       (= q (:quality unit))
                       true))
                   (fn impact [unit] ; impact filter
                     (if-let [q (when (:impact unit)  (f-value :impact))]
                       (do
                         ;; (println "compare impact" q (:impact unit) unit)
                         (= q (:impact unit)))
                       true))
                   #_(fn process [unit] ; process filter ;; waiting for process status to be added
                     (if-let [q (f-select-value :process)]
                       (= q (:process unit))
                       true))]
          ;; text (str/lower-case (get-in app [:route :project :land-acquisition-filters attribute]))
          units (get-in app [:route :project :land/units])
          filtered-ids (->> units
                            (filterv (apply every-pred filters))
                            (mapv :teet-id)
                            set)]
      (.log js/console "filter result:" (count filtered-ids) "of" (count units))
      (assoc-in app [:route :project :land/filtered-unit-ids] filtered-ids)))

  SearchOnChange
  (process-event
    [{attribute :attribute
      value :value} app]
    ;; (println "SearchOnChange" attribute value)
    (t/fx (assoc-in app [:route :project :land-acquisition-filters attribute] value)
          {:tuck.effect/type :debounce
           :timeout 600
           :id :change-cadastral-search
           :event #(->UpdateFilteredUnitIDs attribute value)}))

  FetchLandAcquisitions
  (process-event
    [{project-id :project-id} app]
    (t/fx app
          {:tuck.effect/type :query
           :query :land/fetch-land-acquisitions
           :args {:project-id project-id}
           :result-event ->LandAcquisitionFetchSuccess}))

  LandAcquisitionFetchSuccess
  (process-event
    [{result :result} app]
    (-> app
        (update-in [:route :project] merge result)
        (update-in [:route :project] dissoc :thk.project/related-cadastral-units-info)))

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

  SubmitEstateCompensationForm
  (process-event [{:keys [form-data estate-id]} app]
    (let [project-id (get-in app [:params :project])]
      (t/fx app
            {:tuck.effect/type :command!
             :command :land/create-estate-procedure
             :success-message "Foo! ✅"                      ;;todo proper message
             :payload (merge
                        form-data                           ;; TODO add select keys
                        {:thk.project/id project-id
                         :estate-procedure/estate-id estate-id})
             :result-event (partial ->FetchLandAcquisitions project-id) ;;TODO fetch esate compensations
             })))

  FetchRelatedEstates
  (process-event [_ {:keys [params] :as app}]
    (let [project-id (:project params)
          fetched (get-in app [:route :project :fetched-estates-count])
          estates-count (count (get-in app [:route :project :land/related-estate-ids]))]
      (if (= fetched estates-count)
        app
        (t/fx (update-in app [:route :project] dissoc :land/related-estates)
              {:tuck.effect/type :query
               :query :land/related-project-estates
               :args {:thk.project/id project-id}
               :result-event ->FetchRelatedEstatesResponse}))))

  FetchRelatedEstatesResponse
  (process-event [{{:keys [estates units]} :response} app]
    (t/fx (-> app
              (assoc-in [:route :project :land/related-estate-ids] estates)
              (assoc-in [:route :project :fetched-estates-count] 0)
              (assoc-in [:route :project :land/units] units)
              (assoc-in [:route :project :land/estate-info-failure] false))
          (fn [e!]
            (e! (->FetchEstateInfos estates 1)))))

  FetchEstateResponse
  (process-event [{:keys [response]} app]
    (-> app
        (update-in [:route :project :land/units]
                   (fn [units]
                     (mapv
                       #(if (= (:KINNISTU %) (:estate-id response))
                          (assoc % :estate response)
                          %)
                       units))
                   response)
        (update-in [:route :project :fetched-estates-count] inc)))

  FetchEstateInfos
  (process-event [{estate-ids :estate-ids
                   retry-count :retry-count} {:keys [params] :as app}]
    (let [project-id (:project params)]
      (apply t/fx app
             (for [estate-id estate-ids]
               (merge {:tuck.effect/type :query
                       :query :land/estate-info
                       :args {:estate-id estate-id
                              :thk.project/id project-id}
                       :result-event ->FetchEstateResponse
                       :error-event (fn [error]
                                      (if (= (:error (ex-data error)) :request-timeout)
                                        (if (pos? retry-count)
                                          (->FetchEstateInfos [estate-id] (dec retry-count))
                                          (common-controller/->ResponseError (ex-info "x road failed to respond" {:error :invalid-x-road-response})))
                                        (common-controller/->ResponseError error)))}))))))

;; Events for updating different forms in land purchase
(extend-protocol t/Event
  UpdateOwnerCompensationForm
  (process-event [{:keys [owner-set form-data]} app]
    (common-controller/update-page-state
     app
     [:land/forms owner-set :land/owner-compensation-form]
     merge form-data))

  UpdateEstateForm
  (process-event [{:keys [owner-set estate-id form-data]} app]
    (common-controller/update-page-state
     app
     [:land/forms owner-set :land/estate-forms estate-id]
     merge form-data))

  UpdateCadastralForm
  (process-event [{:keys [owner-set cadastral-id form-data]} app]
    (common-controller/update-page-state
     app
     [:land/forms owner-set :land/cadastral-forms cadastral-id]
     merge form-data)))

(defmethod common-controller/on-server-error :invalid-x-road-response [err app]
  (let [error (-> err ex-data :error)]
    (if (get-in app [:route :project :land/estate-info-failure])
      app
      (t/fx (snackbar-controller/open-snack-bar (assoc-in app [:route :project :land/estate-info-failure] true) (tr [:error error]) :warning)))))
