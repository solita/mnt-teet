(ns teet.asset.assets-view
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [Grid Paper]]
            [teet.ui.query :as query]
            [teet.common.common-styles :as common-styles]
            [teet.ui.typography :as typography]
            [herb.core :refer [<class]]
            [teet.localization :refer [tr]]
            [teet.asset.asset-ui :as asset-ui]))

(defn- asset-filters [e! atl filters]
  [:div {:style {:min-width "300px"}}
   [:div {:style {:padding "1rem"}}
    [asset-ui/select-fgroup-and-fclass
     {:e! e!
      :value (@filters :fgroup-and-fclass)
      :on-change #(swap! filters assoc :fgroup-and-fclass %)
      :atl atl}]
    "SEARCH THIS: " (pr-str @filters)]])

(defn- assets-list [e! atl assets]
  [:div (pr-str assets)])

(defn assets-page [e! app]
  (r/with-let [filters (r/atom {})]
    [:div {:style {:padding "1.875rem 1.5rem"
                   :display :flex
                   :height "calc(100vh - 220px)"
                   :flex-direction :column
                   :flex 1}}
     [Paper {:style {:display :flex :flex 1}}
      [:div {:style {:width "30vw"}}
       [asset-filters e! (:asset-type-library app) filters]]
      [:div {:style {:flex 1 :overflow-y :scroll
                     :max-height "calc(100vh - 170px)"
                     :padding "1rem"}}
       [:div {:class (<class common-styles/flex-row-space-between)}
        [typography/Heading1 (tr [:asset :manager :link])]]
       [query/query {:e! e!
                     :query :assets/search
                     :args {:fclass #{(second (@filters :fgroup-and-fclass))}}
                     :simple-view [assets-list e! (:asset-type-library app)]}]
       [:div "here's the assets"]]]]))
