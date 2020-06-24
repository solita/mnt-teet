(ns teet.ui.select
  "Selection components"
  (:require [reagent.core :as r]
            [herb.core :as herb :refer [<class]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.common.common-controller :as common-controller]
            [teet.common.common-styles :as common-styles]
            [tuck.core :as t]
            [teet.localization :refer [tr]]
            [teet.user.user-info :as user-info]
            [teet.ui.common :as common]
            [taoensso.timbre :as log]
            [teet.ui.material-ui :refer [FormControl FormControlLabel RadioGroup Radio Checkbox
                                         Popper CircularProgress Paper]]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.util :as util :refer [mapc]]
            ["react"]
            [teet.util.collection :as cu]
            [teet.ui.icons :as icons]))

(def select-bg-caret-down "url('data:image/svg+xml;charset=US-ASCII,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%22292.4%22%20height%3D%22292.4%22%3E%3Cpath%20fill%3D%22%23005E87%22%20d%3D%22M287%2069.4a17.6%2017.6%200%200%200-13-5.4H18.4c-5%200-9.3%201.8-12.9%205.4A17.6%2017.6%200%200%200%200%2082.2c0%205%201.8%209.3%205.4%2012.9l128%20127.9c3.6%203.6%207.8%205.4%2012.8%205.4s9.2-1.8%2012.8-5.4L287%2095c3.5-3.5%205.4-7.8%205.4-12.8%200-5-1.9-9.2-5.5-12.8z%22%2F%3E%3C%2Fsvg%3E')")

(defn- primary-select-style
  [error]
  ^{:pseudo {:focus theme-colors/focus-style}}
  {:-moz {:appearance :none}
   :-webkit {:appearance :none}
   :border-radius "2px"
   :appearance "none"
   :display :block
   :background-color :white
   :border (if error
             (str "1px solid " theme-colors/error)
             (str "1px solid " theme-colors/gray-light))
   :padding "10px 13px"
   :width "100%"
   :cursor :pointer
   :max-height "41px"
   :font-size "1rem"
   :background-image select-bg-caret-down
   :background-repeat "no-repeat"
   :background-position "right .7em top 50%"
   :background-size "0.65rem auto"})

(defn- select-label-style
  []
  {:font-size "1rem"})

(defn form-select [{:keys [label name id items on-change value format-item label-element
                           show-label? show-empty-selection? error error-text required empty-selection-label]
                        :or {format-item :label
                             show-label? true}}]
  (let [option-idx (zipmap items (range))
        change-value (fn [e]
                       (let [val (-> e .-target .-value)]
                         (if (= val "")
                           (on-change nil)
                           (on-change (nth items (int val))))))]
    [:label {:for id
             :class (<class select-label-style)}
     (when show-label?
       (if label-element
         [label-element label (when required [common/required-astrix])]
         [:span label (when required
                        [common/required-astrix])]))
     [:div {:style {:position :relative}}
      [:select
       {:value (or (option-idx value) "")
        :name name
        :class (<class primary-select-style error)
        :required (boolean required)
        :id id
        :on-change (fn [e]
                     (change-value e))}
       (when show-empty-selection?
         [:option {:value "" :label empty-selection-label}])
       (doall
        (map-indexed
         (fn [i item]
           [:option {:value i
                     :key i}
            (format-item item)])
         items))]]
     (when (and error-text error)
       [:span {:class (<class common-styles/input-error-text-style)}
        error-text])]))

;; TODO this needs better styles and better dropdown menu

(defonce enum-values (r/atom {}))
(defrecord SetEnumValues [attribute values]
  t/Event
  (process-event [_ app]
    ;; (log/debug "SetEnumValues" attribute (count values))
    (swap! enum-values assoc attribute values)
    app))

;; (common-controller/register-init-event! :set-enum-values (partial ->SetEnumValues))

(defn select-style
  []
  ^{:pseudo {:hover {:border-bottom (str "2px solid " theme-colors/primary)
                     :padding-bottom "5px"}}}               ;;This is done because material ui select can't have box sizing border box
  {:color theme-colors/blue
   :font-family "Roboto"
   :border "none"})

(defn select-opt
  []
  {:font-family "Roboto"
   :color theme-colors/gray-dark})


(defn select-with-action-styles
  []
  ^{:pseudo {:focus theme-colors/focus-style
             :invalid {:box-shadow :inherit
                       :outline :inherit}
             :hover {:margin-bottom "0"
                     :border-bottom (str "2px solid " theme-colors/primary)}}}
  {:-moz {:appearance :none}
   :-webkit {:appearance :none}
   :cursor :pointer
   :background-color :white
   :border-radius 0
   :display :block
   :min-width "5rem"
   :border :none
   :padding-right "2rem"
   :font-size "1rem"
   :background-image select-bg-caret-down
   :background-repeat "no-repeat"
   :background-position "right .7em top 50%"
   :background-size "0.65rem auto"
   :margin-bottom "2px"})

