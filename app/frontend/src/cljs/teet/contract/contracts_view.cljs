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
            [clojure.string :as str]
            [teet.ui.url :as url]
            [teet.ui.common :as common]
            [teet.environment :as environment]
            [teet.contract.contract-model :as contract-model]
            [teet.contract.contract-common :as contract-common]
            [teet.contract.contract-style :as contract-style]
            [teet.ui.typography :as typography]
            [teet.ui.container :as container]
            [teet.contract.contract-status :as contract-status]
            [teet.common.common-styles :as common-styles]))

(defn contract-card-header
  [contract-status contract-name contract-url-id]
  [:div {:class (<class contract-style/contract-card-header-component-style)}
   [contract-status/contract-status
    {:container-class (<class contract-style/contract-card-header-component-status-style)
     :show-label? false}
    contract-status]
   [:h4 contract-name]
   [url/Link {:page :contract
              :params {:contract-ids contract-url-id}}
    (str (tr [:contracts :contracts-list :view]))]])

(defn contract-card
  [e! contract contract-expansion-atom]
  (r/with-let []
    [container/collapsible-container {:class (<class contract-style/contract-card-style)
                                      :container-class (<class contract-style/contract-card-style-header)
                                      :collapsible-class (<class contract-style/contract-card-style-container)
                                      :side-component (contract-card-header
                                                        (:thk.contract/status contract)
                                                        (contract-model/contract-name contract)
                                                        (contract-model/contract-url-id contract))
                                      :on-toggle #(swap! contract-expansion-atom
                                                    update-in [(:db/id contract)] (fn [x] (not x)))
                                      :open? (get @contract-expansion-atom (:db/id contract))}
     ""
     [:div {:class (<class contract-style/contract-card-details-style)}
      [contract-common/contract-external-links contract]
      [contract-common/contract-information-row contract]]]))

(defn contract-list-expansion-buttons
  [expand-list collapse-list]
  [:div
   [buttons/button-secondary {:size :small
                              :class (<class contract-style/contract-list-secondary-button ".5rem")
                              :on-click expand-list
                              :start-icon (r/as-element [icons/navigation-unfold-more])}
    (tr [:contracts :contracts-list :expand-all])]
   [buttons/button-secondary {:size :small
                              :class (<class contract-style/contract-list-secondary-button ".5rem")
                              :on-click collapse-list
                              :start-icon (r/as-element [icons/navigation-unfold-less])}
    (tr [:contracts :contracts-list :collapse-all])]])

(defn contacts-list-header
  [{:keys [contracts-count expand-list collapse-list]}]
  [:div {:class (<class contract-style/contracts-list-header-style)}
   [contract-list-expansion-buttons expand-list collapse-list]
   [typography/SmallText (str contracts-count " "
                           (tr (if (= contracts-count 1)
                                 [:contracts :contracts-list :result]
                                 [:contracts :contracts-list :results])))]])

(defn contracts-list
  [e! contracts]
  (r/with-let [contract-expansion-atom (r/atom (reduce (fn [agg x] (assoc agg (:db/id  x) false)) {} contracts))]
    [:div {:class (<class contract-style/contracts-list-style)}
     [contacts-list-header {:contracts-count (count contracts)}]
     (doall
       (for [contract contracts]
         ^{:key (str (:db/id contract))}
         [contract-card e! contract contract-expansion-atom]))]))

(def filter-options
  [:my-contracts
   :all-contracts
   :unassigned])

(def filter-fields-types
  [:search-field
   :select-enum-field
   :select-form-field
   :select-user-field])

(defn toggle-filters-visibility-button
  [filters-visibility? toggle-filters-visibility]
  [buttons/button-secondary {:size :small
                             :class (<class contract-style/contract-list-secondary-button 0)
                             :on-click toggle-filters-visibility
                             :start-icon (r/as-element [icons/content-filter-alt-outlined])}
   (if filters-visibility?
     (tr [:contracts :filters :hide-filters])
     (tr [:contracts :filters :show-filters]))])


