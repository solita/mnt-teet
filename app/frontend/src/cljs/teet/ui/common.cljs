(ns teet.ui.common
  "Common UI utilities"
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.localization :refer [tr]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.format :as format]
            [teet.ui.select :as select]
            [teet.ui.material-ui :refer [ButtonBase]]
            [teet.ui.typography :refer [Text SmallText]]))

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
             :focus {:background-color theme-colors/gray-lightest}}}
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

;;
;; Status
;;
(defn status-container-style
  []
  {:display :flex
   :flex-direction :row
   :align-items :center
   :border-bottom "solid 1px"
   :border-color theme-colors/gray-light
   :padding-bottom "1rem"
   :margin-bottom "1rem"})

(defn status-style
  []
  {:flex-basis "30%"})

(defn status
  [{:keys [e! status attribute modified on-change]}]
  [:div {:class (<class status-container-style)}
   [select/select-enum {:e! e!
                        :on-change on-change
                        :value status
                        :tiny-select? true
                        :attribute attribute
                        :class (<class status-style)}]
   [labeled-data {:label (tr [:common :last-modified])
                  :data (or (format/date modified)
                            "-")}]])
