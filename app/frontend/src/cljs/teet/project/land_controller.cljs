(ns teet.project.land-controller
  (:require [tuck.core :as t]
            [clojure.string :as str]
            [teet.util.collection :as cu]
            [teet.localization :refer [tr]]
            [teet.map.map-controller :as map-controller]
            [goog.math.Long]
            [teet.common.common-controller :as common-controller]
            [teet.snackbar.snackbar-controller :as snackbar-controller]
            [taoensso.timbre :as log]))

(defrecord ToggleLandUnit [unit])
(defrecord SearchOnChange [attribute value])
(defrecord UpdateFilteredUnitIDs [attribute ids])
(defrecord SubmitLandAcquisitionForm [form-data cadastral-id estate-procedure-type estate-id])
(defrecord FetchLandAcquisitions [project-id])
(defrecord LandAcquisitionFetchSuccess [result])
(defrecord FetchEstateInfos [estate-ids retry-count])
(defrecord FetchEstateResponse [response])
(defrecord FetchRelatedEstatesResponse [response])
(defrecord FetchRelatedEstates [])
(defrecord ToggleOpenEstate [estate-id])
(defrecord SubmitEstateCompensationForm [form-data estate-id])
(defrecord FetchEstateCompensations [project-id])
(defrecord FetchEstateCompensationsResponse [response])

(defrecord UpdateEstateForm [estate form-data])
(defrecord CancelEstateForm [estate-id])
(defrecord CancelLandAcquisition [unit])
(defrecord UpdateCadastralForm [cadastral-id form-data])

(defrecord RefreshEstateInfo [])
(defrecord IncrementEstateCommentCount [estate-id])
(defrecord DecrementEstateCommentCount [estate-id])
(defrecord IncrementUnitCommentCount [unit-id])
(defrecord DecrementUnitCommentCount [unit-id])

(defn toggle-selected-unit
  [id cad-units]
  (map
    (fn [unit]
      (assoc unit :selected? (and (= (:teet-id unit)
                                     id)
                                  (not (:selected? unit)))))
    cad-units))


(defn unit-last-updated [unit]
  (let [timestamp-strs (-> unit
                           (select-keys [:REGISTR :MUUDET :MOODUST] )
                           vals)]
    (->> timestamp-strs
         (filter some?)
         sort
         last)))

(defn unit-new?
  "Decides whether we show it as \"NEW\" in the UI, for purposes of
  deciding if it may be replacement for a deleted unit."
  [tunnus units]
  (let [this-unit (first (filter #(= tunnus (:TUNNUS %))
                                 units))
        deleted-units (filter :deleted units)
        deleted-min-timestamp (->> deleted-units
                                   (map unit-last-updated)
                                   sort
                                   first)]
    (if deleted-min-timestamp
      (> (unit-last-updated this-unit) deleted-min-timestamp)
      ;; else
      (if this-unit
        false
        true)))) ;; new if not in the set of known cadastral units

(defn cadastral-purposes [tunnus unit]
  (->> unit
       :estate
       :katastriyksus
       (filterv #(= (:katastritunnus %) tunnus))
       first
       :sihtotstarbed
       (mapv :sihtotstarve_tekst)
       set
       (clojure.string/join ", ")))


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
    (if (empty? query)
      true
      (if (and (not-empty owners)
               (some match? owners))
        true
        false))))

(def owner-type-can-receive-process-fee #{"Füüsiline isik"
                                          "Juriidiline isik"})
(defn format-process-fees
  "Format process fee rows for sending to server."
  [form-data]
  (cu/update-in-if-exists
   form-data
   [:estate-procedure/process-fees]
   (fn [process-fees]
     (vec
      (for [{pfr :process-fee-recipient :as pf} process-fees
            :when (seq pf)]
        (merge
         (select-keys pf [:db/id :estate-process-fee/fee])
         {:estate-process-fee/recipient (:recipient pfr)}
         (when-let [owner (and (not (contains? pf :estate-process-fee/business-id))
                               (not (contains? pf :estate-process-fee/person-id))
                               (:owner pfr))]
           ;; Owner of the estate, add person or business code
           (case (:isiku_tyyp owner)
             ;; Physical person
             "Füüsiline isik"
             {:estate-process-fee/person-id (str "EE" (:r_kood owner))}

             ;; Legal entity with business id (but not public)
             "Juriidiline isik"
             {:estate-process-fee/business-id (str "EE" (:r_kood owner))}))))))))

