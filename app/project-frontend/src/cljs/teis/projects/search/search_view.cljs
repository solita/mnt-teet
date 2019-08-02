(ns teis.projects.search.search-view
  "Quick search of projects and project groups"
  (:require [reagent.core :as r]
            [teis.ui.material-ui :refer [TextField Paper CircularProgress]]
            [teis.localization :refer [tr]]
            [teis.common.common-controller]
            [teis.projects.search.search-controller :as search-controller]
            [teis.projects.search.search-interface :as search-interface]
            [teis.ui.material-ui :refer [List ListItem ListItemIcon ListItemText]]))


(defn quick-search [e! {:keys [quick-search]}]
  [:<>
   [TextField {:style {:width "300px"}
               :label (tr [:search :quick-search])
               :value (or (:term quick-search) "")
               :on-change #(e! (search-controller/->UpdateQuickSearchTerm (-> % .-target .-value)))
               :placeholder (tr [:search :quick-search])}]
   (when (contains? quick-search :results)
     [:div {:style {:position "absolute"
                    :width "300px"
                    :z-index 99}}
      [Paper
       (if-let [results (:results quick-search)]
         [List {}
          (map-indexed (fn [i result]
                         (let [{:keys [icon text href]} (search-interface/format-search-result result)]
                           ^{:key i}
                           [ListItem (if href
                                       {:component "a"
                                        :href href}
                                       {})
                            (when icon
                              [ListItemIcon icon])
                            [ListItemText text]]))
                       results)]
         [CircularProgress])]])])
