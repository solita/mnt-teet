(ns teet.contract.contracts-view
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [Grid]]
            [teet.ui.query :as query]
            [alandipert.storage-atom :refer [local-storage]]
            [teet.ui.buttons :as buttons]
            [teet.localization :refer [tr]]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.icons :as icons]))

(defn contract-card
  [e! contract]
  [:h3 (pr-str contract)])

(defn contracts-list
  [e! contracts]
  [:div
   (doall
     (for [contract contracts]
       ^{:key (str (:db/id contract))}
       [contract-card e! contract]))])

(def filter-options
  [:my-contracts
   :all-contracts
   :unassigned])

(defn search-shortcuts
  [value options change-shortcut]
  (into [:div
         [:span "SHORTCUTS"]]
        (mapv
          (fn [option]
            (let [selected? (= option value)]
              [:<>
               (if selected?
                 [:div
                  [:strong (tr [:contracts :shortcuts option])]]
                 [:div
                  [buttons/link-button {:on-click #(change-shortcut option)}
                   (tr [:contracts :shortcuts option])]])]))
          options)))

(def search-fields
  {:road-number {:type TextField}
   :object-name {:type TextField}})

(defn search-inputs
  [filter-values input-change search-fields]
  [:div
   (into [:div]
         (mapv
           (fn [[field-name {field-type :type}]]
             [field-type {:value (field-name filter-values)
                         :start-icon icons/action-search
                         :on-change #(input-change field-name (-> % .-target .-value))}])
           search-fields))])

(defn contract-search
  [{:keys [e! filter-options clear-filters search-fields
           filtering-atom input-change change-shortcut]}]

  [:div
   [buttons/link-button {:on-click clear-filters}
    "CLEAR FILTERS"]
   [search-shortcuts (:shortcut @filtering-atom) filter-options change-shortcut]
   [search-inputs @filtering-atom input-change search-fields]])


;; Targeted from routes.edn will be located in route /contracts
(defn contracts-listing-view
  [e! {contracts :contracts
       route :route :as app}]
  (r/with-let [default-filtering-value {:shortcut :my-contracts}
               filtering-atom (local-storage (r/atom default-filtering-value)
                                             :contract-filters)
               change-shortcut #(swap! filtering-atom assoc :shortcut %)
               input-change (fn [key value]
                              (swap! filtering-atom assoc key value))
               clear-filters #(reset! filtering-atom default-filtering-value)]
    [Grid {:container true}
     [Grid {:item true
            :xs 4}
      [:h1 "CONTRACT SEARCH AND FILTERS"]
      [contract-search {:e! e!
                        :search-fields search-fields
                        :filtering-atom filtering-atom
                        :filter-options filter-options
                        :input-change input-change
                        :change-shortcut change-shortcut
                        :clear-filters clear-filters}]]
     [Grid {:item true
            :xs 8}
      [:h1 "CONTRACTS LIStING"]
      [query/debounce-query
       {:e! e!
        :query :contract/list-contracts
        :args {:payload @filtering-atom
               :refresh (:contract-refresh route)}
        :simple-view [contracts-list e!]}
       250]]]))
