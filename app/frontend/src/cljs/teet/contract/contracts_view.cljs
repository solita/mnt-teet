(ns teet.contract.contracts-view
  (:require [reagent.core :as r]
            [herb.core :refer [<class]]
            [teet.ui.material-ui :refer [Grid]]
            [teet.ui.query :as query]
            [alandipert.storage-atom :refer [local-storage]]
            [teet.ui.buttons :as buttons]
            [teet.localization :refer [tr]]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.icons :as icons]
            [teet.ui.select :as select]
            [teet.common.common-styles :as common-styles]
            [clojure.string :as str]
            [teet.ui.url :as url]
            [teet.ui.common :as common]
            [teet.environment :as environment]
            [teet.contract.contract-model :as contract-model]
            [teet.contract.contract-view :as contract-view]
            [teet.contract.contract-style :as contract-style]))

(defn contract-card
  [e! {:thk.contract/keys [procurement-id procurement-part-id procurement-number external-link]
       contract-name :thk.contract/name :as contract}]
  [:div {:class (<class common-styles/margin-bottom 2)}
   [:h3 contract-name]
   [:span (pr-str contract)]
   [contract-view/contract-procurement-link contract]
   (when external-link
     [contract-view/contract-external-link contract])
   [common/external-contract-link {:href (str (environment/config-value :contract :thk-procurement-url) procurement-id)}
    (str/upper-case
      (str (tr [:contracts :thk-procurement-link]) " " procurement-id))]
   [url/Link {:page :contract
              :params {:contract-ids (contract-model/contract-url-id contract)}}
    "LINK TO THIS ACTIVITY"]])

(defn contracts-list
  [e! contracts]
  [:div
   [:h2 (count contracts)]
   (doall
     (for [contract contracts]
       ^{:key (str (:db/id contract))}
       [contract-card e! contract]))])

(def filter-options
  [:my-contracts
   :all-contracts
   :unassigned])

(def filter-fields-types
  [:search-field
   :select-enum-field
   :select-form-field])

(defn toggle-filter-visibility-button
  [visible? on-click-handler]
  [buttons/button-secondary {:size :small
                             :style {:padding "0 1.18rem"
                                     :box-sizing :border-box
                                     :height "1.5rem"}
                             :on-click on-click-handler
                             :start-icon (r/as-element [icons/content-filter-alt-outlined])}
   (if visible?
     (tr [:contracts :filters :hide-filters])
     (tr [:contracts :filters :show-filters]))])

(defn search-shortcuts
  [value options change-shortcut visible-filters? toggle-visibility]
  [:div {:class (<class contract-style/search-shortcuts)}
  (into [:div {:class (<class contract-style/search-shortcut-items)}]
        (mapv
          (fn [option]
            (let [selected? (= option value)]
              [:<>
               (if selected?
                 [:span {:class (<class contract-style/search-shortcut-item selected?)
                         :on-click #(change-shortcut option)}
                  (tr [:contracts :shortcuts option])]
                 [:span
                  [buttons/link-button {:class (<class contract-style/search-shortcut-item selected?)
                                        :on-click #(change-shortcut option)}
                   (tr [:contracts :shortcuts option])]])]))
          options))
   [toggle-filter-visibility-button ]])

(def search-fields
  [[:road-number {:type TextField}]
   [:project-name {:type TextField}]
   [:contract-name {:type TextField}]
   [:procurement-id {:type TextField}]
   [:procurement-number {:type TextField}]
   [:ta/region {:type select/select-enum
                :field-options {:attribute :ta/region
                                :show-empty-selection? true}}]
   [:contract-type {:type select/select-enum
                    :field-options {:attribute :thk.contract/type
                                    :show-empty-selection? true}}]
   [:contract-status {:type select/form-select
                      :field-options {:items contract-model/contract-statuses
                                      :format-item #(tr [:contract %])
                                      :attribute :thk.contract/status
                                      :show-empty-selection? true}}]])

(def filter-fields
  [[:road-number {:type :search-field}]
   [:project-name {:type :search-field}]
   [:contract-name {:type :search-field}]
   [:procurement-id {:type :search-field}]
   [:procurement-number {:type :search-field}]
   [:ta/region {:type :select-enum-field
                :field-options {:attribute :ta/region
                                :show-empty-selection? true}}]
   [:contract-type {:type :select-enum-field
                    :field-options {:attribute :thk.contract/type
                                    :show-empty-selection? true}}]
   [:contract-status {:type :select-form-field
                      :field-options {:items contract-model/contract-statuses
                                      :format-item #(tr [:contract %])
                                      :attribute :thk.contract/status
                                      :show-empty-selection? true}}]])

(defmulti filter-input (fn [input-options filter-field-type]
                         filter-field-type))

(defmethod filter-input :search-field
  [input-options]
  [TextField (merge input-options
               {:start-icon icons/action-search
                :placeholder (tr [:search :quick-search])})])

(defmethod filter-input :select-enum-field
  [input-options]
  [select/select-enum input-options])

(defmethod filter-input :select-form-field
  [input-options]
  [select/form-select input-options])

(defn filter-inputs
  [e! filter-values input-change search-fields]
  [:div
   (into [:<>]
     (mapv
       (fn [[field-name {field-type :type
                         field-options :field-options}] ]
         (let [general-input-options (merge {:e! e!
                                      :label (tr [:contracts :filters :inputs field-name])
                                      :value (field-name filter-values)
                                      :on-change #(input-change
                                                    field-name
                                                    (if (= field-type :search-field)
                                                      (-> % .-target .-value)
                                                      %))}
                                       field-options)]
           (filter-input general-input-options field-type)))
       search-fields))])

(defn contract-search
  [{:keys [e! filter-options clear-filters search-fields
           filtering-atom input-change change-shortcut]}]
  [:div
   [:div {:class (<class contract-style/search-header)}
    [:div {:class (<class contract-style/quick-filters-header)}
     (tr [:contracts :quick-filters])]]
   [search-shortcuts (:shortcut @filtering-atom) filter-options change-shortcut]
   ;[search-inputs e! @filtering-atom input-change search-fields]
   [filter-inputs e! @filtering-atom input-change filter-fields]
   [buttons/link-button {:on-click clear-filters}
    "CLEAR FILTERS"]])

;; Targeted from routes.edn will be located in route /contracts
(defn contracts-listing-view
  [e! {route :route :as app}]
  (r/with-let [default-filtering-value {:shortcut :my-contracts}
               filtering-atom (local-storage (r/atom default-filtering-value)
                                             :contract-filters)
               change-shortcut #(swap! filtering-atom assoc :shortcut %)
               input-change (fn [key value]
                              (swap! filtering-atom assoc key value))
               clear-filters #(reset! filtering-atom default-filtering-value)]
    [Grid {:container true}
     [Grid {:item true
            :xs 12}
      [:div {:class (<class contract-style/contract-page-container)}
       [:h1 (tr [:contracts :shortcuts (:shortcut @filtering-atom)])]
       [contract-search {:e! e!
                         :search-fields search-fields
                         :filtering-atom filtering-atom
                         :filter-options filter-options
                         :input-change input-change
                         :change-shortcut change-shortcut
                         :clear-filters clear-filters}]]]
     [:h1 "CONTRACTS LISTING"]
     [query/debounce-query
      {:e! e!
       :query :contracts/list-contracts
       :args {:search-params @filtering-atom
              :refresh (:contract-refresh route)}
       :simple-view [contracts-list e!]}
      250]]))
