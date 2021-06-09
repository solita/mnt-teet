(ns teet.asset.cost-groups-view
  (:require [herb.core :refer [<class]]
            [reagent.core :as r]
            [teet.asset.asset-model :as asset-model]
            [teet.asset.asset-type-library :as asset-type-library]
            [teet.asset.asset-ui :as asset-ui]
            [teet.asset.cost-items-controller :as cost-items-controller]
            [teet.common.common-styles :as common-styles]
            [teet.localization :as localization :refer [tr]]
            [teet.ui.container :as container]
            [teet.ui.table :as table]
            [teet.ui.text-field :as text-field]
            [teet.ui.typography :as typography]
            [teet.ui.url :as url]))

(defn- cost-group-unit-price [e! value row]
  (r/with-let [price (r/atom value)
               change! #(reset! price (-> % .-target .-value))
               saving? (r/atom false)
               ;; Called after both success and error responses
               finish-saving! (fn []
                                ;; Reset price to nil, so current `value` is shown and reset to `price` on field focus
                                ;; Successful save does refresh, so the new properly formatted value is fetched from the backend
                                (reset! price nil)
                                (reset! saving? false))
               save! (fn [current-value row]
                       (when (not= current-value @price)
                         (reset! saving? true)
                         (e! (cost-items-controller/->SaveCostGroupPrice finish-saving! row @price))))
               save-on-enter! (fn [current-value row e]
                                (when (= "Enter" (.-key e))
                                  (save! current-value row)))]
    [:div {:class (<class common-styles/flex-row)
           :style {:justify-content :flex-end
                   ;; to have same padding as header for alignment
                   :padding-right "16px"}}
     [text-field/TextField {:input-style {:text-align :right
                                          :width "8rem"}
                            :value (or @price value)
                            :on-change change!
                            :on-key-down (r/partial save-on-enter! value row)
                            :on-focus #(reset! price value)
                            :on-blur (r/partial save! value row)
                            :disabled @saving?
                            :end-icon [text-field/euro-end-icon]}]]))


(defn- format-properties [atl properties]
  (into [:<>]
        (map (fn [[k v u]]
               [:div
                [typography/BoldGrayText k ": "]
                v
                (when u
                  (str "\u00a0" u))]))
        (asset-type-library/format-properties @localization/selected-language
                                              atl properties)))


(defn- format-cost-table-column [{:keys [e! atl locked?]} column value row]
  (case column
    :type (asset-ui/label (asset-type-library/item-by-ident atl value))
    :common/status (asset-ui/label (asset-type-library/item-by-ident
                           atl (:db/ident value)))
    :properties (format-properties atl row)
    :quantity (str value
                   (when-let [qu (:quantity-unit row)]
                     (str " " qu)))
    :cost-per-quantity-unit (if locked?
                              (asset-ui/format-euro value)
                              [cost-group-unit-price e! value row])
    :total-cost (asset-ui/format-euro value)
    (str value)))

(defn- table-section-header [e! query listing-opts closed-set {ident :db/ident :as header-type} subtotal]
  [table/listing-table-body-component listing-opts
   [container/collapsible-container-heading
    {:container-class [(<class common-styles/flex-row)
                       (when (= "fclass" (namespace ident))
                         (<class common-styles/indent-rem 1))]
     :open? (not (closed-set ident))
     :on-toggle (e! cost-items-controller/->ToggleOpenTotals ident)}
    [:<>
     [url/Link {:page :cost-items-totals
                :query (merge query {:filter (str ident)})}
      (asset-ui/label header-type)]
     [:div {:style {:float :right :font-weight 700
                    :font-size "80%"}} subtotal]]]])

(defn- cost-items-totals-page*
  [e! {query :query atl :asset-type-library :as app}
   {totals :cost-totals version :version
    closed-totals :closed-totals
    :or {closed-totals #{}}
    :as state}]
  (r/with-let [listing-state (table/listing-table-state)]
    (let [locked? (asset-model/locked? version)
          listing-opts {:columns asset-model/cost-totals-table-columns
                        :column-align asset-model/cost-totals-table-align
                        :column-label-fn #(if (= % :common/status)
                                            (asset-ui/label (asset-type-library/item-by-ident atl %))
                                            (tr [:asset :totals-table %]))
                        :format-column (r/partial format-cost-table-column
                                                  {:e! e! :atl atl :locked? locked?})}

          [filter-fg-or-fc filtered-cost-groups]
          (->> totals :cost-groups
               (cost-items-controller/filtered-cost-group-totals app atl))

          grouped-totals (->> filtered-cost-groups
                              (group-by (comp first :ui/group))
                              ;; sort by translated fgroup label
                              (sort-by (comp asset-ui/label first)))
          filter-link-fn #(url/cost-items-totals
                           {:project (get-in app [:params :project])
                            ::url/query (merge query {:filter (str (:db/ident %))})})]
      [asset-ui/cost-items-page-structure
       {:e! e!
        :app  app
        :state state
        :hierarchy {:fclass-link-fn filter-link-fn
                    :fgroup-link-fn filter-link-fn
                    :list-features? false}}
       [:div.cost-items-totals
        [asset-ui/filter-breadcrumbs {:atl atl
                                      :root-label (tr [:asset :totals-table :all-components])
                                      :query query
                                      :filter-kw filter-fg-or-fc
                                      :page :cost-items-totals}]
        [:div {:style {:max-width "25vw"}}
         [asset-ui/relevant-road-select
          {:e! e!
           :extra-opts ["all-cost-items" "no-road-reference"]
           :extra-opts-label {"all-cost-items" (tr [:asset :totals-table :all-cost-items])
                              "no-road-reference" (tr [:asset :totals-table :no-road-reference])}
           :value (get-in app [:query :road])

           :on-change (e! cost-items-controller/->SetTotalsRoadFilter)}]]
        [:div {:style {:float :right}}
         [:b
          (tr [:asset :totals-table :project-total]
              {:total (:total-cost totals)})]]

        [table/listing-table-container
         [table/listing-header (assoc listing-opts :state listing-state)]
         (doall
          (for [[fg fgroup-rows] grouped-totals
                :let [ident (:db/ident fg)
                      open? (not (closed-totals ident))]]
            ^{:key (str ident)}
            [:<>
             [table-section-header e! query listing-opts closed-totals fg
              (get-in totals [:fclass-and-fgroup-totals (:db/ident fg)])]
             (when open?
               [:<>
                (doall
                 (for [[fc fclass-rows] (group-by (comp second :ui/group)
                                                  fgroup-rows)
                       :let [ident (:db/ident fc)
                             open? (not (closed-totals ident))]]
                   ^{:key (str ident)}
                   [:<>
                    [table-section-header e! query listing-opts closed-totals fc
                     (get-in totals [:fclass-and-fgroup-totals (:db/ident fc)])]
                    (when open?
                      [table/listing-body (assoc listing-opts :rows fclass-rows)])]))])]))]]])))

(defn cost-items-totals-page [e! app state]
  [asset-ui/wrap-atl-loader cost-items-totals-page* e! app state])
