(ns teet.ui.common
  "Common UI utilities"
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.ui.icons :as icons]
            [garden.color :refer [darken lighten as-hex]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.material-ui :refer [ButtonBase Link Chip Collapse]]
            [teet.ui.typography :refer [Text SmallText] :as typography]
            [teet.common.common-styles :as common-styles]
            [teet.ui.buttons :as buttons]
            [teet.ui.format :as format]))

(def lifecycle-methods
  "Supported lifecycle methods in mixins."
  [:component-will-receive-props
   :component-did-mount
   :component-will-unmount
   :should-component-update])

(defn component [& mixins-and-render-fn]
  (let [{mixins true
         render-fn false} (group-by map? mixins-and-render-fn)
        run-lifecycle-fns (fn [method args]
                            (doseq [lifecycle-fn (keep method mixins)]
                              (apply lifecycle-fn args)))]
    (assert (= 1 (count render-fn))
            "Expected exactly one render fn to be provided")
    (r/create-class
     (merge
      {:component-will-receive-props
       (fn [this new-argv]
         (run-lifecycle-fns :component-will-receive-props [this new-argv]))

       :component-did-mount
       (fn [this]
         (run-lifecycle-fns :component-did-mount [this]))

       :component-will-unmount
       (fn [this]
         (run-lifecycle-fns :component-will-unmount [this]))

       :reagent-render
       (fn [& args]
         (try
           (apply (first render-fn) args)
           (catch :default e
             (.error js/console "RENDER FN THREW EXCEPTION" e))))}

      (let [checks (keep :should-component-update mixins)]
        (when (seq checks)
          ;; We have functions to check update condition, add lifecycle
          {:should-component-update
           (fn [this old-argv new-argv]
             (if (some #(% this old-argv new-argv) checks)
               true
               false))}))))))

(defn should-component-update?
  "Helper function to create a :should-component-update lifecycle function.
  Uses get-in to fetch the given accessor paths from both the old and the new
  arguments and returns true if any path's values differ.

  For example if the component has arguments: [e! my-thing foo]
  the path to access key :name from my-thing is: [1 :name]."
  [& accessor-paths]
  (fn [_ old-argv new-argv]
    (let [old-argv  (subvec old-argv 1)
          new-argv (subvec new-argv 1)]
      (boolean
       (some #(not= (get-in old-argv %) (get-in new-argv %))
             accessor-paths)))))

;;
;; Labeled data
;;
(defn- container-style
  []
  {:border 0
   :margin 0
   :display :inline-flex
   :padding 0
   :position :relative
   :min-width 0
   :flex-direction :column
   :vertical-align :top})

(defn- label-style
  []
  {:top 0
   :left 0
   :position :absolute
   :padding 0
   :display :block
   :color theme-colors/gray

   :font-family "Roboto"
   :font-weight 300
   :font-size "14px"
   :line-height "14px"
   :transform "translate(0, 2.5px)"
   :transform-origin "top left"

   :white-space :nowrap})

(defn- data-style
  []
  {:white-space :nowrap
   :text-overflow :ellipsis
   :margin-top "16px"
   :padding "6px 24px 0 0"
   :line-height "19px"
   :position :relative
   :color theme-colors/blue})

(defn- required-astrix-style
  []
  {:color theme-colors/error})

(defn required-astrix
  []
  [:span {:class (<class required-astrix-style)}
   " *"])

(defn labeled-data [{:keys [label data class]}]
  [:div {:class class}
   [:div {:class (herb/join (<class container-style)
                            class)}
    [Text {:component :span
           :classes {:root (<class label-style)}}

     (str label ":")]
    [Text {:component :span
           :classes {:root (<class data-style)}}
     data]]])

(defn list-button-style
  []
  ^{:pseudo {:hover {:background-color theme-colors/gray-lightest}
             :focus {:box-shadow (str "0 0 0 2px " theme-colors/blue-light)}}}
  {:padding "0.75rem 0.25rem"
   :display :flex
   :justify-content :flex-start
   :transition "background-color 0.2s ease-in-out"
   :border-bottom (str "1px solid " theme-colors/gray-lighter)})

(defn list-button-link
  "Listable link with bottom border and big clickable area"
  [{:keys [link label sub-label icon end-text]}]
  [ButtonBase {:class (<class list-button-style)
               :element "a"
               :href link}
   [icon {:style {:align-self :flex-start
                  :margin-right "0.5rem"}}]
   [:div {:style {:margin-right :auto}}
    [:span label]
    (when sub-label
      [SmallText sub-label])]
   (when end-text
     [SmallText end-text])])

(defn heading-buttons-style
  []
  {:display :flex
   :flex-wrap :nowrap})

(defn header-with-actions [header-text & actions]
  [:div {:class (<class common-styles/header-with-actions)}
   [typography/Heading1 header-text]
   (into [:div {:class (<class heading-buttons-style)}] actions)])


(defn feature-and-action-style
  []
  ^{:pseudo {:hover {:background-color theme-colors/gray-dark}}}
  {:background-color theme-colors/gray
   :color theme-colors/white
   :transition "background-color 200ms ease-in-out"
   :padding "0.5rem"
   :padding-left "1rem"
   :margin-bottom "0.5rem"
   :display :flex
   :justify-content :space-between
   :align-items :center})

(defn feature-and-action
  [{:keys [label]}
   {:keys [button-label action on-mouse-enter on-mouse-leave]}]
  [:div {:on-mouse-enter on-mouse-enter
         :on-mouse-leave on-mouse-leave
         :class (<class feature-and-action-style)}
   [:span
    {:style {:margin-right "0.5rem"}}
    label]
   [buttons/rect-white {:on-click action}
    button-label]])

(defn- thk-link-style
  []
  ^{:pseudo {:hover {:text-decoration :underline}}}
  {:font-size "24px"
   :text-decoration :none
   :display :flex
   :align-items :center})

(defn- thk-link-icon-style
  []
  {:margin-left "0.5rem"
   :font-size "20px"
   :font-weight :bold})

(defn thk-link
  [opts label]
  [Link (merge {:class (<class thk-link-style)}
               opts)
   label
   [icons/action-open-in-new {:class (<class thk-link-icon-style)}]])

(def ^{:const true
       :doc "Minimum browser window width that is considered wide for layout purposes."}
  wide-display-cutoff-width 2200)

(defonce window-width
  (let [width (r/atom js/document.body.clientWidth)]
    (set! (.-onresize js/window)
          (fn [_]
            (reset! width js/document.body.clientWidth)))
    width))


(defn wide-display? []
  (>= @window-width wide-display-cutoff-width))


(defn estate-group-style
  []
  ^{:pseudo {:first-of-type {:border-top "1px solid white"}}}
  {:border-left "1px solid white"})

(defn hierarchical-heading-container
  [bg-color font-color show-polygon]
  (with-meta
    {:background-color bg-color
     :position :relative
     :color font-color}
    (when show-polygon
      {:pseudo {:before {:content "''"
                         :width 0
                         :height 0
                         :border-bottom "15px solid transparent"
                         :border-left (str "15px solid " bg-color)
                         :position :absolute
                         :bottom "-15px"
                         :transform "rotate(90deg)"
                         :left 0}}})))

(defn hierarchical-child-container
  []
  {:display :flex
   :flex-direction :column
   :margin-left "15px"})

(defn hierarchical-container
  [{:keys [heading-color heading-text-color heading-content children show-polygon?]
    :or {heading-color theme-colors/gray-lighter
         heading-text-color :inherit
         show-polygon? true}}]
  [:<>
   [:div {:class (<class hierarchical-heading-container heading-color heading-text-color show-polygon?)}
    heading-content]
   [:div {:class (<class hierarchical-child-container)}
    (doall
      (map
        (fn [child]
          (with-meta
            [:div {:class (<class estate-group-style)}
             child]
            (meta child)))
        children))]])

(defn hierarchical-container-button-style
  [bg-color]
  ^{:pseudo {:hover {:background-color (darken bg-color 10)}}}
  {:width "100%"
   :justify-content :space-between
   :flex-direction :column
   :align-items :flex-start
   :cursor :pointer
   :border-bottom "3px solid white"
   :background-color bg-color
   :padding "1rem"
   :transition "0.2s ease-in-out background-color"})

(defn hierarchical-container-style
  [bg-color]
  {:justify-content :space-between
   :flex-direction :column
   :align-items :flex-start
   :border-bottom "3px solid white"
   :background-color bg-color
   :padding "1rem"})

(defn hierarchical-heading-container2
  [bg-color font-color show-polygon]
  (with-meta
    {:background-color bg-color
     :position :relative
     :color font-color
     :border-bottom "1px solid white"}
    (when show-polygon
      {:pseudo {:before {:content "''"
                         :width 0
                         :height 0
                         :border-bottom "15px solid transparent"
                         :border-left (str "15px solid " bg-color)
                         :position :absolute
                         :bottom "-15px"
                         :transform "rotate(90deg)"
                         :left 0}}})))

(defn hierarchical-container2
  ([param]
   [hierarchical-container2 param theme-colors/gray-light])
  ([{:keys [text-color content heading heading-button children after-children-component]
     :or {text-color :inherit}} bg-color]
   (r/with-let [open? (r/atom false)
                toggle-open! #(do
                                (.stopPropagation %)
                                (swap! open? not))]
     [:<>
      [:div {:class (<class hierarchical-heading-container2 bg-color text-color (and
                                                                                  content
                                                                                  (or children after-children-component)
                                                                                  @open?))}
       [:div                                                ;; This is a div because buttons shouldn't contain buttons even though this is bad practice as well
        {:disable-ripple true
         :class (<class hierarchical-container-button-style bg-color)
         :on-click toggle-open!}
        [:div {:style {:width "100%"
                       :display :flex
                       :justify-content :space-between
                       :align-items :center}}
         [:div {:style {:flex-grow 1
                        :text-align :start}}
          heading]
         (when (and heading-button @open?)
           [:div {:style {:flex-grow 0}
                  :on-click (fn [e]
                              (.stopPropagation e))}
            heading-button])]]
       (when content
         [Collapse {:in @open?
                    :mount-on-enter true}
          [:div {:style {:padding "1rem"}}
           content]])]

      (when (or children after-children-component)
        [Collapse {:in @open?
                   :mount-on-enter true}
         [:div {:class (<class hierarchical-child-container)}
          (doall
            (for [child children]
              (with-meta
                (if (vector? child)                         ;;Check if it's component and render that instaed
                  child
                  [hierarchical-container2 child (as-hex (lighten bg-color 15))])
                {:key (:key child)})))
          after-children-component]])])))