(defn search-shortcuts
  [{:keys [value options change-shortcut]}]
  [:<>
   (into [:div {:class (<class contract-style/search-shortcut-items-style)}]
     (mapv
       (fn [option]
         (let [selected? (= option value)]
           [:<>
            (if selected?
              [:span {:class (<class contract-style/search-shortcut-item-style selected?)
                      :on-click #(change-shortcut option)}
               (tr [:contracts :shortcuts option])]
              [:span
               [buttons/link-button {:class (<class contract-style/search-shortcut-item-style selected?)
                                     :on-click #(change-shortcut option)}
                (tr [:contracts :shortcuts option])]])]))
       options))])

(defn filters-header
  [{:keys [shortcut-value options change-shortcut filters-visibility?
           toggle-filters-visibility]}]
  [:div {:class (<class contract-style/filters-header-style)}
   [search-shortcuts {:value shortcut-value
                      :change-shortcut change-shortcut
                      :options options}]
   [toggle-filters-visibility-button filters-visibility? toggle-filters-visibility]])

(def filter-fields
  [[:road-number {:type :search-field}]
   [:project-name {:type :search-field}]
   [:contract-name {:type :search-field}]
   [:procurement-number {:type :search-field}]
   [:contract-number {:type :search-field}]
   [:partner-name {:type :search-field}]
   [:project-manager {:type :select-user-field
                :field-options {:attribute :t:activity/manager
                                :show-empty-selection? true}}]

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
  [input-options _]
  [TextField (merge input-options
               {:start-icon icons/action-search
                :placeholder (tr [:search :quick-search])})])

(defmethod filter-input :select-enum-field
  [input-options _]
  [select/select-enum input-options])

(defmethod filter-input :select-form-field
  [input-options _]
  [select/form-select input-options])

(defmethod filter-input :select-user-field
  [input-options _]
  [select/select-user input-options])

(defn filter-inputs
  [{:keys [e! filter-input-fields filter-values input-change filters-visibility? clear-filters]}]
  [:div {:style {:display (if filters-visibility? :block :none)}}
   [:div {:class (<class contract-style/filter-inputs-style)}
    (into [:<>]
      (mapv
        (fn [[field-name {field-type :type
                          field-options :field-options}]]
          (let [general-input-options (merge {:e! e!
                                              :class (<class contract-style/filter-input-style)
                                              :label (tr [:contracts :filters :inputs field-name])
                                              :value (field-name filter-values)
                                              :on-change #(input-change
                                                            field-name
                                                            (if (= field-type :search-field)
                                                              (-> % .-target .-value)
                                                              %))}
                                        field-options)]
            (filter-input general-input-options field-type)))
        filter-input-fields))]
   [buttons/link-button-with-icon {:class (<class contract-style/clear-filters-button-style)
                                   :on-click clear-filters
                                   :icon [icons/content-clear]}
    (tr [:search :clear-filters])]])

(defn contract-search
  [{:keys [e! filter-options clear-filters filtering-atom input-change change-shortcut]}]
  (r/with-let [filters-visibility? (r/atom false)
               toggle-filters-visibility #(swap! filters-visibility? not)]
    [:div {:class (<class contract-style/contract-search-container-style)}
     [:div {:class (<class contract-style/search-header-style)}
      [:div {:class (<class contract-style/quick-filters-header-style)}
       (tr [:contracts :quick-filters])]]
     [filters-header {:shortcut-value (:shortcut @filtering-atom)
                      :options filter-options
                      :change-shortcut change-shortcut
                      :filters-visibility? @filters-visibility?
                      :toggle-filters-visibility toggle-filters-visibility}]
     [filter-inputs {:e! e!
                     :filter-input-fields filter-fields
                     :filter-values @filtering-atom
                     :input-change input-change
                     :filters-visibility? @filters-visibility?
                     :clear-filters clear-filters}]]))

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
    [Grid {:style {:flex 1}
           :class (<class common-styles/flex-1)
           :container true}
     [Grid {:item true
            :xs 12
            :style {:display :flex}}
      [:div {:class (<class contract-style/contract-page-container-style)}
       [:h1 (tr [:contracts :shortcuts (:shortcut @filtering-atom)])]
       [contract-search {:e! e!
                         :filtering-atom filtering-atom
                         :filter-options filter-options
                         :input-change input-change
                         :change-shortcut change-shortcut
                         :clear-filters clear-filters}]
       [query/debounce-query
        {:e! e!
         :query :contracts/list-contracts
         :args {:search-params @filtering-atom
                :refresh (:contract-refresh route)}
         :simple-view [contracts-list e!]}
        250]]]]))
