(ns teet.ui.container
  (:require [herb.core :refer [<class]]
            [teet.theme.container-theme :as container-theme]
            [teet.ui.icons :as icons]
            [teet.ui.material-ui :refer [Collapse IconButton]]
            [teet.ui.typography :as typography]))

(defn collapsible-container-heading
  "The heading of collapsible container that shows open/closed state
  and controls the state."
  [{:keys [on-toggle open? side-component size
           container-class disabled? div-attrs]
    :or {on-toggle identity
         size :medium
         container-class (<class container-theme/container-control)}}
   heading]
  [:div  (merge {:class container-class} div-attrs)
   [:span (when disabled?
            {:style {:visibility :hidden}})
    [IconButton
     {:size :small
      :color :primary
      :on-click on-toggle
      :class (<class container-theme/collapse-button)}

     (if open?
       [icons/hardware-keyboard-arrow-down {:color :primary}]
       [icons/hardware-keyboard-arrow-right {:color :primary}])]]

   (if (= size :small)
     [typography/Text2Bold heading]
     [:h3 {:style {:margin 0 :flex 1}} heading])
   (when side-component
     side-component)])

(defn collapsible-container
  [{:keys [open? container-attrs mount-on-enter collapsible-class class] :as opts
    :or {mount-on-enter false}} heading contents]
  [:div (merge {:class [class (<class container-theme/container)]}
          container-attrs)
   [collapsible-container-heading opts heading]
   [Collapse {:class collapsible-class
              :in (boolean open?)
              :mount-on-enter mount-on-enter}
    contents]])
