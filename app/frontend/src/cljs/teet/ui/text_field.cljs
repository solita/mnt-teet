(ns teet.ui.text-field
  (:require [herb.core :as herb :refer [<class]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.material-ui :refer [IconButton]]
            [teet.ui.common :as common]
            [teet.common.common-styles :as common-styles]
            [teet.ui.typography :as typography]))

(defn- input-field-style
  [error multiline disabled? start-icon? end-icon? type]
  (merge
    ^{:pseudo {:invalid {:box-shadow "inherit"
                         :outline "inherit"}
               "-webkit-outer-spin-button" {"-webkit-appearance" "none"}}}
    {:border-radius "3px"
     :border (if error
               (str "1px solid " theme-colors/error)
               (str "1px solid " theme-colors/black-coral-1))
     :padding "10px 13px"
     :width "100%"
     :font-size "1rem"}
    (when multiline
      {:resize :vertical})
    (when start-icon?
      {:padding-left "2.5rem"})
    (when end-icon?
      {:padding-right "2.5rem"})
    (when disabled?
      {:color theme-colors/text-disabled
       :background-color theme-colors/card-background-extra-light})
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


(defn file-end-style
  []
  {:color theme-colors/gray-light
   :max-height "42px"
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

(defn unit-end-icon
  "Display unit (like SI unit or currency) as end icon for text field"
  [unit]
  [:span {:class (<class end-icon-style)} unit])

(def euro-end-icon (partial unit-end-icon "€"))
(def sqm-end-icon (partial unit-end-icon "m²"))


(defn file-end-icon
  [file-type]
  [:span {:class (<class file-end-style)} (str file-type)])

(defn TextField
  [{:keys [label id type error style input-button-icon read-only? inline?
           input-button-click required input-style dark-theme?
           multiline error-text input-class start-icon
           maxrows rows hide-label? end-icon label-element]
    :as props
    :or {rows 2}} & _children]
  (let [element (if multiline
                  :textarea
                  :input)]
    [:label {:for id
             :class (<class common-styles/input-label-style read-only? dark-theme?)
             :style style}
     (when-not hide-label?
       (if label-element
         [label-element label (when required [common/required-astrix])]
         [typography/Text2Bold
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
                               :step :on-key-down :disabled :min :max :type :ref :value
                               :required :id :on-blur :placeholder :pattern])
                 {:style input-style
                  :class (herb/join (<class input-field-style error multiline read-only?
                                            (boolean start-icon) (boolean end-icon) type)
                                    input-class)}
                 (when read-only?
                   {:disabled true})
                 (when multiline
                   {:rows rows
                    :maxrows maxrows}))]
      (when end-icon
        end-icon)
      (when (and input-button-click input-button-icon)
        [IconButton {:on-click input-button-click
                     :disable-ripple true
                     :color :primary
                     :class (<class input-button-style)}
         [input-button-icon]])]
     (when (and error error-text)
       [:span {:class (<class common-styles/input-error-text-style)}
        error-text])]))
