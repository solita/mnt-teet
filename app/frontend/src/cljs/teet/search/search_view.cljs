(ns teet.search.search-view
  "Quick search of projects and project groups"
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [Paper CircularProgress IconButton
                                         List ListItem ListItemIcon ListItemText InputAdornment]]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.icons :as icons]
            [teet.ui.common :as common]
            [teet.ui.hotkeys :as hotkeys]
            [teet.localization :refer [tr]]
            [teet.common.common-controller]
            [teet.search.search-controller :as search-controller]
            [teet.search.search-interface :as search-interface]
            [teet.ui.events :as events]
            [teet.ui.typography :as typography]
            [herb.core :as herb :refer [<class]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.common.common-styles :as common-styles]))

(def sw "100%")

(defn result-container
  []
  ^{:combinators
    {[:> :ul] {:padding "0"}}}
  {:overflow-y "scroll"
   :max-height "80vh"
   :border-radius "0px"
   :border-top :none})

(defn result-container-item
  []
  ^{:pseudo {:last-child {:border-bottom :none}}}
  {:padding-top "0"
   :padding-right "0"
   :padding-bottom "0"
   :display :flex
   :align-items :center
   :height "3.125rem"
   :border :none
   :border-bottom (str "1px solid " theme-colors/border-dark)})

(defn circular-progress-style
  []
  {:height "2rem"
   :width "2rem"
   :margin-top "1rem"
   :margin-bottom "1rem"
   :margin-left :auto
   :margin-right :auto
   :display :block})

(defn quick-search [_e! _quick-search input-class input-style]
  (let [show-results? (r/atom false)]
    (common/component
      (hotkeys/hotkey "?" #(.focus (.getElementById js/document "quick-search")))
      (hotkeys/hotkey "Escape" #(reset! show-results? false))
      (events/click-outside #(reset! show-results? false))
      (fn [e! quick-search]
        [:<>
         [TextField
          {:id            "quick-search"
           :style         {:width sw}
           :type          :search
           :variant       :outlined
           :input-class   input-class
           :input-style   input-style
           :value         (:term quick-search)
           :placeholder   (tr [:search :quick-search])
           :on-change     #(let [term (-> % .-target .-value)]
                             (when (>= (count term)
                                       search-controller/min-search-term-length)
                               (reset! show-results? true))
                             (e! (search-controller/->UpdateQuickSearchTerm term)))
           :on-focus      #(reset! show-results? true)
           :auto-complete "off"
           :InputProps    {:start-adornment
                           (r/as-element
                             [InputAdornment {:position :end}
                              [IconButton {:color :primary
                                           :edge  :start}
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
                                 :class (herb/join
                                          (<class common-styles/list-item-link)
                                          (<class result-container-item))}
                                (when href
                                  {:component "a"
                                   :href      href}))
                      (when icon
                        [ListItemIcon icon])
                      [ListItemText
                       {:disable-typography true
                        :primary text}]]))
                 results)]
               [CircularProgress { :size "20" :class (<class
                                                      circular-progress-style)
                                  }])]])
         ]))))
