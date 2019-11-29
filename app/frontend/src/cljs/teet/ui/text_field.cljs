(ns teet.ui.text-field
  (:require [herb.core :as herb :refer [<class]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.material-ui :refer [IconButton]]))

(defn- input-field-style
  [error multiline start-icon?]
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
      {:padding-left "2.5rem"})))

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

(defn- error-span-style
  []
  {:font-size "1rem"
   :color theme-colors/error
   :position :absolute})

(defn- start-icon-style
  []
  {:min-height "42px"
   :display :flex
   :align-items :center
   :position :absolute
   :left "10px"})

(defn TextField
  [{:keys [label id type ref error style value
           on-change input-button-icon
           placeholder input-button-click required input-style
           multiline on-blur error-text input-class start-icon
           maxrows rows] :as props
    :or {rows 2}} & children]
  (let [element (if multiline
                  :textarea
                  :input)]
    [:label {:for id
             :style style
             :class (<class label-style)}
     [:span {:class (<class label-text-style)}
      label (when required
              " *")]
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
                       :class (herb/join (<class input-field-style error multiline (boolean start-icon)) input-class)
                       :on-change on-change}
                      (when multiline
                        {:rows rows
                         :maxrows maxrows}))]
      (if (and input-button-click input-button-icon)
        [IconButton {:on-click input-button-click
                     :disable-ripple true
                     :color :primary
                     :class (<class input-button-style)}
         [input-button-icon]])]
     (when (and error error-text)
       [:span {:class (<class error-span-style)}
        error-text])]))
