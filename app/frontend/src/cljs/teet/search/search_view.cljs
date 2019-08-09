(ns teet.search.search-view
  "Quick search of projects and project groups"
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [TextField Paper CircularProgress
                                         List ListItem ListItemIcon ListItemText]]
            [teet.ui.common :as common]
            [teet.ui.hotkeys :as hotkeys]
            [teet.localization :refer [tr]]
            [teet.common.common-controller]
            [teet.search.search-controller :as search-controller]
            [teet.search.search-interface :as search-interface]
            [teet.ui.events :as events]))


(defn quick-search [_ _]
  (let [show-results? (r/atom false)]
    (common/component
     (hotkeys/hotkey "?" #(.focus (.getElementById js/document "quick-search")))
     (events/click-outside #(reset! show-results? false))
     (fn [e! {:keys [quick-search]}]
       [:<>
        [TextField {:id "quick-search"
                    :style {:width "300px"}
                    :label (tr [:search :quick-search])
                    :value (or (:term quick-search) "")
                    :on-change #(do
                                  (reset! show-results? true)
                                  (e! (search-controller/->UpdateQuickSearchTerm (-> % .-target .-value))))
                    :placeholder (tr [:search :quick-search])
                    :on-focus #(reset! show-results? true)}]
        (when (and @show-results?
                   (contains? quick-search :results))
          [:div {:style {:position "absolute"
                         :width "300px"
                         :z-index 99}}
           [Paper
            (if-let [results (:results quick-search)]
              [List {}
               (map-indexed (fn [i result]
                              (let [{:keys [icon text href]} (search-interface/format-search-result result)]
                                ^{:key i}
                                [ListItem (merge
                                           {:on-click #(reset! show-results? false)}
                                           (when href
                                             {:component "a"
                                              :href href}))
                                 (when icon
                                   [ListItemIcon icon])
                                 [ListItemText text]]))
                            results)]
              [CircularProgress])]])]))))
