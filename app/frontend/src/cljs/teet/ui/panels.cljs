(ns teet.ui.panels
  "Different panels for showing content."
  (:require [herb.core :refer [<class]]
            [reagent.core :as r]
            [teet.ui.material-ui :refer [Card CardHeader CardContent
                                         Collapse IconButton Divider
                                         Modal Fade Dialog DialogTitle
                                         DialogContent]]
            [teet.ui.icons :as icons]
            [teet.ui.typography :as typography]
            [teet.theme.theme-colors :as theme-colors]))

(defn collapsible-panel
  "Panel that shows content that can be opened/closed with a button.
  In the initial closed state only the title is shown and an arrow icon button to open.
  Additional :action can element can be provided to add another action to the panel header."
  [{:keys [title action] :as opts} content]
  (r/with-let [open-atom (or (:open-atom opts) (r/atom false))]
    (let [open-atom (or (:open-atom opts) open-atom)
          open? @open-atom]
      [Card
       [CardHeader {:title (if (vector? title)
                             (r/as-element title)
                             title)
                    :action (r/as-element
                              [:div {:style {:display "inline-block"}}
                               action
                               #_[IconButton {:color    "primary"
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

(defn modal-style
  []
  {:display :flex
   :align-items :center
   :justify-content :center})

(defn modal-container-style
  []
  {:display :flex
   :width "90%"
   :max-width "900px"
   :background-color :white
   :border-radius "3px"
   :height "50vh"})

(defn modal-left-panel-container
  []
  {:background-color theme-colors/gray
   :width            "300px"
   :padding          "1rem"})

(defn modal-right-panel-container
  []
  {:flex    1
   :padding "1rem"})

(defn right-panel-heading-style
  []
  {:margin-bottom   "2rem"
   :display         :flex
   :justify-content :space-between
   :align-items     :center})

(defn modal+
  "Advanced modal container"
  [{:keys [title on-close open-atom left-panel right-panel] :as _opts}]
  (let [open-atom (or open-atom (r/atom true))
        close-fn #(do
                    (reset! open-atom false)
                    (when on-close
                      (on-close)))]
    [:<>
     [Modal {:open                   @open-atom
             :class                  (<class modal-style)
             :close-after-transition true
             :on-close               close-fn}
      [:div {:class (<class modal-container-style)}
       [Fade {:in @open-atom}
        [:<>
         [:div {:class (<class modal-left-panel-container)}
          left-panel]
         [:div {:class (<class modal-right-panel-container)}
          [:div {:class (<class right-panel-heading-style)}
           [typography/Heading2 title]
           [IconButton {:aria-label     "close"
                        :color          :primary
                        :disable-ripple true
                        :on-click       close-fn
                        :size           :small}
            [icons/navigation-close]]]
          right-panel]]]]]]))


(defn modal
  "Simple modal container"
  [{:keys [title on-close open-atom actions] :as _opts} content]
  (let [open-atom (or open-atom (r/atom true))      ;;creates new atoms unnecessarily
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
      content]
     (when actions
       actions)]))
