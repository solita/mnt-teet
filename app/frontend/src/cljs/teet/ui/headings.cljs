(ns teet.ui.headings
  "Different page and section heading components"
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [Card CardHeader TextField]]))

(defn header [{:keys [title subtitle icon action]}]
  [Card
   [CardHeader (merge {:title title
                       :subtitle subtitle}
                      (when action
                        {:action (r/as-element action)}))
       ]])
