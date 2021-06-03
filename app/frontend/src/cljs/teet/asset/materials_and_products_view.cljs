(ns teet.asset.materials-and-products-view
  (:require [reagent.core :as r]
            [teet.asset.asset-model :as asset-model]
            [teet.asset.asset-type-library :as asset-type-library]
            [teet.asset.asset-ui :as asset-ui]
            [teet.asset.cost-items-controller :as cost-items-controller]
            [teet.localization :as localization :refer [tr]]
            [teet.ui.table :as table]
            [teet.ui.typography :as typography]
            [teet.ui.url :as url]))

(def materials-and-products-table-columns
  [:material :common/status :parameter :component :material-approval-status])

(defn- get-column [atl material column]
  (case column
    :material (:material/type material)
    :common/status (:common/status material)
    :parameter (-> material (dissoc :component/_materials :material/type))
    :component (->> material :component/_materials (map (comp :db/ident :component/ctype)))
    :material-approval-status "TODO"))

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

(defn- format-components [atl components]
  (->> components
       (group-by identity)
       (map (fn [[ctype components]]
              [:div (str (->> ctype (asset-type-library/item-by-ident atl)
                              asset-ui/label)
                         (let [num-components (count components)]
                           (if (> num-components 1)
                             (str " (" num-components ")")
                             "")))]))
       (into [:<>])))

(defn- format-material-table-column [{:keys [e! atl locked?]} column value row]
  (case column
    :material (->> value :db/ident (asset-type-library/item-by-ident atl) asset-ui/label)
    :component (format-components atl value)
    :parameter (format-properties atl value)
    (str value)))


(defn- materials-and-products-page*
  [e! {query :query atl :asset-type-library :as app}
   {materials-and-products :materials-and-products
    version :version
    :as state}]
  (r/with-let [listing-state (table/listing-table-state)]
    (let [locked? (asset-model/locked? version)
          listing-opts {:columns asset-model/materials-and-products-table-columns
                        :get-column (r/partial get-column atl)
                        :column-label-fn #(if (= % :common/status)
                                            (asset-ui/label (asset-type-library/item-by-ident atl %))
                                            (tr [:asset :totals-table %]))
                        :format-column (r/partial format-material-table-column
                                                  {:e! e! :atl atl :locked? locked?})}

          filter-link-fn #(url/materials-and-products
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
         [table/listing-body (assoc listing-opts
                                    :rows materials-and-products
                                    )]]]])))

(defn materials-and-products-page [e! app state]
  [asset-ui/wrap-atl-loader materials-and-products-page* e! app state])