(defn- count-chip-style
  []
  {:margin-right "0.25rem"
   :cursor :inherit})

(defn count-chip
  [opts]
  [Chip (merge
          {:size :small
           :color :primary
           :class (<class count-chip-style)}
          opts)])

(defn comment-count-chip
  [{:comment/keys [counts]}]
  (let [{:comment/keys [old-comments new-comments]} counts
        new? (pos? (int new-comments))
        count (+ new-comments
                 old-comments)]
     [Chip {:size :small
           :color :primary
           :class (<class count-chip-style)
           :label (str count)
           :style {:background-color (if new?
                                       theme-colors/red
                                       theme-colors/primary)}}]))

(defn heading-and-grey-border-body
  [{:keys [heading body]}]
  [:div {:style {:margin-bottom "1.5rem"}}
   [:div {:style {:margin-bottom "0.25rem"}}
    heading]
   [:div {:style {:padding-left "0.5rem"
                  :border-left (str "solid 7px " theme-colors/gray-light)}}
    body]])

(def number-formatter (js/Intl.NumberFormat "et-EE" #js {:style "currency"
                                                         :currency "EUR"}))

(defn readable-currency
  [s]
  (.format number-formatter s))

(defn info-row-item-style
  []
  ^{:pseudo {:last-child {:margin-right 0}}}
  {:margin-right "1rem"
   :margin-bottom "1rem"})

(defn basic-information-row
  "[[data-title data-value]...]"
  [data]
  [:div {:class (<class common-styles/flex-row-wrap)}
   (doall
     (for [[label data] data]
       ^{:key label}
       [:div {:class (<class info-row-item-style)}
        [typography/SectionHeading label]
        [:p data]]))])

(defn column-with-space-between [space-between & children]
  (let [cls (<class common-styles/padding-bottom space-between)]
    (into [:<>]
          (map (fn [child]
                 [:div {:class cls}
                  child]))
          children)))
