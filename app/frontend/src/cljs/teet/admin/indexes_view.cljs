(ns teet.admin.indexes-view
  "Indexes admin user interface"
  (:require [teet.ui.material-ui :refer [Table TableHead TableBody TableRow TableCell Paper Collapse]]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.buttons :as buttons]
            [herb.core :refer [<class]]
            [clojure.string :as str]
            [teet.ui.icons :as icons]
            [teet.localization :refer [tr tr-enum]]
            [teet.ui.form :as form]
            [teet.ui.format :as format]
            [teet.ui.typography :as typography]
            teet.user.user-spec
            [teet.ui.select :as select]
            [teet.ui.util :refer [mapc]]
            [teet.ui.query :as query]
            [reagent.core :as r]
            [teet.theme.theme-colors :as theme-colors]
            [teet.common.common-styles :as common-styles]
            [teet.ui.common :as common-ui]
            [teet.ui.context :as context]
            [teet.user.user-model :as user-model]
            [teet.util.date :as date]
            [teet.ui.date-picker :as date-picker]
            [teet.admin.admin-controller :as admin-controller]
            [teet.ui.url :as url]
            [cljs-time.core :as t]
            [teet.util.coerce :as uc]))

(defn- by-month [start-date]
  (iterate #(t/plus % (t/months 1)) start-date))

(def enabled-indexes
  #{{:name "Bitumen index"
     :id "bitumen"
     :type :cost-index.type/bitumen-index
     :unit "€"}
    {:name "Consumer Price index"
     :id "consumer"
     :type :cost-index.type/consumer-price-index
     :unit "p"}})

(defn- is-valid-index-id?
  "Checks if id is defined in the 'enabled-indexes' variable and returns the index"
  [index-id]
  (filterv (fn [x] (if (= (:id x) index-id) x nil)) enabled-indexes))

(defn index-save-form-button
  [invalid-attributes {:keys [validate]}]
  [:div {:class (<class common-styles/margin-left 1)}
   [buttons/button-primary
    {:on-click validate
     :type :submit
     :disabled (boolean (seq invalid-attributes))}
    [:span (tr [:buttons :save])]]])