(defn select-with-action
  [{:keys [label id name value items format-item on-change
           required? show-empty-selection? empty-selection-label
           container-class class label-element show-label?]
    :or {format-item :label
         show-label? true}}]
  (let [label-element (if label-element
                        label-element
                        :span)
        option-idx (zipmap items (range))
        change-value (fn [e]
                       (let [val (-> e .-target .-value)]
                         (if (= val "")
                           (on-change nil)
                           (on-change (nth items (int val))))))]
    [:div {:class container-class}
     [:label {:html-for id}
      (when (and label show-label?)
        [label-element (str label ":")])
      [:select
       {:value (or (option-idx value) "")
        :name name
        :required (boolean required?)
        :id id
        :on-change (fn [e]
                     (change-value e))
        :class (herb/join (<class select-with-action-styles)
                          (when class
                            class))}
       (when show-empty-selection?
         [:option {:value ""
                   :label empty-selection-label
                   :class (<class select-opt)}])
       (doall
         (map-indexed
           (fn [i item]
             [:option {:value i
                       :key i
                       :class (<class select-opt)}
              (format-item item)])
           items))]]]))

(defn query-enums-for-attribute! [attribute]
  (common-controller/->Query {:query :enum/values
                              :args {:attribute attribute}
                              :result-event (partial ->SetEnumValues attribute)}))

