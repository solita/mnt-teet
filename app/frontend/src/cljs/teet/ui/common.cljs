(ns teet.ui.common
  "Common UI utilities"
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.ui.icons :as icons]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.material-ui :refer [ButtonBase Link Chip]]
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
   [:span label]
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
  [bg-color font-color]
  ^{:pseudo {:before {:content "''"
                      :width 0
                      :height 0
                      :border-bottom "15px solid transparent"
                      :border-left (str "15px solid " bg-color)
                      :position :absolute
                      :bottom "-15px"
                      :transform "rotate(90deg)"
                      :left 0}}}
  {:background-color bg-color
   :position :relative
   :color font-color})

(defn hierarchical-child-container
  []
  {:display :flex
   :flex-direction :column
   :margin-left "15px"})

(defn hierarchical-container
  [{:keys [heading-color heading-text-color heading-content children]
    :or {heading-color theme-colors/gray-lighter
         heading-text-color :inherit}}]
  [:<>
   [:div {:class (<class hierarchical-heading-container heading-color heading-text-color)}
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

(defn estate-detail
  [{:keys [title date body]}]
  [:div {:style {:margin-bottom "1.5rem"}}
   [:div {:style {:margin-bottom "0.25rem"}}
    [typography/BoldGreyText {:style {:display :inline}} title] " "
    [typography/GreyText {:style {:display :inline}} (format/parse-date-string date)]]
   [:div {:style {:padding-left "0.5rem"
                  :border-left (str "solid 7px " theme-colors/gray-light)}}
    [:span body]]])
