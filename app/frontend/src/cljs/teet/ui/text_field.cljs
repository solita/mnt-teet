(ns teet.ui.text-field
  (:require [herb.core :as herb :refer [<class]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.user.user-model :as user-model]
            [teet.ui.material-ui :refer [IconButton]]
            [teet.ui.common :as common]
            [teet.common.common-styles :as common-styles]
            [reagent.core :as r]
            [teet.ui.select :as select]
            react-mentions
            [clojure.set :as set]))

(def Mention (r/adapt-react-class (aget react-mentions "Mention")))
(def MentionsInput (r/adapt-react-class (aget react-mentions "MentionsInput")))

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

(defn- label-text-style
  []
  {:display :block
   :font-size "1rem"})

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
  [{:keys [label id type ref error style value
           on-change input-button-icon read-only?
           placeholder input-button-click required input-style
           multiline on-blur error-text input-class start-icon
           maxrows rows auto-complete step hide-label? end-icon label-element] :as _props
    :or {rows 2}} & _children]
  (let [element (if multiline
                  :textarea
                  :input)]
    [:label {:for id
             :style style
             :class (<class label-style)}
     (when-not hide-label?
       (if label-element
         [label-element label (when required [common/required-astrix])]
         [:span {:class (<class label-text-style)}
          label (when required
                  [common/required-astrix])]))
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
                                                 (boolean start-icon) type)
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



(defn mentions-input
  [_]
  (let [input-ref (atom nil)
        caret (atom nil)
        old-mentions (atom nil)]
    (r/create-class
      {:component-did-update
       (fn [_]
         (let [inp @input-ref
               c @caret]
           (when (and inp c)
             (set! (.-selectioStart inp) c)
             (set! (.-selectionEnd inp) c))))

       :reagent-render
       (fn [{:keys [e! value on-change _error _read-only? label id required]}]
         [:label.mention {:for id}
          [:span {:class (<class label-text-style)}
           label (when required
                   [common/required-astrix])]
          [MentionsInput {:value value
                          :on-change (fn [e new-value new-plain-text-value new-mentions]
                                       (let [old-mention-ids (into #{} (map #(aget % "id")) @old-mentions)
                                             new-mention-ids (into #{} (map #(aget % "id")) new-mentions)
                                             added-mention (first (set/difference new-mention-ids old-mention-ids))
                                             removed-mention (first (set/difference old-mention-ids new-mention-ids))]
                                         (reset! caret
                                                 (cond
                                                   ;; Mention added: move caret after it
                                                   added-mention
                                                   (some #(when (= (aget % "id") added-mention)
                                                            (+ (aget % "plainTextIndex")
                                                               (count (aget % "display")))) new-mentions)

                                                   ;; Mention removed: move caret at it's start index
                                                   removed-mention
                                                   (some #(when (= (aget % "id") removed-mention)
                                                            (aget % "plainTextIndex")) @old-mentions)

                                                   ;; No mentions changed, keep caret at current position
                                                   :else
                                                   (.-selectionStart @input-ref)))
                                         (reset! old-mentions new-mentions))
                                       (on-change e)
                                       (when (not= (count new-plain-text-value)
                                                   @caret)
                                         ;; Flush forces re-render did-update call
                                         ;; Otherwise there would be situations in which the change is fired twice
                                         ;; without a call to did-update
                                         (r/flush)))
                          :input-ref #(reset! input-ref %)
                          :class-name "comment-textarea"}
           [Mention {:trigger "@"
                     :display-transform (fn [_ display]
                                          (str "@" display))
                     :class-name "mentions__mention"
                     :data (fn [search callback]
                             (e! (select/->CompleteUser
                                   search
                                   (fn [users]
                                     (callback
                                       (into-array
                                         (for [u users]
                                           #js {:display (user-model/user-name u)
                                                :id (str (:db/id u))})))))))}]]])})))