(defn valid-enums-for
  "called along the lines of (valid-enums-for :document.category/project-doc ...)"
  [valid-for-criterion attribute]
  (into []
        (comp (if valid-for-criterion
                (do
                  (log/debug "valid check(2): filter " valid-for-criterion "vs attr" attribute "enum-vals" (map :db/ident  (@enum-values attribute)))
                  (filter #(= valid-for-criterion (:enum/valid-for %))))
                identity)
              (map :db/ident)) (@enum-values attribute)))

(defn with-enum-values
  "Call component with the values of the given enumeration.
  Automatically fetches enum values from database, if they haven't already been fetched."
  [{:keys [e! attribute]} _]
  (when-not (contains? @enum-values attribute)
    (log/debug "getting enum vals for attribute" attribute)
    (e! (query-enums-for-attribute! attribute)))
  (fn [{:keys [attribute]} component]
    (when-let [values (@enum-values attribute)]
      (if (vector? component)
        (conj component values)
        [component values]))))

(defn select-enum
  "Select an enum value based on attribute. Automatically fetches enum values from database."
  [{:keys [e! attribute required label-element sort-fn]}]
  (when-not (contains? @enum-values attribute)
    (log/debug "getting enum vals for attribute" attribute)
    (e! (query-enums-for-attribute! attribute)))
  (fn [{:keys [value on-change name show-label? show-empty-selection?
               tiny-select? id error container-class class values-filter
               full-value? empty-selection-label]
        :enum/keys [valid-for]
        :or {show-label? true
             show-empty-selection? true}}]
    (let [tr* #(tr [:enum %])
          value (if (and (map? value)
                         (contains? value :db/ident))
                  ;; If value is a enum ref pulled from db, extract the kw value
                  (:db/ident value)
                  value)
          select-comp (if tiny-select?
                        select-with-action
                        form-select)
          ;; values (valid-enums-for valid-for attribute)
          values (into []
                       (comp (if valid-for
                               (do
                                 (log/debug "valid check(1): filter " valid-for "vs attr" attribute "enum vals" (map :db/ident  (@enum-values attribute)))
                                 (filter #(= valid-for (:enum/valid-for %))))
                               identity)
                             (map :db/ident))
                       (@enum-values attribute))
          values (if values-filter
                   (filterv values-filter values)
                   ;; else
                   values)]
      [select-comp {:label (tr [:fields attribute])
                    :name name
                    :id id
                    :label-element label-element
                    :empty-selection-label empty-selection-label
                    :container-class container-class
                    :show-label? show-label?
                    :error (boolean error)
                    :value (or value :none)
                    :on-change (if full-value?
                                 ;; If full value is specified, use the enum map (which may have
                                 ;; other interesting attributes) as the value instead of just
                                 ;; the keyword.
                                 (fn [kw]
                                   (on-change (some #(when (= (:db/ident %) kw) %) (@enum-values attribute))))

                                 ;; Otherwise pass the kw as value as is
                                 on-change)
                    :show-empty-selection? show-empty-selection?
                    :items (sort-by (or sort-fn tr*) values)
                    :format-item tr*
                    :required required
                    :class class}])))

(def ^:private selectable-users (r/atom nil))

(defrecord SetSelectableUsers [users]
  t/Event
  (process-event [_ app]
    (reset! selectable-users users)
    app))

(defrecord CompleteUserResult [callback result]
  t/Event
  (process-event [_ app]
    (callback result)
    app))

(defrecord CompleteUser [search callback]
  t/Event
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :query
           :query :user/list
           :args {:search search}
           :result-event (partial ->CompleteUserResult callback)})))


(defn- format-user [{:user/keys [family-name person-id] :as user}]
  (if family-name
    (user-info/user-name user)
    (str person-id)))

(defn- user-select-popper []
  {:padding "0.3rem"
   :z-index 99})

(defn- user-select-entry [highlight?]
  ^{:pseudo {:hover {:background-color theme-colors/gray-lighter}}}
  {:padding "0.5rem"
   :cursor :pointer
   :background-color (if highlight?
                       theme-colors/gray-lighter
                       theme-colors/white)})

(defn select-user
  "Select user"
  [{:keys [e! value on-change label required error
           show-label?]
    :or {show-label? true}}]
  (r/with-let [state (r/atom {:loading? false
                              :users nil
                              :open? false
                              :input ""})
               input-ref (atom nil)]
    (let [{:keys [loading? users open? input highlight]} @state]
      [:<>
       [TextField {:ref #(reset! input-ref %)
                   :label label
                   :show-label? show-label?
                   :required required
                   :error error
                   :placeholder (tr [:user :autocomplete :placeholder])
                   :on-key-down (fn [e]
                                  (let [hl-idx (and (seq users) highlight
                                                    (cu/find-idx #(= highlight %) users))]
                                    (case (.-key e)
                                      ;; Move highlight down
                                      "ArrowDown"
                                      (when hl-idx
                                        (swap! state assoc
                                               :highlight (nth users
                                                               (if (< hl-idx (dec (count users)))
                                                                 (inc hl-idx)
                                                                 0)))
                                        (.preventDefault e))

                                      ;; Move highlight up
                                      "ArrowUp"
                                      (when hl-idx
                                        (swap! state assoc
                                               :highlight (nth users
                                                               (if (zero? hl-idx)
                                                                 (dec (count users))
                                                                 (dec hl-idx))))
                                        (.preventDefault e))

                                      "Enter"
                                      (when hl-idx
                                        (on-change highlight)
                                        (swap! state assoc :open? false)
                                        (.preventDefault e))

                                      "Escape"
                                      (when open?
                                        (swap! state assoc :open? false)
                                        (.stopPropagation e))

                                      nil)))
                   :on-blur #(js/setTimeout
                              ;; Delay closing because we might be blurring
                              ;; because user clicked one of the options
                              (fn [] (when open?
                                       (swap! state assoc :open? false)))
                              100)
                   :value (if value
                            (format-user value)
                            input)
                   :on-focus #(when (and (seq users) (>= (count input) 2))
                                (swap! state assoc :open? true))
                   :on-change (fn [e]
                                (let [t (-> e .-target .-value)
                                      loading? (>= (count t) 2)]
                                  (when value
                                    (on-change nil))

                                  (swap! state
                                         #(assoc %
                                            :input t
                                            :open? loading?
                                            :loading? loading?))

                                  (when loading?
                                    (e! (->CompleteUser t
                                                        (fn [users]
                                                          (swap! state assoc
                                                                 :loading? false
                                                                 :open? true
                                                                 :users users
                                                                 :highlight (first users))))))))
                   :input-button-click #(do
                                          (on-change nil)
                                          (swap! state assoc :input "")
                                          (r/after-render
                                            (fn []
                                              (.focus @input-ref))))
                   :input-button-icon icons/content-clear}]
       (when open?
         [Popper {:open true
                  :anchorEl @input-ref
                  :placement "bottom"
                  :modifiers #js {:hide #js {:enabled false}
                                  :preventOverflow #js {:enabled false}}

                  :style {:z-index 9999} ; Must have high z-index to use in modals
                  }
          [Paper  {:style {:width (.-clientWidth @input-ref)}
                   :class ["user-select-popper" (<class user-select-popper)]}
           (if loading?
             [CircularProgress {:size 20}]
             [:div.select-user-list
              (if (seq users)
                (mapc (fn [user]
                          [:div.select-user-entry
                           {:class [(if (= user highlight)
                                      "active"
                                      "inactive")
                                    (<class user-select-entry (= user highlight))]
                            :on-click #(do
                                         (swap! state assoc :open? false)
                                         (on-change user))}
                           (format-user user)])
                        users)
                [:span.select-user-no-results {:style {:padding "0.5rem"}} (tr [:user :autocomplete :no-options])])])]])])))

(defn radio [{:keys [value items format-item on-change]}]
  (let [item->value (zipmap items (map str (range)))]
    [FormControl {:component "fieldset"}
     [RadioGroup {:value (item->value value)
                  :on-change #(on-change (nth items (-> % .-target .-value js/parseInt)))}
      (util/with-keys
        (for [item items
              :let [value (item->value item)
                    label ((or format-item str) item)]]
          [FormControlLabel {:label label
                             :value value
                             :control (r/as-element [Radio {:value value}])}]))]]))

(defn checkbox [{:keys [value on-change label label-placement disabled] :or {label-placement :end
                                                                             disabled false}}]
  [FormControlLabel {:label label
                     :label-placement label-placement
                     :disabled (boolean disabled)
                     :control (r/as-element [Checkbox {:checked (boolean value)
                                                       :disabled (boolean disabled)
                                                       :on-change #(let [checked? (-> % .-target .-checked)]
                                                                     (on-change checked?))}])}])
