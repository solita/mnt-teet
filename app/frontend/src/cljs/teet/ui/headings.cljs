(ns teet.ui.headings
  "Different page and section heading components"
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [AppBar CardHeader TextField Typography IconButton Drawer]]
            [teet.ui.icons :as icons]))

(defn header
  [{:keys [title subtitle button action]}]
  (let [open? (r/atom true)]
    (fn []
      [:nav
       [Drawer {:variant "permanent"
                :anchor "left"
                :open @open?
                :style {:width (if @open?
                                 "200px"
                                 "80px")
                        :flex-shrink 0
                        :white-space "nowrap"}}
        [:div {:style {:padding "1rem"
                       :display "flex"
                       :align-items "center"
                       :justify-content "space-between"}}
         (when @open?
           [Typography (merge {:variant "h6"})
            title])
         [IconButton {:color "primary"
                      :on-click (fn []
                                  (println "open?: " @open?)
                                  (swap! open? not))}
          (if @open?
            [icons/navigation-chevron-left]
            [icons/navigation-chevron-right])]]
        (when action
          (r/as-element action))]])))
