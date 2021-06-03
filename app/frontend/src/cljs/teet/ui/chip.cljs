(ns teet.ui.chip
  "A chip for input selected items"
  (:require [herb.core :refer [<class]]
            [teet.ui.icons :as icons]))



(defn- selected-item-chip-style []
  ^{:pseudo {:focus {:border-color "#40a9ff"
                     :background-color "#e6f7ff"}}
    :combinators {[:> :span] {:overflow "hidden"
                              :white-space "nowrap"
                              :text-overflow "ellipsis"}
                  [:> :.material-icons] {:font-size "12px"
                                         :cursor "pointer"
                                         :padding "4px"}}}
  {:display "flex"
   :align-items "center"
   :height "24px"
   :margin "2px"
   :line-height "22px"
   :background-color "#fafafa"
   :border "1px solid #e8e8e8"
   :border-radius "2px"
   :box-sizing "content-box"
   :padding "0 4px 0 10px"
   :outline "0"
   :overflow "hidden"})

(defn selected-item-chip [{:keys [on-remove]} content]
  [:div {:class (<class selected-item-chip-style)}
   [:span content]
   (when on-remove
     [icons/navigation-close
      {:on-click on-remove}])])
