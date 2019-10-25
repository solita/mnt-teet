(ns teet.ui.common
  "Common UI utilities"
  (:require [reagent.core :as r]
            [herb.core :as herb :refer [<class]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.typography :as typography]))

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
   :color "#5D6071"

   :font-family "Roboto"
   :font-weight 300
   :font-size "14px"
   :line-height "14px"
   :transform "translate(0, 2.5px)"
   :transform-origin "top left"})

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
    [typography/Text {:component :span
                      :classes {:root (<class label-style)}}
     label]
    [typography/Text {:component :span
                      :classes {:root (<class data-style)}}
     data]]])
