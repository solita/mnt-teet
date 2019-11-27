(ns teet.ui.container
  (:require [herb.core :refer [<class]]
            [teet.theme.container-theme :as container-theme]
            [teet.ui.icons :as icons]
            [teet.ui.material-ui :refer [Collapse IconButton]]))

(defn collapsible-container [{:keys [on-toggle open? side-component]
                          :or   {on-toggle identity}} heading contents]
  [:div {:class (<class container-theme/container)}
   [:div  {:class (<class container-theme/container-control)}
    [IconButton {:size :small
                 :color :primary
                 :on-click on-toggle
                 :class (<class container-theme/collapse-button)}
     (if open?
       [icons/hardware-keyboard-arrow-down {:color :primary}]
       [icons/hardware-keyboard-arrow-up {:color :primary}])]
    [:h3 {:style {:margin 0 :flex 1}}
     heading]
    (when side-component
      side-component)]
   [Collapse {:in (boolean open?)}
    contents]])
