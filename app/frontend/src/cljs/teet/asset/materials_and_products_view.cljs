(ns teet.asset.materials-and-products-view
  (:require [reagent.core :as r]
            [teet.asset.asset-model :as asset-model]
            [teet.asset.asset-type-library :as asset-type-library]
            [teet.asset.asset-ui :as asset-ui]
            [teet.asset.cost-items-controller :as cost-items-controller]
            [teet.localization :refer [tr]]
            [teet.ui.table :as table]
            [teet.ui.url :as url]))

(defn- materials-and-products-page*
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
                        :format-column str #_(r/partial asset-ui/format-cost-table-column
                                                  {:e! e! :atl atl :locked? locked?})}

          filter-link-fn #(url/cost-items-totals
                           {:project (get-in app [:params :project])
                            :url/query (merge query {:filter (str (:db/ident %))})})]
      [asset-ui/cost-items-page-structure
       {:e! e!
        :app  app
        :state state
        :hierarchy {:fclass-link-fn filter-link-fn
                    :fgroup-link-fn filter-link-fn
                    :list-features? false}}
       [:div.cost-items-totals
        #_[filter-breadcrumbs atl query filter-fg-or-fc]
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
         "TESTING"]]])))

(defn materials-and-products-page [e! app state]
  [asset-ui/wrap-atl-loader materials-and-products-page* e! app state])
