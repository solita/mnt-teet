(ns teet.ui.text-field
  (:require [herb.core :as herb :refer [<class]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.material-ui :refer [IconButton]]
            [teet.ui.common :as common]
            [teet.common.common-styles :as common-styles]))

(defn- input-field-style
  [error multiline read-only? start-icon?]
  (merge
    ^{:pseudo {:invalid {:box-shadow "inherit"
                         :outline "inherit"}
               :focus theme-colors/focus-style}}
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
      {:color :inherit})))

(defn- label-text-style
  []
  {:display :block
   :font-size "1rem"})

(defn- label-style
  []
  {:margin-bottom "1.5rem"})

(defn- input-button-style
  []
  {:max-height "42px"
   :position :absolute
   :top 0
   :right 0})

(defn- start-icon-style
  []
  {:min-height "42px"
   :display :flex
   :align-items :center
   :position :absolute
   :left "10px"})

(defn TextField
  [{:keys [label id type ref error style value
           on-change input-button-icon read-only?
           placeholder input-button-click required input-style
           multiline on-blur error-text input-class start-icon
           maxrows rows auto-complete step hide-label?] :as _props
    :or {rows 2}} & _children]
  (let [element (if multiline
                  :textarea
                  :input)]
    [:label {:for id
             :style style
             :class (<class label-style)}
     (when-not hide-label?
       [:span {:class (<class label-text-style)}
                label (when required
                        [common/required-astrix])])
     [:div {:style {:position :relative}}
      (when start-icon
        [start-icon {:color :primary
                     :class (<class start-icon-style)}])
      [element (merge {:type type
                       :ref ref
                       :required required
                       :value value
                       :id id
                       :style input-style
                       :on-blur on-blur
                       :placeholder placeholder
                       :class (herb/join (<class input-field-style error multiline read-only?
                                                 (boolean start-icon))
                                         input-class)
                       :on-change on-change}
                      (when read-only?
                        {:disabled true})
                      (when multiline
                        {:rows rows
                         :maxrows maxrows})
                      (when auto-complete
                        {:auto-complete auto-complete})
                      (when step
                        {:step step}))]
      (when (and input-button-click input-button-icon)
        [IconButton {:on-click input-button-click
                     :disable-ripple true
                     :color :primary
                     :class (<class input-button-style)}
         [input-button-icon]])]
     (when (and error error-text)
       [:span {:class (<class common-styles/input-error-text-style)}
        error-text])]))
