(ns teet.ui.form-fields.selection
  "Implements selection form fields"
  (:require [clojure.string :as str]
            [reagent.core :as r]
            [teet.localization :as localization :refer [tr]]
            [teet.ui.form-fields :as form-fields]
            [teet.ui.icons :as icons]
            [stylefy.core :as stylefy]
            [taoensso.timbre :as log]))

(defn radio-selection [{:keys [update! label name show-option options error warning required? placeholder
                               option-value disabled? dir radio-selection-style]}
                       data]
  (let [selected (if (coll? data)
                   (set (or data #{}))
                   #{data})
        placeholder (if (= :default placeholder)
                      (tr [:common-text :all])
                      placeholder)
        options (if placeholder
                  (into [placeholder] options)
                  options)
        option-value (or option-value identity)
        option-idx (zipmap options
                           (map str (range)))
        dir (or dir :horizontal)]

    [:div.radio-selection (stylefy/use-style {} #_(merge style-form-fields/radio-selection radio-selection-style))
     #_[form-fields/label-with-required label {} required?]
     (doall
      (map-indexed
       (fn [i option]
         (let [checked? (not (nil? (selected (if (coll? option)
                                               (second option)
                                               option))))]
           ^{:key (str "radio-" (option-idx option))}
           [:div.radio (stylefy/use-style {} #_(if (= dir :horizontal)
                                             style-form-fields/radio-horizontal
                                             style-form-fields/radio-vertical))
            [:label (stylefy/use-style {} #_(if (= dir :horizontal)
                                          style-form-fields/radio-horizontal-label
                                          style-form-fields/radio-vertical-label))
             [:input (merge (stylefy/use-style {} #_(if (= dir :horizontal)
                                                  style-form-fields/radio-horizontal-input
                                                  style-form-fields/radio-vertical-input))
                            {:type :radio
                             :name name
                             :value (option-idx option)
                             :checked (if (or (= option-value (if (coll? option)
                                                                (second option)
                                                                option))
                                              checked?)
                                        "checked" "")
                             :disabled disabled?
                             :on-change #(do
                                           (update! (if (= nil (if (coll? option)
                                                                 (first option)
                                                                 option))
                                                      nil
                                                      option)))})]
             (if (and placeholder (zero? i))
               placeholder
               (show-option option))]]))
       options))
     (when (or error warning)
       [:div
        (stylefy/use-sub-style {} #_style-form-fields/radio-selection :required)
        (if error error warning)])]))

(defn field-selection [{:keys [update! label label-style show-option options
                               selection-style select-style required? disabled?
                               option-value placeholder tooltip]}
                             data]
  (let [show-option (or show-option str)
        option-value (or option-value identity)
        placeholder (if (= :default placeholder)
                      (if required?
                        (tr [:common-text :select])
                        "--")
                      placeholder)
        options (if placeholder
                  (cons placeholder (into [] options))
                  options)
        option-idx (zipmap (map option-value options)
                           (map str (range)))
        show-tooltip-atom? (atom false)]
    [:div (stylefy/use-style (merge (when tooltip
                                    {:position "relative"})
                                  selection-style)
                           {:on-mouse-over (when tooltip #(reset! show-tooltip-atom? true))
                            :on-mouse-out (when tooltip #(reset! show-tooltip-atom? false))})
     #_(when tooltip
       [form-fields/field-tooltip tooltip show-tooltip-atom?])
     #_[form-fields/label-with-required label label-style required?]
     [:select (stylefy/use-style {}
                               {:value (or (option-idx data) "")
                                :disabled disabled?
                                :on-change #(let [v (-> % .-target .-value)]
                                              (update! (some (fn [[opt idx]]
                                                               (when (= v idx)
                                                                 opt))
                                                             option-idx)))})
      (doall
        (for [option options
              :let [val (option-value option)
                    idx (option-idx val)
                    show-val (if (= option placeholder)
                               placeholder
                               (show-option option))]]
          ^{:key (str idx "-" show-val)}
          [:option {:value (if (= option placeholder) nil idx)} show-val]))]]))

(defn- highlight-matches [text substrings]
  (loop [acc [:<>]
         text text]
    (let [lower-text (str/lower-case text)
          [idx substr] (first
                        (sort-by first
                                 (keep #(let [idx (.indexOf lower-text %)]
                                          (when (>= idx 0)
                                            [idx %]))
                                       substrings)))
          idx (or idx -1)
          matching-text (when-not (neg? idx)
                          (subs text idx (+ idx (count substr))))
          after-match (subs text (+ idx (count substr)))]
      (cond
        ;; No more matches
        (neg? idx)
        (conj acc text)

        ;; There is text before the matching part
        (pos? idx)
        (recur (into acc
                     [;; text before the match
                      (subs text 0 idx)

                      ;; matching text
                      [:b matching-text]])
               after-match)

        ;; Match is at the beginning
        :else
        (recur (conj acc [:b matching-text])
               after-match)))))

(defn search-selection [_ _]
  (let [search-text (r/atom "")
        highlight-index (r/atom nil)
        input-width (atom "100%")
        input-field (atom nil)
        show-all-options? (r/atom false)
        focus! (fn []
                 (r/after-render #(some-> @input-field .focus)))]
    (r/create-class
     {:component-did-mount #(let [input (some-> % .-refs (aget "root")
                                                (.querySelector "input"))]
                              (reset! input-field input)
                              (reset! input-width (.-clientWidth input)))
      :reagent-render
      (fn [{:keys [update! label label-style show-option options
                   required? full-width? placeholder]}
           data]
        (let [show-option (or show-option str)
              term (str/lower-case @search-text)
              substrings (remove str/blank? (str/split term #"\s+"))
              all? @show-all-options?
              matching-options (cond
                                 all?
                                 options

                                 (empty? substrings)
                                 []

                                 :else
                                 (filter (fn [option]
                                           (when-let [show-val (show-option option)]
                                             (let [lower-show-val (str/lower-case show-val)]
                                               (every? (partial str/includes? lower-show-val)
                                                       substrings))))
                                         options))
              select! #(do
                         (update! %)
                         (reset! show-all-options? false))]
          [:div {:ref "root"}
           #_[form-fields/label-with-required label label-style required?]

           ;; Show search icon on left side of search field that can be used to activate
           [icons/action-search
            {:position "absolute"
             :margin-top "5px"
             :cursor "pointer"}
            {:on-click #(do
                          (when data
                            (update! nil))
                          (reset! search-text "")
                          (focus!))}]
           ^{:key "search-input"}
           [:input {:placeholder placeholder
                    :type "text"
                    :style (merge
                            {:margin-left "22px"} ; make room for search icon
                            (when full-width? {:width "calc(100% - 22px)"}))
                    :value (if data
                             (show-option data)
                             @search-text)
                    :on-key-down (fn [evt]
                                   (case (.-key evt)
                                     "ArrowUp"
                                     (do
                                       (.preventDefault evt)
                                       (swap! highlight-index #(if (and % (pos? %))
                                                                 (dec %)
                                                                 (dec (count matching-options)))))

                                     "ArrowDown"
                                     (do
                                       (.preventDefault evt)
                                       (when (empty? matching-options)
                                         (reset! show-all-options? true))
                                       (swap! highlight-index #(if (and % (< % (dec (count matching-options))))
                                                                 (inc %)
                                                                 0)))

                                     " "
                                     (when (empty? matching-options)
                                       (.preventDefault evt)
                                       (reset! show-all-options? true))

                                     "Enter"
                                     (when (seq matching-options)
                                       (.preventDefault evt)
                                       (let [idx @highlight-index]
                                         (if (and idx (<= 0 idx (dec (count matching-options))))
                                           (select! (nth matching-options idx))
                                           (select! (first matching-options)))))

                                     "Tab"
                                     (when (= 1 (count matching-options))
                                       (select! (first matching-options)))

                                     ;; By default, do nothing
                                     nil))
                    :on-change #(do
                                  ;; Clear a selection
                                  (when data
                                    (update! nil))
                                  (reset! show-all-options? false)
                                  (reset! highlight-index nil)
                                  (reset! search-text (-> % .-target .-value)))}]

           [:div {:style {:float "right" :height "0px"}}
            [icons/hardware-keyboard-arrow-down
             {:position "relative"
              :top "-24px"
              :left "-3px"}
             {:on-click #(swap! show-all-options? not)}]]
           (when (or all?
                     (and (not data)
                          (not (str/blank? @search-text))))
             (let [hl-idx @highlight-index]
               ;; Scroll a highlighted result into view (for keyboard arrow navigation)
               (when hl-idx
                 (r/after-render #(when-let [res (.querySelector js/document ".search-result-highlight")]
                                    (.scrollIntoView res))))
               [:div.search-results (stylefy/style {:position "absolute"
                                                  :width (- @input-width 4)
                                                  :background-color "white" ;(colors/input-bg)
                                                  :z-index 1001
                                                  :border-left "1px solid black"
                                                  :border-right "1px solid black"
                                                  :border-bottom "1px solid black"
                                                  :padding "2px"
                                                  :overflow-y "scroll"
                                                  :max-height 300
                                                  :margin-left "22px"})
                (doall
                 (map-indexed
                  (fn [i option]
                    (let [show-val (show-option option)
                          highlight? (= i hl-idx)]
                      ^{:key i}
                      [:div (stylefy/style
                             (merge
                              {:color "black" ;(colors/primary-text-color)
                               :stylefy.core/mode {:hover {:color "white"
                                                           :background-color "#3a87fd"}}}

                              (when highlight?
                                {:color "white"
                                 :background-color "#3a87fd"}))
                             (merge
                              {:on-click #(select! option)}
                              (when highlight?
                                {:class "search-result-highlight"})))

                       (if all?
                         show-val
                         (highlight-matches show-val substrings))]))
                  matching-options))]))]))})))


(defmethod form-fields/field :selection [{radio? :radio? search? :search? :as field} data]
  (cond
    radio?
    [radio-selection field data]

    search?
    [search-selection field data]

    :else
    [field-selection field data]))

(defmethod form-fields/show-value :selection [{:keys [show-option option-value label label-style] :as s} data]
  [:div
   #_[form-fields/label-with-required label label-style false]
   (let [option-value (or option-value identity)
         selected-option (some #(when (= data (option-value %))
                                  %)
                               (:options s))]
     (if selected-option
       (show-option selected-option)
       "-"))])
