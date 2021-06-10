(ns teet.ui.common
  "Common UI utilities"
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.ui.icons :as icons]
            [garden.color :refer [darken lighten as-hex]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.material-ui :refer [ButtonBase Chip Collapse Popper Portal Grid
                                         Paper Menu MenuItem ListItemIcon ClickAwayListener]]
            [teet.ui.typography :refer [Text SmallGrayText] :as typography]
            [teet.common.common-styles :as common-styles]
            [teet.ui.buttons :as buttons]
            [re-svg-icons.feather-icons :as fi]
            [teet.ui.util :refer [mapc]]
            [teet.ui.format :as format]))

(def Link typography/Link)
(def Link2 typography/Link2)

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
    [typography/TextBold {:component :span
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
      [SmallGrayText sub-label])]
   (when end-text
     [SmallGrayText end-text])])

(defn heading-buttons-style
  []
  {:display :flex
   :flex-wrap :nowrap})

(defn header-with-actions [header-text & actions]
  [:div {:class (<class common-styles/header-with-actions)}
   [typography/Heading1 header-text]
   (into [:div {:class (<class heading-buttons-style)}]
         actions)])


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
  ^{:pseudo {:hover {:text-decoration :none}}}
  {:font-size "24px"
   :text-decoration :underline
   :display :flex
   :align-items :center})

(defn- vektorio-link-style
  []
  ^{:pseudo {:hover {:text-decoration :none}}}
   {:margin-left "10px"
    :margin-right "10px"
    :font-size "24px"
    :text-decoration :underline
    :display :flex
    :align-items :center})

(defn- contract-link-style
  []
  ^{:pseudo {:last-child {:margin-right 0}}}
  {:margin-right "1rem"
   :white-space :nowrap
   :display :flex
   :align-items :center
   :font-size "0.875rem"})

 (defn- contract-link-icon-style
   []
   {:margin-left "0.5rem"
    :font-size "1rem"})

(defn- thk-link-icon-style
  []
  {:margin-left "0.5rem"
   :font-size "20px"
   :font-weight :bold})

(defn- vektorio-link-icon-style
  []
  (thk-link-icon-style))

(defn thk-link
  [opts label]
  [Link (merge {:class (<class thk-link-style)}
               opts)
   label
   [icons/action-open-in-new {:class (<class thk-link-icon-style)}]])

(defn vektorio-link
  [opts label]
  [Link (merge {:class (<class vektorio-link-style)}
          opts)
   label
   [icons/action-open-in-new {:class (<class vektorio-link-icon-style)}]])

