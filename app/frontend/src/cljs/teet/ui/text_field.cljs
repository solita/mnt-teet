(ns teet.ui.text-field
  (:require [herb.core :as herb :refer [<class]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.material-ui :refer [IconButton]]
            [teet.ui.common :as common]
            [teet.common.common-styles :as common-styles]))

(defn- input-field-style
  [error multiline read-only? start-icon? type]
  (merge
    ^{:pseudo {:invalid {:box-shadow "inherit"
                         :outline "inherit"}
               :focus theme-colors/focus-style
               "-webkit-outer-spin-button" {"-webkit-appearance" "none"}}}
    {:border-radius "2px"
     :border (if error
               (str "1px solid " theme-colors/error)
               (str "1px solid " theme-colors/gray-light))
     :padding "10px 13px"
     :width "100%"
     :font-size "1rem"}
    (when multiline
      {:resize :vertical})
    (when start-icon?
      {:padding-left "2.5rem"})
    (when read-only?
      {:color :inherit
       :background-color theme-colors/gray-lighter})
    (when (= type :number)
      {"-moz-appearance" "textfield"})))



(defn- label-style
  []
  {:margin-bottom "1.5rem"})

(defn input-button-style
  []
  {:max-height "42px"
   :position :absolute
   :top "50%"
   :right 0
   :transform "translateY(-50%)"})

(defn end-icon-style
  []
  {:max-height "42px"
   :position :absolute
   :top "50%"
   :right "10px"
   :transform "translateY(-50%)"})

(defn- start-icon-style
  []
  {:min-height "42px"
   :display :flex
   :align-items :center
   :position :absolute
   :left "10px"})

(defn euro-end-icon
  []
  [:span {:class (<class end-icon-style)} "€"])

(defn sqm-end-icon
  []
  [:span {:class (<class end-icon-style)} "m²"])

(defn TextField
  [{:keys [label id type error style input-button-icon read-only? inline?
           input-button-click required input-style
           multiline error-text input-class start-icon
           maxrows rows hide-label? end-icon label-element
           ] :as props
    :or {rows 2}} & _children]
  (let [element (if multiline
                  :textarea
                  :input)]
    [:label {:for id
             :style style}
     (when-not hide-label?
       (if label-element
         [label-element label (when required [common/required-astrix])]
         [:span {:class (<class common-styles/label-text-style)}
          label (when required
                  [common/required-astrix])]))
     [:div {:style {:position :relative
                    :display (if inline? :inline-block :block)}}
      (when start-icon
        [start-icon {:color :primary
                     :class (<class start-icon-style)}])
      [element (merge
                 (select-keys props
                              [:on-change :lang :on-focus :auto-complete
                               :step :on-key-down :min :max :type :ref :value
                               :required :id :on-blur :placeholder])
                 {:style input-style
                  :class (herb/join (<class input-field-style error multiline read-only?
                                            (boolean start-icon) type)
                                    input-class)}
                 (when read-only?
                   {:disabled true})
                 (when multiline
                   {:rows rows
                    :maxrows maxrows}))]
      (when end-icon
        [end-icon])
      (when (and input-button-click input-button-icon)
        [IconButton {:on-click input-button-click
                     :disable-ripple true
                     :color :primary
                     :class (<class input-button-style)}
         [input-button-icon]])]
     (when (and error error-text)
       [:span {:class (<class common-styles/input-error-text-style)}
        error-text])]))
