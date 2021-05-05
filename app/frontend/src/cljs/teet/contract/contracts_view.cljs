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
            [teet.contract.contract-view :as contract-view]))

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

(defn search-inputs
  [e! filter-values input-change search-fields]
  [:div
   (into [:div]
         (mapv
           (fn [[field-name {field-type :type
                             field-options :field-options}]]
             [field-type (merge {:e! e!
                                 :label (tr [:contract :search field-name])
                                 :value (field-name filter-values)
                                 :start-icon icons/action-search
                                 :on-change #(input-change
                                               field-name
                                               (if (= field-type TextField)
                                                 (-> % .-target .-value)
                                                 %))}
                                field-options)])
           search-fields))])

(defn contract-search
  [{:keys [e! filter-options clear-filters search-fields
           filtering-atom input-change change-shortcut]}]
  [:div
   [buttons/link-button {:on-click clear-filters}
    "CLEAR FILTERS"]
   [search-shortcuts (:shortcut @filtering-atom) filter-options change-shortcut]
   [search-inputs e! @filtering-atom input-change search-fields]])

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
      [:div {:class (<class common-styles/margin-bottom 2)}
       [:h1 "CONTRACT SEARCH AND FILTERS"]
       [contract-search {:e! e!
                         :search-fields search-fields
                         :filtering-atom filtering-atom
                         :filter-options filter-options
                         :input-change input-change
                         :change-shortcut change-shortcut
                         :clear-filters clear-filters}]]]
     [:h1 "CONTRACTS LIStING"]
     [query/debounce-query
      {:e! e!
       :query :contracts/list-contracts
       :args {:search-params @filtering-atom
              :refresh (:contract-refresh route)}
       :simple-view [contracts-list e!]}
      250]]))
