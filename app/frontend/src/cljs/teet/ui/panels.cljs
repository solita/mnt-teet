(ns teet.ui.panels
  "Different panels for showing content."
  (:require [herb.core :refer [<class]]
            [reagent.core :as r]
            [teet.common.common-styles :as common-styles]
            [teet.ui.material-ui :refer [Card CardHeader CardContent IconButton
                                         Divider Dialog DialogTitle DialogContent]]
            [teet.ui.icons :as icons]
            [teet.ui.typography :as typography]
            [teet.ui.util :refer [mapc]]
            [teet.theme.theme-colors :as theme-colors]))

(defn panel-with-action
  "Panel with an action element.
  Additional :action element can be provided to add an action to the panel header."
  [{:keys [title action] :as opts} content]
  [Card
   [CardHeader {:title (if (vector? title)
                         (r/as-element title)
                         title)
                :action (r/as-element
                          [:div {:style {:display "inline-block"}}
                           action])}]
   [CardContent
    [Divider]
    content]])

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

(defn title-and-subtitle-style
  []
  {:display :flex
   :flex-direction :row
   :justify-content :flex-start
   :align-items :flex-end})

(defn dialog-heading-style
  []
  {:margin-bottom 0
   :margin-top "1rem"
   :margin-right "1rem"})

(defn modal-style
  []
  {:display :flex
   :align-items :center
   :justify-content :center})

(defn modal-container-style
  []
  {:display :flex
   :background-color :white
   :border-radius "3px"
   :min-height "60vh"})

(defn modal-left-panel-container
  []
  {:background-color theme-colors/gray
   :width            "300px"
   :padding          "1rem"})

(defn modal-right-panel-container
  []
  {:flex    1
   :overflow :hidden
   :padding "1rem"
   :display :flex
   :flex-direction :column})

(defn right-panel-content-container
  []
  {:overflow-y :auto
   :flex 1})

(defn right-panel-heading-style
  []
  {:margin-bottom   "2rem"
   :display         :flex
   :justify-content :space-between
   :align-items     :center})

(defn- dialog-floating-close-button-style []
  {:display :inline-block
   :position :absolute
   :right "0px"})

(defn modal+
  "Advanced modal container"
  [{:keys [title on-close open-atom left-panel right-panel max-width] :as _opts
    :or {max-width :md}}]
  (let [close-fn #(do
                    (reset! open-atom false)
                    (when on-close
                      (on-close)))]
    [Dialog {:open @open-atom
             :close-after-transition true
             :max-width max-width
             :on-close close-fn
             :full-width true}
     [:div {:class (<class modal-container-style)}
      [:<>
       (when left-panel
         [:div {:class (<class modal-left-panel-container)}
          left-panel])
       [:div {:class (<class modal-right-panel-container)}
        [:div {:class (<class right-panel-heading-style)}
         [typography/Heading2 title]
         [IconButton {:aria-label "close"
                      :color :primary
                      :disable-ripple true
                      :on-click close-fn
                      :size :small}
          [icons/navigation-close]]]
        [:div {:class (<class right-panel-content-container)}
         right-panel]]]]]))

(defn modal
  "Default modal container"
  [{:keys [title subtitle on-close open-atom actions disable-content-wrapper? max-width data-cy]
    :or {max-width "sm"}
    :as _opts} content]
  (r/with-let [open-atom (or open-atom (r/atom true))       ;;creates new atoms unnecessarily
               close-fn #(do
                           (reset! open-atom false)
                           (when on-close
                             (on-close)))
               close-button [IconButton {:aria-label "close"
                                         :color :primary
                                         :disable-ripple true
                                         :on-click close-fn
                                         :size :small}
                             [icons/navigation-close]]]
    [Dialog {:max-width max-width
             :full-width true
             :open (boolean @open-atom)
             :on-close close-fn
             :data-cy data-cy}
     (if title
       ;; Title specified, show title and close button
       [DialogTitle {:disable-typography true
                     :class              (<class dialog-title-style)}
        [:span
         {:class (<class title-and-subtitle-style)}
         [typography/Heading1
          {:class (<class dialog-heading-style)}
          title]
         (when subtitle
           [typography/GrayText
            {:class (<class (fn [] common-styles/h4-desktop))}
            subtitle])]

        close-button]

       ;; No title specified, just show floating close button
       [:div {:class (<class dialog-floating-close-button-style)}
         close-button])
     [(if disable-content-wrapper?
        :<>
        DialogContent)
      content]
     (when actions
       actions)]))

(defn- side-by-side-container-style
  []
  {:display :flex
   :flex-direction :row})

(defn- side-by-side-column-style
  [flex-num]
  ^{:pseudo {:first-child {:border-left 0
                           :padding-right "1rem"}
             :last-child {:border-right 0
                          :padding-left "1rem"}}}
  {:flex (str flex-num)
   :border-color theme-colors/gray-lighter
   :border-style :solid
   :border-width "0 2px 0 0"
   :flex-grow 0
   :flex-shrink 0
   :flex-direction :column
   :display :flex
   :padding "0.5rem 0.25rem"
   :justify-content :flex-start})

(defn side-by-side
  "Show components side by side. With border in between.
  Takes in components flex number based on which the components automatically assign space
  eg. both have flex 1 they both get 50% of given space"
  [& percentages-and-components]
  [:div {:class (<class side-by-side-container-style)}
   (mapc (fn [[flex-num component]]
           [:div {:class (<class side-by-side-column-style flex-num)}
            component])
         (partition 2 percentages-and-components))])

(defn button-with-modal [{:keys [modal-title button-component modal-component modal-options open-atom
                                 on-open on-close]}]
  (r/with-let [open? (or open-atom (r/atom false))
               open! #(do (reset! open? true)
                          (when on-open
                            (on-open)))
               close! #(do (reset! open? false)
                           (when on-close
                             (on-close))
                           nil)]
    [:<>
     (assoc-in button-component [1 :on-click] open!)
     [modal (merge modal-options
                   {:open-atom open?
                    :on-close close!
                    :title modal-title})
      (conj modal-component close!)]]))