(defn external-contract-link
  [opts label]
  [Link (merge {:class (<class contract-link-style)
                :target :_blank}
               opts)
   label
   [icons/action-open-in-new {:class (<class contract-link-icon-style)}]])

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
  [bg-color separator?]
  ^{:pseudo {:hover {:background-color (darken bg-color 10)}}}
  {:width "100%"
   :justify-content :space-between
   :flex-direction :column
   :align-items :flex-start
   :cursor :pointer
   :background-color bg-color
   :padding "1rem"
   :border-bottom (if separator? "1px solid white" "none")
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

(defn collapsable-heading-container
  [bg-color font-color]
  {:background-color bg-color
   :position :relative
   :color font-color})

(defn hierarchical-container2
  ([param]
   [hierarchical-container2 param theme-colors/gray-light])
  ([{:keys [text-color content open? heading heading-button children after-children-component]
     :or {text-color :inherit
          open? false}}
    bg-color]
   (r/with-let [open? (r/atom open?)
                toggle-open! #(do
                                (.stopPropagation %)
                                (swap! open? not))]
     [:div {:style {:border-bottom "3px solid white"}}
      [:div {:class (<class hierarchical-heading-container2 bg-color text-color (and
                                                                                  (or (seq children) after-children-component)
                                                                                  @open?))}
       [:div                                                ;; This is a div because buttons shouldn't contain buttons even though this is bad practice as well
        {:class (<class hierarchical-container-button-style bg-color (seq content))
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
                  [hierarchical-container2 child (or (:color child) (as-hex (lighten bg-color 5)))])
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

(defn heading-and-gray-border-body
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
  [right-align-last? font-size]
  (with-meta (merge {:margin-right "1rem"
                     :margin-bottom "1rem"}
               (when (some? font-size)
                 {:font-size font-size}))
    (merge (when (some? font-size)
      {:combinators {[:> :h6] {:font-size font-size}}})
      (when right-align-last?
        {:pseudo {:last-child {:margin-right 0 :margin-left "auto"}}}))))

(defn basic-information-row
  "[[data-title data-value]...]"
  ([data]
   [basic-information-row {} data])
  ([opts data]
   [:div {:class (<class common-styles/flex-row-wrap)}
    (doall
     (for [[label data row-options :as row] data
           :when row]
        ^{:key (str (:key opts) "-" label)}
        [:div (merge row-options
                     {:class [(<class info-row-item-style (:right-align-last? opts) (:font-size opts))
                              (:class row-options)]})
         [typography/SectionHeading label]
         [:div data]]))]))

(defn- basic-information-column-row-style
  []
  {:border-top (str "1px solid " theme-colors/gray-lighter)})

(defn basic-information-column-cell-style
  []
  ^{:pseudo {:last-of-type {:padding "0.5rem 0 0.5rem 0.5rem"
                            :border-right 0}}}
  {:display :table-cell
   :max-width "0px"
   :padding "0.5rem 0.5rem 0.5rem 0"
   :border-right (str "1px solid " theme-colors/gray-lighter)})

(defn info-box-icon-container-style
  []
  {:margin-right "0.25rem"
   :display :flex})

(defn basic-information-column
  "Takes a list of maps with keys [:label :data :key] and displays given data in a table"
  [rows]
  [:table {:style {:border-collapse :collapse
                   :width "100%"}}
   [:tbody
    (for [{:keys [label data] :as row} rows]
      ^{:key (str (or (:key row) (:db/id row) (str label "+" data)))}
      [:tr {:class (<class basic-information-column-row-style)}
       [:td {:class (<class basic-information-column-cell-style)}
        label]
       [:td {:class (<class basic-information-column-cell-style)}
        data]])]])

(defn column-with-space-between [space-between & children]
  (let [cls (<class common-styles/padding-bottom space-between)]
    (into [:<>]
          (map (fn [child]
                 [:div {:class cls}
                  child]))
          children)))

(defn info-box [{:keys [variant title content icon cy]
                 :or {variant :info}}]
  (let [icon (or icon
                 (case variant
                   :success [icons/action-check-circle-outline]
                   :warning [icons/alert-warning-outlined]
                   :error [icons/alert-error-outline]
                   [fi/info]))]
    [:div (merge {:class (<class common-styles/info-box variant)}
                 (when cy
                   {:data-cy cy}))
     [:div {:class [(<class common-styles/flex-align-center)
                    (<class common-styles/margin-bottom 0.5)]}
      [:div {:class (<class info-box-icon-container-style)}
       icon]
      (if title
        [typography/Heading3 title]
        content)]
     (when (and title content)
       content)]))

(defn popper-tooltip-content [{:keys [variant title body icon]
                        :or {variant :info}}]
  (let [icon (or icon
                 (case variant
                   :success [icons/action-check-circle-outline]
                   :warning [icons/alert-warning-outlined]
                   :error [icons/alert-error-outline]
                   [fi/info]))]
    [:div {:class (<class common-styles/popper-tooltip variant)}
     [:div {:style {:display :flex
                    :justify-content :center
                    :align-items :center}}
      [:div {:style {:margin-right "0.5rem"}}
       icon]
      [:div
       [typography/TextBold title]
       [typography/Text body]]]]))

(defn popper-tooltip-container-style
  []
  {:display :block})

(defn popper-tooltip
  "Wrap component in an error tooltip. When component is hovered, the
  error message is displayed.

  If msg is nil, the component is returned as is.
  Otherwise msg must be a map containing :title and :body  for the error message."
  [{:keys [title body variant icon class] :as msg
    :or {variant :error}} component]
  (r/with-let [hover? (r/atom false)
               anchor-el (r/atom nil)
               set-anchor-el! #(reset! anchor-el %)
               enter! #(reset! hover? true)
               leave! #(reset! hover? false)
               container-class (if (nil? class)
                                 (<class popper-tooltip-container-style)
                                 class)]
    (if (nil? msg)
      component
      [:div {:on-mouse-enter enter!
             :on-mouse-leave leave!
             :on-click enter!
             :on-focus enter!
             :on-blur leave!
             :ref set-anchor-el!
             :tabIndex 0
             :class container-class}
       component
       [Popper {:style {:z-index 1600}                      ;; z-index is not specified for poppers so they by default appear under modals
                :open @hover?
                :anchor-el @anchor-el
                :placement "bottom-start"}
        [popper-tooltip-content {:variant variant
                                 :title title
                                 :body body
                                 :icon icon}]]])))


(defn portal
  "Create new portal component pair. Returns vector of two components [from-comp to-comp].
  When from-comp component is rendered it will render a portal that places the children
  to the to-comp. When to-comp is rendered it will create the target element where the
  portal places its children.
  "
  []
  (let [elt (r/atom nil)]
    [(fn portal-from [& children]
       (when-let [e @elt]
         (into [Portal {:container e}] children)))
     (fn portal-to []
       [:div {:ref #(reset! elt %)}])]))

(defn- context-menu-item [toggle-menu! {:keys [icon label on-click link id]}]
  (let [label (if (fn? label)
                (label)
                label)]
    [MenuItem
     (merge
      {:on-click (fn [_]
                   (toggle-menu!)
                   (when on-click
                     (on-click)))}
      (when id {:id id}))
     [ListItemIcon icon]
     (if link
       [Link link label]
       [typography/Text label])]))

(defn context-menu
  "Shows a button that opens a context menu.
  Label is the label for the button that opens the menu.
  Icon is the icon for the button.

  Items is a collection of menu items that are to be shown.
  Each item is a map containing an :icon, a :label and
  :on-click. Nil values are ignored.

  Optional keys:
  :menu-placement controls where the Popper component is placed
                  in relation to the button (detaults to bottom-end)"
  [{:keys [label icon items menu-placement id class]
    :or {menu-placement "bottom-end"}}]
  (r/with-let [open? (r/atom false)
               toggle! #(swap! open? not)
               anchor (r/atom false)
               set-anchor! #(reset! anchor %)]
    [:<>
     [buttons/button-secondary
      (merge
       {:size "small"
        :end-icon (r/as-element icon)
        :on-click toggle!
        :ref set-anchor!}
       (when id {:id id})
       (when class
         {:class class}))
      label]
     [Popper {:open @open?
              :anchor-el @anchor
              :placement menu-placement}
      [:div {:style {:margin-top "0.5rem"}}
       [ClickAwayListener
        {:on-click-away toggle!}
        [Paper
         (mapc (r/partial context-menu-item toggle!)
               (remove nil? items))]]]]]))

(defn date-label-component
  "Shows :label with formatted date from :value with an info icon and :tooltip"
  [{value :value label :label tooltip :tooltip}]
  [:div
   {:data-cy "date-label-component"}
   [:label {:class (<class common-styles/input-label-style false false)}
    [typography/Text2Bold label]]
   [:div
    {:class (<class common-styles/flex-row-center)}
    [popper-tooltip tooltip
     [icons/action-info-outlined
      {:style {:color :primary}}]]
    [:div
     {:style {:padding-left "0.3em"}}
     [typography/Text (format/date value)]]]])

(defn scroll-sensor [on-scroll]
  (let [observer (js/IntersectionObserver.
                  (fn [entries]
                    (when (.-isIntersecting (aget entries 0))
                      (on-scroll)))
                  #js {:threshold #js [1]})]
    (fn [_]
      [:span {:ref #(when %
                      (.observe observer %))}])))

(defn tag-style
  [bg-color]
  {:color theme-colors/white
   :display :inline-block
   :background-color bg-color
   :padding "1px 5px"
   :border-radius "2px"})

(defn primary-tag
  [text]
  [:div {:class (<class tag-style theme-colors/primary)}
   [typography/Text3Bold text]])

(defn error-boundary [_child]
  (let [error (r/atom nil)]
    (r/create-class
     {:component-did-catch (fn [_ err info]
                             (reset! error {:error err
                                            :error-info info
                                            :details-open? false}))
      :reagent-render
      (fn [child]
        (if-let [err @error]
          [:div
           [:b "ERROR: " (:error err)]
           [:a {:on-click (swap! error :details-open? not)} "details"]
           [Collapse {:in (:details-open? err)}
            [:div (pr-str (:error-info err))]]]
          child))})))
