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
            [teet.ui.format :as format]
            [taoensso.timbre :as log]))

(def select-bg-caret-down "url('data:image/svg+xml;charset=US-ASCII,%3Csvg%20xmlns%3D%22http%3A%2F%2Fwww.w3.org%2F2000%2Fsvg%22%20width%3D%22292.4%22%20height%3D%22292.4%22%3E%3Cpath%20fill%3D%22%23005E87%22%20d%3D%22M287%2069.4a17.6%2017.6%200%200%200-13-5.4H18.4c-5%200-9.3%201.8-12.9%205.4A17.6%2017.6%200%200%200%200%2082.2c0%205%201.8%209.3%205.4%2012.9l128%20127.9c3.6%203.6%207.8%205.4%2012.8%205.4s9.2-1.8%2012.8-5.4L287%2095c3.5-3.5%205.4-7.8%205.4-12.8%200-5-1.9-9.2-5.5-12.8z%22%2F%3E%3C%2Fsvg%3E')")

(defn- primary-select-style
  [error]
  ^{:pseudo {:focus theme-colors/focus-style
             :invalid {:box-shadow :inherit
                       :outline :inherit}}}
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
   :font-size "1rem"
   :background-image select-bg-caret-down
   :background-repeat "no-repeat"
   :background-position "right .7em top 50%"
   :background-size "0.65rem auto"})

(defn- select-label-style
  []
  {:font-size "1rem"})

(defn form-select [{:keys [label name id items on-change value format-item
                               show-empty-selection? error error-text required]
                        :or {format-item :label}}]
  (let [option-idx (zipmap items (range))
        change-value (fn [e]
                       (let [val (-> e .-target .-value)]
                         (if (= val "")
                           (on-change nil)
                           (on-change (nth items (int val))))))]
    [:label {:for id
             :class (<class select-label-style)}
     [:span label (when required
                    [common/required-astrix])]
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
         [:option {:value ""}])
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
           required? show-empty-selection?
           container-class select-class]
    :or {format-item :label}}]
  (let [option-idx (zipmap items (range))
        change-value (fn [e]
                       (let [val (-> e .-target .-value)]
                         (if (= val "")
                           (on-change nil)
                           (on-change (nth items (int val))))))]
    [:div {:class container-class}
     [:label {:html-for id}
      (str label ":")
      [:select
       {:value (or (option-idx value) "")
        :name name
        :required (boolean required?)
        :id id
        :on-change (fn [e]
                     (change-value e))
        :class (herb/join (<class select-with-action-styles)
                          (when select-class
                            select-class))}
       (when show-empty-selection?
         [:option {:value ""
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

(defn select-enum
  "Select an enum value based on attribute. Automatically fetches enum values from database."
  [{:keys [e! attribute required tiny-select? show-label?]
    :or {show-label? true}}]
  (when-not (contains? @enum-values attribute)
    (log/debug "getting enum vals for attribute" attribute)
    (e! (query-enums-for-attribute! attribute)))
  (fn [{:keys [value on-change name id error container-class class values-filter]
        :enum/keys [valid-for]}]
    (let [tr* #(tr [:enum %])
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
                    :container-class container-class
                    :show-label? show-label?
                    :error (boolean error)
                    :value (or value :none)
                    :on-change on-change
                    :show-empty-selection? true
                    :items (sort-by tr* values)
                    :format-item tr*
                    :required required
                    :class class}])))

(def ^:private selectable-users (r/atom nil))

(defrecord SetSelectableUsers [users]
  t/Event
  (process-event [_ app]
    (reset! selectable-users users)
    app))

(defn select-user
  "Select user"
  [{:keys [e! value on-change label required error
           extra-selection extra-selection-label]}]
  (when (nil? @selectable-users)
    (e! (common-controller/->Query {:query :user/list
                                    :args {}
                                    :result-event ->SetSelectableUsers})))
  [form-select (merge
                {:label label
                 :value value
                 :error error
                 :required required
                 :on-change on-change
                 :show-empty-selection? true}
                (if extra-selection
                  {:items (conj @selectable-users extra-selection)
                   :format-item (fn [user]
                                  (if (= user extra-selection)
                                    extra-selection-label
                                    (user-info/user-name-and-email user)))}
                  {:items @selectable-users
                   :format-item user-info/user-name-and-email}))])
;;
;; Status
;;
(defn- status-container-style
  []
  {:display :flex
   :flex-direction :row
   :align-items :center
   :border-bottom "solid 1px"
   :border-color theme-colors/gray-light
   :padding-bottom "1rem"
   :margin-bottom "1rem"})

(defn status
  [{:keys [e! status attribute modified on-change]}]
  [:div {:class (<class status-container-style)}
   [select-enum {:e!                     e!
                 :on-change       on-change
                 :value           status
                 :tiny-select?    true
                 :attribute       attribute}]
   [common/labeled-data {:label (tr [:common :last-modified])
                         :data  (or (format/date modified)
                                    "-")}]])
