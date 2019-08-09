(ns teet.ui.panels
  "Different panels for showing content."
  (:require [reagent.core :as r]
            [teet.ui.material-ui :refer [Card CardHeader CardActionArea CardActions CardContent
                                         Collapse
                                         IconButton Button Divider]]
            [teet.ui.icons :as icons]))

(defn collapsible-panel
  "Panel that shows content that can be opened/closed with a button.
  In the initial closed state only the title is shown and an arrow icon button to open.
  Additional :action can element can be provided to add another action to the panel header."
  [{:keys [title] :as opts} content]
  (r/with-let [open-atom (or (:open-atom opts) (r/atom false))]
    (let [open? @open-atom]
      [Card
       [CardHeader {:title title
                    :action (r/as-element
                             [IconButton {:color "primary"
                                          :on-click #(swap! open-atom not)}
                              (if open?
                                [icons/navigation-expand-less]
                                [icons/navigation-expand-more])])}]
       [Collapse {:in open? :unmountOnExit true :timeout "auto"}
        [CardContent
         [Divider]
         content]]])))

(defn panel
  "Simple content panel with title and content"
  [{:keys [title]} content]
  [Card
   [CardHeader {:title title}]
   [CardContent content]])

(defn main-content-panel [{:keys [title]} content]
  [Card
   [CardHeader {:title title}]
   [CardContent content]])
