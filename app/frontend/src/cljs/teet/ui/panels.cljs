(ns teet.ui.panels
  "Different panels for showing content."
  (:require [herb.core :refer [<class]]
            [reagent.core :as r]
            [teet.ui.material-ui :refer [Card CardHeader CardContent
                                         Collapse IconButton Divider
                                         Dialog DialogTitle DialogContent]]
            [teet.ui.icons :as icons]
            [teet.ui.typography :as typography]))

(defn collapsible-panel
  "Panel that shows content that can be opened/closed with a button.
  In the initial closed state only the title is shown and an arrow icon button to open.
  Additional :action can element can be provided to add another action to the panel header."
  [{:keys [title action] :as opts} content]
  (r/with-let [open-atom (or (:open-atom opts) (r/atom false))]
    (let [open-atom (or (:open-atom opts) open-atom)
          open? @open-atom]
      [Card
       [CardHeader {:title  title
                    :action (r/as-element
                              [:div {:style {:display "inline-block"}}
                               action
                               [IconButton {:color    "primary"
                                            :on-click #(swap! open-atom not)}
                                (if open?
                                  [icons/navigation-expand-less]
                                  [icons/navigation-expand-more])]])}]
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

(defn dialog-title-style
  []
  {:display         :flex
   :justify-content :space-between
   :align-items     :flex-start})

(defn dialog-heading-style
  []
  {:margin-bottom 0
   :margin-top    "1rem"})

(defn modal
  "Simple modal container"
  [{:keys [title on-close] :as opts} content]
  (let [open-atom (or (:open-atom opts) (r/atom true))      ;;creates new atoms unnecessarily
        close-fn #(do
                    (reset! open-atom false)
                    (when on-close
                      (on-close)))]
    [Dialog {:full-width true
             :max-width  "sm"
             :open       @open-atom
             :on-close   close-fn}
     [DialogTitle {:disable-typography true
                   :class              (<class dialog-title-style)}
      [typography/Heading1
       {:class (<class dialog-heading-style)}
       title]
      [IconButton {:aria-label     "close"
                   :color          :primary
                   :disable-ripple true
                   :on-click       close-fn
                   :size           :small}
       [icons/navigation-close]]]
     [DialogContent
      content]]))
