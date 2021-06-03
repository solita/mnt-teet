(ns teet.asset.materials-and-products-view
  (:require [reagent.core :as r]
            [teet.asset.asset-model :as asset-model]
            [teet.asset.asset-type-library :as asset-type-library]
            [teet.asset.asset-ui :as asset-ui]
            [teet.asset.cost-items-controller :as cost-items-controller]
            [teet.localization :refer [tr]]
            [teet.ui.table :as table]
            [teet.ui.typography :as typography]
            [teet.ui.url :as url]))

(defn- materials-and-products-page*
  [e! {query :query atl :asset-type-library :as app}
   {materials-and-products :materials-and-products
    version :version
    :as state}]
  (r/with-let [listing-state (table/listing-table-state)]
    (let [locked? (asset-model/locked? version)
          listing-opts {:columns asset-model/materials-and-products-table-columns
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
        [:div {:style {:max-width "25vw"}}
         [typography/Heading1 "Materials"]]
        [table/listing-table-container
         [table/listing-header (assoc listing-opts :state listing-state)]
         (str materials-and-products)]]])))

(defn materials-and-products-page [e! app state]
  [asset-ui/wrap-atl-loader materials-and-products-page* e! app state])