(extend-protocol t/Event

  CancelLandAcquisition
  (process-event [{unit :unit} app]
    (t/fx (-> app
              (update-in [:route :project :land/cadastral-forms (:teet-id unit)]
                         (fn [form-data]
                           (if-let [saved (:saved-data form-data)]
                             saved
                             form-data))))
          (fn [e!]
            (e! (->ToggleLandUnit unit)))))

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


  CancelEstateForm
  (process-event [{estate-id :estate-id} app]
    (-> app
        (update-in [:route :project :land/estate-forms estate-id] (fn [form-data]
                                                                    (if-let [saved (:saved-data form-data)]
                                                                      saved
                                                                      form-data)))
        (update-in [:route :project :land/open-estates] cu/toggle estate-id)))

  UpdateFilteredUnitIDs
  (process-event [{attribute :attribute value :value} app]
    ;; (.log js/console "UFUI: a" (pr-str attribute) "v" (pr-str value))
    (let [f-value #(get-in app [:route :project :land-acquisition-filters %])
          f-select-value #(:value (f-value %)) ;; use f-select-value with form-select, but not with select-enum
          filters [(fn est [unit] ; estate id search filter
                     (let [r (field-includes? (or (get-in unit [:estate :estate-id]) "") (f-value :estate-search-value))]                       #_(println "aaddress match?" (boolean r) (f-value :estate-search-value))
                       r))
                   (partial owner-filter-fn (f-value :owner-search-value))
                   (fn cad [unit] ;; cadastral
                     (field-includes? (:TUNNUS unit) (f-value :cadastral-search-value)))
                   (fn quality [unit] ; quality filter
                     (if-let [q (f-select-value :quality)]
                       (= q (:quality unit))
                       true))
                   (fn impact [unit] ; impact filter
                     (let [unit-impact (get-in app [:route :project :land/cadastral-forms (:teet-id unit) :land-acquisition/impact])
                           impact (if (nil? unit-impact)
                                    :land-acquisition.impact/undecided
                                    unit-impact)]
                       (if (f-value :impact)
                         (= (f-value :impact) impact)
                         true)))
                   (fn status [unit] ; status filter
                     (let [unit-status (get-in app [:route :project :land/cadastral-forms (:teet-id unit) :land-acquisition/status])]
                       (log/debug "status filter fn: unit-status" unit-status "f-status" (f-value :land-acquisition/status))
                       (if-let [q (f-value :status)]
                         (= q unit-status)
                         true)))]
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
    (let [land-acquisitions (:land-acquisitions result)
          related-cadastral-units (:thk.project/related-cadastral-units result)]

      (-> app
          (assoc-in [:route :project :thk.project/related-cadastral-units] related-cadastral-units)
          (assoc-in [:route :project :land-acquisitions] land-acquisitions)
          (update-in [:route :project :land/cadastral-forms]
                     (fn [cadastral-forms]
                       (into (or cadastral-forms {})
                             (for [{:land-acquisition/keys [cadastral-unit] :as form} land-acquisitions]
                               [cadastral-unit form]))))
          (update-in [:route :project] merge result)
          (update-in [:route :project] dissoc :thk.project/related-cadastral-units-info))))

  SubmitLandAcquisitionForm
  (process-event [{:keys [form-data cadastral-id estate-procedure-type estate-id]} app]
    (let [project-id (get-in app [:params :project])
          {:land-acquisition/keys [area-to-obtain pos-number]} form-data]
      (t/fx app
            {:tuck.effect/type :command!
             :command (if (:db/id form-data)
                        :land/update-land-acquisition
                        :land/create-land-acquisition)
             :success-message (tr [:land :land-acquisition-saved])
             :payload (merge (if (= estate-procedure-type :estate-procedure.type/urgent)
                               (dissoc form-data :land-acquisition/price-per-sqm)
                               form-data)
                             {:cadastral-unit cadastral-id
                              :land-acquisition/estate-id estate-id
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
             :command (if (:db/id form-data)
                        :land/update-estate-procedure
                        :land/create-estate-procedure)
             :success-message (tr [:land :estate-compensation-success])
             :payload (-> (dissoc form-data :saved-data)
                          (merge
                            {:thk.project/id project-id
                             :estate-procedure/estate-id estate-id})
                          format-process-fees)
             :result-event (partial ->FetchEstateCompensations project-id)})))

  FetchEstateCompensations
  (process-event [{project-id :project-id} app]
    (t/fx app
          {:tuck.effect/type :query
           :query :land/fetch-estate-compensations
           :args {:thk.project/id project-id}
           :result-event ->FetchEstateCompensationsResponse}))

  FetchEstateCompensationsResponse
  (process-event [{response :response} app]
    (-> app
        (common-controller/update-page-state
          [:land/estate-forms]
          (fn [estate-forms]
            (into (or estate-forms {})
                  (for [[estate-id form] response]
                    [estate-id
                     (cu/update-in-if-exists
                       form [:estate-procedure/process-fees]
                       (fn [process-fees]
                         (mapv #(assoc % :process-fee-recipient
                                         {:owner (or (:estate-process-fee/person-id %)
                                                     (:estate-process-fee/business-id %))
                                          :recipient (:estate-process-fee/recipient %)})
                               process-fees)))]))))
        (assoc-in [:route :project :estate-compensations]
                  (mapv (fn [[_ value]]
                          value)
                        response))))
  FetchRelatedEstates
  (process-event [_ {:keys [params] :as app}]
    (let [project-id (:project params)
          fetched (get-in app [:route :project :fetched-estates-count])
          estates-count (count (get-in app [:route :project :land/related-estate-ids]))]
      (if (= fetched estates-count)
        app
        (t/fx
         ;; Set as empty vector while fetch, so UI doesn't trigger another fetch
         (assoc-in app [:route :project :land/related-estate-ids] [])
         {:tuck.effect/type :query
          :query :land/related-project-estates
          :args {:thk.project/id project-id}
          :result-event ->FetchRelatedEstatesResponse}))))

  FetchRelatedEstatesResponse
  (process-event [{{:keys [estates units estate-info]} :response} app]
    (-> app
        (assoc-in [:route :project :land/related-estate-ids] estates)
        (assoc-in [:route :project :land/units] units)
        (assoc-in [:route :project :land/estate-info-failure] false)))

  RefreshEstateInfo
  (process-event [_ app]
    (t/fx (update-in app [:route :project] dissoc
                     :land/related-estate-ids
                     :land/units
                     :land/estate-info-failure)
          {:tuck.effect/type :command!
           :command :land/refresh-estate-info
           :payload {:thk.project/id (get-in app [:params :project])}
           :result-event common-controller/->Refresh}))

  IncrementEstateCommentCount
  (process-event [{estate-id :estate-id} app]
    (update-in app [:route :project :estate-comment-count estate-id] (fnil inc 0)))

  IncrementUnitCommentCount
  (process-event [{unit-id :unit-id} app]
    (update-in app [:route :project :unit-comment-count unit-id] (fnil inc 0)))

  DecrementEstateCommentCount
  (process-event [{estate-id :estate-id} app]
    (println "DECREMENT ESTATE COMMENTCOUNT : " estate-id)
    (update-in app [:route :project :estate-comment-count estate-id] (fnil dec 0)))

  DecrementUnitCommentCount
  (process-event [{unit-id :unit-id} app]
    (println "DECREMENT UNIT COMMENTCOUNT : " unit-id)
    (update-in app [:route :project :unit-comment-count unit-id] (fnil dec 0))))

(defn- estate-owner-process-fees [{owners :omandiosad :as _estate}]
  (let [private-owners
        (vec
         (keep (fn [owner]
                 (when (owner-type-can-receive-process-fee (:isiku_tyyp owner))
                   {:process-fee-recipient {:recipient
                                            (str (:eesnimi owner) " " (:nimi owner))
                                            :owner owner}}))
               owners))]
    (if (empty? private-owners)
      ;; Add single empty owner, if there are none
      [{}]
      private-owners)))

;; Events for updating different forms in land purchase
(extend-protocol t/Event
  UpdateEstateForm
  (process-event [{:keys [estate form-data]} app]
    (common-controller/update-page-state
     app
     [:land/estate-forms (:estate-id estate)]
     (fn [old-data]
       (let [computed
             (cond
               ;; When acquisition thru negotiation, automatically add
               ;; process fee rows for all owners
               (and (= :estate-procedure.type/acquisition-negotiation
                       (:estate-procedure/type form-data))
                    (not= :estate-procedure.type/acquisition-negotiation
                          (:estate-procedure/type old-data))
                    (not (contains? old-data :estate-procedure/process-fees)))
               {:estate-procedure/process-fees (estate-owner-process-fees estate)})]
         (merge old-data form-data computed
                (when (not (:saved-data old-data))
                  {:saved-data (if old-data
                                 (dissoc old-data :saved-data)
                                 {})}))))))

  UpdateCadastralForm
  (process-event [{:keys [cadastral-id form-data]} app]
    (common-controller/update-page-state
     app
     [:land/cadastral-forms cadastral-id]
     (fn [old-data]
       (merge old-data form-data
              (when (not (:saved-data old-data))
                {:saved-data (if old-data
                               (dissoc old-data :saved-data)
                               {})}))))))

(defmethod common-controller/on-server-error :invalid-x-road-response [err app]
  (let [error (-> err ex-data :error)]
    (if (get-in app [:route :project :land/estate-info-failure])
      app
      (t/fx (snackbar-controller/open-snack-bar (assoc-in app [:route :project :land/estate-info-failure] true) (tr [:error error]) :warning)))))