(defn index-save-form-button*
  []
  (context/consume
    :form
    (fn [{:keys [invalid-attributes value footer]}]
      [index-save-form-button @invalid-attributes value (update footer :validate
                                                               (fn [validate]
                                                                 (when validate
                                                                   #(validate value))))])))

(defn index-form-cancel-button*
  []
  (context/consume
    :form
    (fn [{:keys [footer]}]
      [buttons/button-secondary {:on-click (:cancel footer)}
       (tr [:buttons :cancel])])))

(defn- delete-index-button
  [e! index]
  [buttons/button-with-confirm
   {:action (e! admin-controller/->DeleteIndex (:db/id index))
    :modal-title (tr [:index-admin :delete-title])
    :modal-text (tr [:index-admin :delete-text])
    :confirm-button-text (tr [:buttons :delete])
    :cancel-button-text (tr [:buttons :cancel])
    :close-on-action? true}
   [buttons/button-warning {:id "delete-index-btn"
                            :class (<class common-styles/margin-right 2)
                            :onClick (e! admin-controller/->DeleteIndex (:db/id index))} (tr [:buttons :delete])]])

(defn- index-form-fields
  []
  [:div {:style {:flex 1
                 :padding "0 1rem"
                 :min-width "300px"}}
   [:div {:style {:flex 1
                 :padding "0 1rem"}}
   [typography/Heading3 {:style {:margin-bottom "1rem"}}
    (tr [:admin :add-index])]
   [:div {:style {:margin-bottom "1rem"}}
    [form/field :cost-index/name
     [TextField {}]]]
   [:div {:style {:margin-bottom "1rem"}}
    [form/field :cost-index/type
     [select/form-select {:format-item #(if % (tr-enum %) (tr [:common :select :empty]))
                          :items (into [nil] (mapv #(:type %) enabled-indexes))}]]]
    [:div {:style {:margin-bottom "1rem"}}
     [form/field :cost-index/valid-from
      [date-picker/date-input {}]]]]
  [:div {:class (<class common-styles/flex-align-end)}
   [index-form-cancel-button*]
   [index-save-form-button*]]])

(defn- add-index-form [e! {:keys [form-value on-change-event save-event cancel-event]}]
  [:div {:class [(<class common-styles/margin-bottom 1)
                 (<class common-styles/flex-row-end)]}
   [form/form2 {:e! e!
                :on-change-event on-change-event
                :save-event save-event
                :cancel-event cancel-event
                :value form-value
                :spec :admin/add-index}
    [index-form-fields]]])

(defn indexes-selection
  [e! page-state id]
  [:div
   (let [index-data (:index-data page-state)]
    [:div {:style {:min-width "300px"
                   :margin-left "2rem"
                   :margin-right "2rem"
                   :background-color theme-colors/gray-lightest
                   :color theme-colors/black-coral
                   :vertical-align :top}}
   [:div [typography/Heading1 (str "Indexes")]]
   [:div {:class (<class common-styles/margin 2 0 2 0)}
    [buttons/button-secondary {:size :small
                               :id (str "admin-addindex")
                               :start-icon (r/as-element [icons/content-add])
                               :on-click (e! admin-controller/->AddIndex)}
     (tr [:buttons :add-index])]]
   [:div
    (if index-data
      (doall (for [index-group (distinct (mapv (fn [x] (:cost-index/type x)) index-data))]
               ^{:key (str index-group)}
               [:<>
                [:div {:class [(<class common-styles/margin 1 0 0 0)]} [:b (tr-enum index-group)]]
                (mapc (fn [x] [:div [url/Link {:page :admin-index-page
                                              :params {:id (:db/id x)}}
                                    (if (= id (str (:db/id x)))
                                      [:b (:cost-index/name x)]
                                      (:cost-index/name x))]])
                      (sort :cost-index/valid-from (filterv
                                               (fn [y] (= index-group (:cost-index/type y)))
                                               index-data)))]))
      (tr [:indexes-admin :no-indexes-added]))]])])

(defn view-index-values
  [values]
  [:div {:class (<class common-styles/margin-bottom 2)}
   (mapc (fn [val]
           [:div {:class [(<class common-styles/flex-align-end)
                          (<class common-styles/flex-align-center)]}
            [:div {:style {:min-width "250px"}}
             (str (:index-value/year val) " " (tr [:calendar :months (dec (:index-value/month val))]))]
            [:div {:class (<class common-styles/margin-left 2) :style {:align-items :right}}
                (str (uc/->double (:index-value/value val)))]]) values)])

(defn edit-index-values
  [e! form-data index values]
  (let
    [index-start (t/date-time (:cost-index/valid-from index))
     month-objects (take
                     (t/in-months
                       (t/interval
                         index-start
                         (t/now)))
                     (by-month index-start))
     last-month (t/minus (t/now) (t/months 1))]
   (mapv (fn [x]
          (let [editable? (or
                    (nil? values)
                    (and
                      values
                      (and
                        (= (t/month x) (t/month last-month))
                        (= (t/year x) (t/year last-month)))))
        month-val (filterv (fn [y] (and
                                     (= (:index-value/year y) (t/year x))
                                     (= (:index-value/month y) (t/month x)))) values)
        field-value (when month-val (uc/->double (:index-value/value (first month-val))))]
   (when editable?
     (e! (admin-controller/->UpdateIndexValues
           {(keyword (str "index-value-" (t/year x) "-" (t/month x))) field-value}))))) month-objects))
  (fn [e! form-data index values]
    (let [index-start (t/date-time (:cost-index/valid-from index))
          month-objects (take
                        (t/in-months
                          (t/interval
                            index-start
                            (t/now)))
                        (by-month index-start))
        last-month (t/minus (t/now) (t/months 1))]
    [form/form2 {:e! e!
                 :on-change-event admin-controller/->UpdateIndexValues
                 :save-event admin-controller/->SaveIndexValues
               :cancel-event admin-controller/->CancelIndexValues
               :value form-data}
   [:div
    (mapc (fn [x]
            (let [editable? (or
                              (nil? values)
                              (and
                                values
                                (and
                                  (= (t/month x) (t/month last-month))
                                  (= (t/year x) (t/year last-month)))))
                  month-val (filterv (fn [y] (and
                                              (= (:index-value/year y) (t/year x))
                                              (= (:index-value/month y) (t/month x)))) values)
                  field-value (when month-val (uc/->double (:index-value/value (first month-val))))]
              [:div {:class [(<class common-styles/flex-align-end)
                           (<class common-styles/flex-align-center)]}
             [:div {:style {:min-width "200px"}}
              (str (t/year x) " " (tr [:calendar :months (dec (t/month x))]))]
             [:div {:class (<class common-styles/margin-left 2)}
              (if editable?
                [:<>
                 [form/field (keyword (str "index-value-" (t/year x) "-" (t/month x)))
                  [TextField {:e e!
                              :value field-value
                              :type :number
                              :step ".01"
                              :required true
                              :hide-label? true
                              :id (str "index-value-" (t/year x) "-" (t/month x))}]]]
                 (str field-value))]])) month-objects)
    [:div {:class[(<class common-styles/flex-align-end)
                  (<class common-styles/margin 2 0 0 0)]}
    [index-form-cancel-button*]
    [index-save-form-button*]]]])))

(defn edit-index-form
  [e! index form-data]
  (let [indexname (:cost-index/name index)]
    (e! (admin-controller/->UpdateEditIndexForm
          {(keyword (str "cost-index/name")) indexname})))
  (fn [e! index form-data]
    [form/form2
     {:e! e!
      :on-change-event admin-controller/->UpdateEditIndexForm
      :save-event admin-controller/->EditIndex
      :cancel-event admin-controller/->CancelEditIndex
      :value form-data}
     [:div {:class (<class common-styles/flex-row-center)}
      [form/field :cost-index/name
       [TextField {:e e!
                   :value (:cost-index/name index)
                   :id (str "index-name-field")}]]]
     [:div {:class [(<class common-styles/flex-align-center)]}
      [delete-index-button e! index]
      [index-form-cancel-button*]
      [index-save-form-button*]]]))

(defn view-index-info
  [e! page-state index edit? edit-values?]
    (let [values (:cost-index/values index)
          index-valid (t/date-time (:cost-index/valid-from index))]
     [:div
      [:div {:class [(<class common-styles/margin-bottom 2)
                     (<class common-styles/flex-align-center)]}
       [:div {:style {:min-width "600px"}}
        (if edit?
          [edit-index-form e! index (:edit-index page-state)]
          [typography/Heading1
            (str (:cost-index/name index))])]

       (when (not (or edit? edit-values?))
         [buttons/button-secondary {:id (str "admin-editindex")
                                    :on-click (e! admin-controller/->EditIndexForm)}
          (tr [:buttons :edit-index])])]
      (when (not edit?)
        [:<>
         [:div {:class (<class common-styles/flex-row)}
          [:div {:style {:min-width "250px"}}
           [:b (tr [:fields :cost-index/type])]]
          (tr-enum (:cost-index/type index))]
         [:div {:class [(<class common-styles/flex-row)
                        (<class common-styles/margin-bottom 2)]}
          [:div {:style {:min-width "250px"}}
           [:b (tr [:fields :cost-index/valid-from])]] (str (tr [:calendar :months (dec (t/month index-valid))])
                                                            " "
                                                            (t/year index-valid))]])
  (if edit-values?
    [:div [edit-index-values e! (:edit-index-values page-state) index values]]
    (when-not edit?
      [:div
       (if (seq values)
         [view-index-values values]
         [:div {:class [(<class common-styles/flex-row)
                        (<class common-styles/margin-bottom 2)]}
          (tr [:indexes-admin :no-values-entered])])
       [buttons/button-primary {:id (str "admin-editindexvalues")
                                :on-click (e! admin-controller/->EditIndexValues)}
      (tr [:buttons :edit-index-values])]]))]))

(defn indexes-content
  [e! query page-state id]
  (let [add-form (or (:add-index page-state) (:add-index-form query))
        index-data (:index-data page-state)]
    [:<>
     (when add-form
      [add-index-form e! {:form-value add-form
                          :on-change-event admin-controller/->UpdateAddIndexForm
                          :save-event admin-controller/->SaveIndex
                          :cancel-event admin-controller/->CancelIndex}])
    (when id
      [view-index-info e! page-state (first
                            (filterv
                              #(when (= id (str (:db/id %))) %)
                              index-data))
       (some? (:edit-index page-state))
       (some? (:edit-index-values page-state))])]))

(defn indexes-page
  "The main indexes admin page"
  [e! {params :params
       query :query} page-state]
  (let [id (:id params)]
    [:<>
     [:div {:class (<class common-styles/flex-row-w100-space-between-center)}
      [teet.admin.admin-view/admin-heading-menu]]
     [:div {:class (<class common-styles/flex-align-end)}
      [indexes-selection e! page-state id]
      [indexes-content e! query page-state id]]]))
