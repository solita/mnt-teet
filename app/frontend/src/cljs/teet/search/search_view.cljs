(ns teet.search.search-view
  "Quick search of projects and project groups"
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [Paper CircularProgress IconButton InputLabel Input
                                         List ListItem ListItemIcon ListItemText InputAdornment FormControl]]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.icons :as icons]
            [teet.ui.common :as common]
            [teet.ui.hotkeys :as hotkeys]
            [teet.localization :refer [tr]]
            [teet.common.common-controller]
            [teet.search.search-controller :as search-controller]
            [teet.search.search-interface :as search-interface]
            [teet.ui.events :as events]
            [herb.core :refer [<class]]
            [teet.common.common-styles :as common-styles]))

(def sw "100%")

(defn result-container []
  {:overflow-y "scroll"
   :max-height "80vh"})

(defn quick-search [_e! _quick-search]
  (let [show-results? (r/atom false)]
    (common/component
      (hotkeys/hotkey "?" #(.focus (.getElementById js/document "quick-search")))
      (hotkeys/hotkey "Escape" #(reset! show-results? false))
      (events/click-outside #(reset! show-results? false))
      (fn [e! quick-search]
        [:div {:style {:position :relative
                       :flex-grow 1
                       :flex-basis "400px"}}
         [TextField
          {:id          "quick-search"
           :style       {:width sw}
           :variant     :outlined
           :value       (:term quick-search)
           :placeholder (tr [:search :quick-search])
           :on-change   #(let [term (-> % .-target .-value)]
                           (when (>= (count term)
                                     search-controller/min-search-term-length)
                             (reset! show-results? true))
                           (e! (search-controller/->UpdateQuickSearchTerm term)))
           :on-focus    #(reset! show-results? true)
           :auto-complete "off"
           :InputProps {:start-adornment
                        (r/as-element
                         [InputAdornment {:position :end}
                          [IconButton {:color :primary
                                       :edge :start}
                           [icons/action-search]]])}}]
         (when (and @show-results?
                    (contains? quick-search :results))
           [:div {:style {:position "absolute"
                          :width    sw
                          :z-index  99}}
            [Paper {:class (<class result-container)}
             (if-let [results (:results quick-search)]
               [List {}
                (map-indexed
                 (fn [i result]
                   (let [{:keys [icon text href]} (search-interface/format-search-result result)]
                     ^{:key i}
                     [ListItem (merge
                                {:on-click #(reset! show-results? false)
                                 :class (<class common-styles/list-item-link)}
                                (when href
                                  {:component "a"
                                   :href      href}))
                      (when icon
                        [ListItemIcon icon])
                      [ListItemText text]]))
                 results)]
               [CircularProgress])]])]))))
