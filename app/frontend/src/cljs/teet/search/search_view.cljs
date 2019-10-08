(ns teet.search.search-view
  "Quick search of projects and project groups"
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [Paper CircularProgress IconButton InputLabel Input
                                         List ListItem ListItemIcon ListItemText InputAdornment FormControl TextField]]
            [teet.ui.icons :as icons]
            [teet.ui.common :as common]
            [teet.ui.hotkeys :as hotkeys]
            [teet.localization :refer [tr]]
            [teet.common.common-controller]
            [teet.search.search-controller :as search-controller]
            [teet.search.search-interface :as search-interface]
            [teet.ui.events :as events]))

(def sw "200px")

(defn quick-search [e! quick-search]
  (let [show-results? (r/atom false)]
    (common/component
      (hotkeys/hotkey "?" #(.focus (.getElementById js/document "quick-search")))
      (hotkeys/hotkey "Escape" #(reset! show-results? false))
      (events/click-outside #(reset! show-results? false))
      (fn [e! quick-search]
        [:div {:style {:position :relative}}
         [TextField
          {:id          "quick-search"
           :style       {:width sw}
           :variant     :outlined
           :value       (:term quick-search)
           :label       (tr [:search :quick-search])
           :on-change   #(do
                           (reset! show-results? true)
                           (e! (search-controller/->UpdateQuickSearchTerm (-> % .-target .-value))))
           ;:placeholder (tr [:search :quick-search])
           :on-focus    #(reset! show-results? true)
           :InputProps {:end-adornment
                         (r/as-element
                           [InputAdornment {:position :end}
                            [IconButton {:color :secondary
                                         :edge :end}
                             [icons/action-search]]])}}]

         #_[TextField {:id          "quick-search"
                       :style       {:width "300px"}
                       :label       (tr [:search :quick-search])
                       :value       (or (:term quick-search) "")
                       :on-change   #(do
                                       (reset! show-results? true)
                                       (e! (search-controller/->UpdateQuickSearchTerm (-> % .-target .-value))))
                       :placeholder (tr [:search :quick-search])
                       :on-focus    #(reset! show-results? true)}]
         (when (and false                                         ;@show-results? Never show the results as the feature is not completed
                    (contains? quick-search :results))
           [:div {:style {:position "absolute"
                          :width    sw
                          :z-index  99}}
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
                                                :href      href}))
                                  (when icon
                                    [ListItemIcon icon])
                                  [ListItemText text]]))
                             results)]
               [CircularProgress])]])]))))
