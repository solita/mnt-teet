(ns teet.admin.indexes-view
  "Indexes admin user interface"
  (:require [teet.ui.material-ui :refer [Table TableHead TableBody TableRow TableCell Paper Collapse]]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.buttons :as buttons]
            [herb.core :refer [<class]]
            [clojure.string :as str]
            [teet.ui.icons :as icons]
            [teet.localization :refer [tr]]
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
            [cljs-time.coerce :as tc]))

(defn- by-month [yyyy mm]
  (iterate #(t/plus % (t/months 1))
           (t/date-time yyyy mm)))

(def enabled-indexes
  #{
    {:name "Bitumen index"
     :id "bitumen"
     :type :cost-index.type/bitumen-index}
    {
     :name "Consumer Price index"
     :id "consumer"
     :type :cost-index.type/consumer-price-index
     }
    })
(defn- is-valid-index-id?
  "Checks if id is defined in the 'enabled-indexes' variable and returns the index"
  [index-id]
  (filterv (fn [x] (if (= (:id x) index-id) x nil)) enabled-indexes)
  )

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
     [select/form-select {:format-item #(if % (name %) (str "- select -"))
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
  [e! route id]
  [:div {:style {:min-width "300px"
                 :margin-left "2rem"
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
    (if route
      (mapc (fn [x] [:div [url/Link {:page :admin-index-page
                                     :params {:id (:db/id x)}}
                           (if (= id (str (:db/id x)))
                                  [:b (:cost-index/name x)]
                                  (:cost-index/name x))]]) route)
      (str "No indexes added"))]])

(defn view-index-values
  ""
  [index]
  [:<>])

(defn edit-index-form
  [e! form-data index values]
  [form/form2 {:e! e!
               :on-change-event admin-controller/->UpdateIndexValues
               :save-event admin-controller/->SaveIndexValues
               :cancel-event admin-controller/->CancelIndexValues
               :value form-data}
   [:div
    (str (str (take
                (t/in-months
                  (t/interval
                    (t/date-time 2019 10 1)
                    (t/now)))
                (by-month 2019 10))))
    [:div {:class (<class common-styles/flex-align-end)}
    [index-form-cancel-button*]
    [index-save-form-button*]]]]
  )

(defn view-index-info
  ""
  [e! admin index editmode?]
    (let [_ (taoensso.timbre/debug "VI " index editmode?)
          values (:cost-index/values index)]
     [:div
      [typography/Heading1
            (str (:cost-index/name index))]
      [:div [:b "Index type "] (:db/ident (:cost-index/type index))]
      [:div {:class (<class common-styles/margin-bottom 2)}
       [:b "Index valid from "] (format/date (:cost-index/valid-from index))]
  (if editmode?
    [:div [edit-index-form e! (:edit-index-values admin) index values]]
    [:div
     (if (seq values)
       [view-index-values e! index values]
       (str "No values entered."))
     [buttons/button-primary {:id (str "admin-addindexvalues")
                              :on-click (e! admin-controller/->EditIndexValues)}
      (tr [:buttons :edit-index-values])]])]))

(defn indexes-content
  [e! admin route id]
  (let [add-form (:add-index admin)
        _ (taoensso.timbre/debug "ID " id)]
    [:<>
     (when add-form
      [add-index-form e! {:form-value add-form
                          :on-change-event admin-controller/->UpdateIndexForm
                          :save-event admin-controller/->SaveIndex
                          :cancel-event admin-controller/->CancelIndex}])
    (when id
      [view-index-info e! admin (first
                            (filterv
                              #(when (= id (str (:db/id %))) %)
                              route))
       (some? (:edit-index-values admin))])]))

(defn indexes-page
  "The main indexes admin page"
  [e! {admin :admin
       params :params} route]
  (let [id (:id params)]
   [:div {:class (<class common-styles/flex-align-end)}
   [query/debounce-query
    {:e! e!
     :query :admin/indexes-data
     :args {:payload {}
            :refresh (:admin-refresh route)}
     :simple-view [indexes-selection e! route id]}
    500]
   [indexes-content e! admin route id]])
  )
