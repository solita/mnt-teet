(ns teet.project.land-view
  (:require [teet.ui.typography :as typography]
            [herb.core :refer [<class]]
            [teet.common.common-styles :as common-styles]
            [teet.ui.buttons :as buttons]
            [teet.localization :refer [tr tr-enum]]
            [teet.ui.util :refer [mapc]]
            [reagent.core :as r]
            [teet.ui.icons :as icons]
            [teet.ui.material-ui :refer [ButtonBase Collapse LinearProgress Grid]]
            [teet.ui.text-field :refer [TextField] :as text-field]
            [teet.project.land-controller :as land-controller]
            [teet.theme.theme-colors :as theme-colors]
            [garden.color :refer [darken]]
            [teet.ui.url :as url]
            [teet.land.land-specs]
            [teet.project.project-controller :as project-controller]
            [teet.ui.form :as form]
            [teet.common.common-controller :as common-controller]
            [teet.ui.select :as select]
            [teet.log :as log]))

(defn cadastral-unit-style
  [selected?]
  (let [bg-color (if selected?
                   theme-colors/gray-lighter
                   theme-colors/gray-lightest)]
    ^{:pseudo {:hover {:background-color (darken bg-color 10)}}}
    {:flex 1
     :padding "0.5rem"
     :display :flex
     :flex-direction :column
     :transition "background-color 0.2s ease-in-out"
     :align-items :normal
     :background-color bg-color
     :border-bottom (str "1px solid " theme-colors/gray-lighter)}))

(defn impact-form-style
  []
  {:background-color theme-colors/gray-lighter
   :padding "1.5rem"})

(defn impact-form-footer
  [{:keys [validate disabled? cancel]}]
  [:div {:style {:display :flex
                 :flex-direction :row
                 :justify-content :flex-end
                 :background-color theme-colors/gray-lighter
                 :padding "0 1.5rem 1.5rem 0"}}
   [buttons/button-secondary {:style {:margin-right "1rem"}
                              :on-click cancel
                              :size :small}
    (tr [:buttons :cancel])]
   [buttons/button-primary {:disabled disabled?
                            :type :submit
                            :size :small
                            :on-click validate}
    (tr [:buttons :save])]])

(defn estate-compensation-form
  []
  {:background-color :inherit
   :padding "1.5rem"})

(comment
  ;; FIXME: app state hierarchy
  {:route
   {:project
    {:land/forms
     {#{"own1" "own2"} ;; set of owners as key
      {:land/owner-compensation-form {:some-owner-comp-keys "value"}

       ;; map containing all estate forms for this owner set
       :land/estate-forms
       {;; estate id and form map
         "123123" {:estate-procedure/motivation-bonus 420}}

       :land/cadastral-forms
       ;; plot number and form maps
       {"44422" {:land-purchase/decision :land-purchase.decision/not-needed}}}}}}})

(defn field-with-title
  [{:keys [title field-name field-type end-icon placeholder]}]
  [:<>
   [typography/BoldGreyText title]
   [Grid {:container true :spacing 3}
    [Grid {:item true :xs 8}
     [TextField {:read-only? true :value title}]]
    [Grid {:item true :xs 4}
     [form/field field-name
      [TextField (merge {:type field-type
                         :placeholder placeholder
                         :hide-label? true}
                        (when end-icon
                          {:end-icon end-icon}))]]]]])

(defn- owner-process-fee-field [{:keys [on-change value]}]
  (let [{:keys [owner recipient]} value]
    (if (nil? owner)
      [TextField {:on-change #(on-change (assoc value :recipient (-> % .-target .-value)))
                  :value recipient}]
      [TextField {:read-only? true :value recipient}])))

(defn estate-group-form
  [e! _ on-change form-data]
  (e! (on-change (-> form-data
                     (update :estate-procedure/compensations
                             #(if (empty? %) [{}] %))
                     (update :estate-procedure/third-party-compensations
                             #(if (empty? %) [{}] %))
                     (update :estate-procedure/land-exchanges
                             #(if (empty? %) [{}] %)))))
  (fn [e! {:keys [estate-id] :as estate} on-change form-data]
    (let [procedure-type (:estate-procedure/type form-data)
          add-row! (fn [field]
                     (e! (on-change
                          (update form-data
                                  field
                                  (fnil conj []) {}))))]
      [:div
       [form/form2 {:e! e!
                    :value form-data
                    :on-change-event on-change
                    :save-event #(land-controller/->SubmitEstateCompensationForm form-data estate-id)
                    :cancel-event #(land-controller/->ToggleOpenEstate estate-id)}
        [:div {:class (<class estate-compensation-form)}
         [:div
          [form/field :estate-procedure/pos
           [TextField {:type :number
                       :label-element typography/BoldGreyText}]]
          [form/field :estate-procedure/type
           [select/select-enum {:e! e!
                                :label-element typography/BoldGreyText
                                :attribute :estate-procedure/type}]]
          (when (= procedure-type :estate-procedure.type/acquisition-negotiation)
            [form/many {:attribute :estate-procedure/process-fees
                        :before [typography/BoldGreyText (tr [:fields :estate-procedure/process-fees])]
                        :after [buttons/link-button
                                {:on-click #(add-row! :estate-procedure/process-fees)}
                                "+ add owner"]}
             [Grid {:container true :spacing 3}
              [Grid {:item true :xs 8}
               [form/field :process-fee-recipient
                [owner-process-fee-field {}]]]
              [Grid {:item true :xs 4}
               [form/field :estate-process-fee/fee
                [TextField {:type :number :placeholder "0"
                            :hide-label? true
                            :min 0
                            :end-icon text-field/euro-end-icon}]]]]])
          (when (#{:estate-procedure.type/urgent :estate-procedure.type/acquisition-negotiation}
                 procedure-type)
            [field-with-title {:title "Motivation bonus"
                               :field-name :estate-procedure/motivation-bonus
                               :type :number
                               :placeholder 0
                               :end-icon text-field/euro-end-icon}])

          (when (#{:estate-procedure.type/urgent}
                 procedure-type)
            [field-with-title {:title "Urgent procedure bonus"
                               :field-name :estate-procedure/urgent-bonus
                               :type :number
                               :placeholder 0
                               :end-icon text-field/euro-end-icon}])

          (when (#{:estate-procedure.type/acquisition-negotiation :estate-procedure.type/expropriation} procedure-type)
            [form/many {:attribute :estate-procedure/compensations
                        :before [typography/BoldGreyText (tr [:fields :estate-procedure/compensations])]
                        :after [buttons/link-button
                                {:on-click #(add-row! :estate-procedure/compensations)}
                                "+ add compensation"]}
             [Grid {:container true :spacing 3}
              [Grid {:item true :xs 8}
               [form/field {:attribute :estate-compensation/reason}
                [select/select-enum {:e! e!
                                     :show-label? false
                                     :attribute :estate-compensation/reason}]]]
              [Grid {:item true :xs 4}
               [form/field {:attribute :estate-compensation/amount}
                [TextField {:hide-label? true
                            :placeholder 0
                            :end-icon text-field/euro-end-icon
                            :type :number}]]]]])

          [form/many {:attribute :estate-procedure/third-party-compensations
                      :before [typography/BoldGreyText (tr [:fields :estate-procedure/third-party-compensations])]
                      :after [buttons/link-button
                              {:on-click #(add-row! :estate-procedure/third-party-compensations)}
                              "+ add compensation"]}
           [Grid {:container true :spacing 3}
            [Grid {:item true :xs 8}
             [form/field {:attribute :estate-compensation/description}
              [TextField {:hide-label? true}]]]
            [Grid {:item true :xs 4}
             [form/field {:attribute :estate-compensation/amount}
              [TextField {:type :number
                          :placeholder 0
                          :end-icon text-field/euro-end-icon
                          :hide-label? true}]]]
            #_[Grid {:item true :xs 2
                     :style {:display :flex
                             :justify-content :center
                             :align-items :center}}
               [form/many-remove #(e! (on-change
                                       (update form-data
                                               :estate-procedure/third-party-compensations
                                               (fn [items]
                                                 (into (subvec items 0 %)
                                                       (subvec items (inc %)))))))
                [buttons/link-button {} "X"]]]]]

          (when (= (:estate-procedure/type form-data) :estate-procedure.type/property-trading)
            [:div
             [form/many {:before [typography/BoldGreyText (tr [:fields :estate-procedure/land-exchanges])]
                         :attribute :estate-procedure/land-exchanges}
              [Grid {:container true :spacing 3}
               [Grid {:item true :xs 12}
                [form/field {:attribute :land-exchange/cadastral-unit-id}
                 [TextField {:hide-label? true
                             :placeholder "Cadastral unit number"}]]]
               [Grid {:item true :xs 6}
                [form/field {:attribute :land-exchange/area}
                 [TextField {:label-element typography/BoldGreyText
                             :type :number
                             :placeholder 0
                             :end-icon text-field/sqm-end-icon}]]]
               [Grid {:item true :xs 6}
                [form/field {:attribute :land-exchange/price-per-sqm}
                 [TextField {:type :number
                             :placeholder 0
                             :end-icon text-field/euro-end-icon
                             :label-element typography/BoldGreyText}]]]]]])]
         (form/footer2 form/form-footer)]]])))


(defn cadastral-unit-form
  [e! {:keys [TUNNUS PINDALA] :as unit} quality on-change-event form-data]
  (let [show-extra-fields? (= (:land-acquisition/impact form-data) :land-acquisition.impact/purchase-needed)]
    [:div
     [form/form {:e! e!
                 :value form-data ;@impact-form
                 :on-change-event (fn [form-data]
                                    (on-change-event TUNNUS form-data)) ; update-impact-form
                 :save-event #(land-controller/->SubmitLandPurchaseForm form-data (:teet-id unit))
                 :spec :land-acquisition/form
                 :cancel-event #(land-controller/->ToggleLandUnit unit)
                 :footer impact-form-footer
                 :class (<class impact-form-style)}
      ^{:attribute :land-acquisition/impact}
      [select/select-enum {:e! e!
                           :attribute :land-acquisition/impact
                           :show-empty-selection? false}]
      (when show-extra-fields?
        ^{:attribute :land-acquisition/pos-number :xs 6}
        [TextField {:type :number}])
      (when show-extra-fields?
        ^{:attribute :land-acquisition/area-to-obtain
          :adornment (let [area (:land-acquisition/area-to-obtain form-data)]
                       [:div
                        [:p (tr [:land :total-area] {:area PINDALA})
                         (case quality
                           :bad [:span {:style {:color theme-colors/red}} " !!! " (tr [:land :unreliable])]
                           :questionable [:span {:style {:color theme-colors/orange}} " ! " (tr [:land :unreliable])]
                           nil)]
                        [:span (tr [:land :net-area-balance] {:area (- PINDALA area)})]])}
        [TextField {:type :number :input-style {:width "50%"}}])]]))

(defn acquisition-impact-status
  [impact]
  (let [impact (if impact
                 impact
                 :land-acquisition.impact/undecided)
        color (case impact
                :land-acquisition.impact/purchase-needed
                theme-colors/red
                :land-acquisition.impact/purchase-not-needed
                theme-colors/green
                :land-acquisition.impact/undecided
                theme-colors/gray-lighter
                theme-colors/gray-lighter)]
    [:div {:class (<class common-styles/flex-align-center)}
     [:div {:class (<class common-styles/status-circle-style color)
            :title impact}]
     [:span {:style {:text-align :left}
             :class (<class common-styles/gray-text)}
      (tr-enum impact)]]))

(defn cadastral-unit-container-style
  []
  ^{:pseudo {:first-of-type {:border-top "1px solid white"}}}
  {:border-left "1px solid white"
   :display :flex
   :position :relative
   :flex 1
   :flex-direction :column})

(defn cadastral-unit-quality-style
  [quality]
  {:position :absolute
   :left "-15px"
   :top "50%"
   :transform "translateY(-50%)"
   :color (if (= quality :bad)
            theme-colors/red
            theme-colors/orange)})


(defn cadastral-unit
  [e! update-cadastral-form-event cadastral-forms {:keys [TUNNUS KINNISTU MOOTVIIS MUUDET quality selected?] :as unit}]
  ^{:key (str TUNNUS)} ;;Arbitrary date before which data quality is bad]
  [:div {:class (<class cadastral-unit-container-style)}
   [:div {:class (<class cadastral-unit-quality-style quality)}
    [:span {:title (str MOOTVIIS " – " MUUDET)} (case quality
                                                  :bad "!!!"
                                                  :questionable "!"
                                                  "")]]
   [ButtonBase {:on-mouse-enter (e! project-controller/->FeatureMouseOvers "geojson_features_by_id" true unit)
                :on-mouse-leave (e! project-controller/->FeatureMouseOvers "geojson_features_by_id" false unit)
                :on-click (e! land-controller/->ToggleLandUnit unit)
                :class (<class cadastral-unit-style selected?)}
    [typography/SectionHeading {:style {:text-align :left}} (:L_AADRESS unit)]
    [:div {:class (<class common-styles/space-between-center)}
     [acquisition-impact-status (get-in unit [:land-acquisition :land-acquisition/impact])]
     [:span {:class (<class common-styles/gray-text)}
        TUNNUS]]]
   [Collapse
    {:in selected?
     :mount-on-enter true}
    [cadastral-unit-form e! unit quality
     update-cadastral-form-event
     (get cadastral-forms TUNNUS)]]])

(defn cadastral-heading-container-style
  [bg-color font-color]
  ^{:pseudo {:before {:content "''"
                      :width 0
                      :height 0
                      :border-bottom "15px solid transparent"
                      :border-left (str "15px solid " bg-color)
                      :position :absolute
                      :bottom "-15px"
                      :transform "rotate(90deg)"
                      :left 0}}}
  {:background-color bg-color
   :position :relative
   :color font-color})

(defn estate-group-style
  []
  ^{:pseudo {:first-of-type {:border-top "1px solid white"}}}
  {:border-left "1px solid white"})

(defn plot-group-container
  []
  {:display :flex
   :flex-direction :column
   :margin-left "15px"})

(defn group-style
  []
  {:width "100%"
   :justify-content :space-between
   :flex-direction :column
   :align-items :flex-start
   :padding "0.5rem"})

(defn estate-group
  [e! open-estates owner-set cadastral-forms estate-forms [estate-id units]]
  ^{:key (str estate-id)}
  [:div {:class (<class estate-group-style)}
   (let [estate (:estate (first units))]
     [:div.heading {:class (<class cadastral-heading-container-style theme-colors/gray-lighter :inherit)}
      [ButtonBase {:class (<class group-style)
                   :on-click (e! land-controller/->ToggleOpenEstate estate-id)}

       [typography/SectionHeading "Estate " estate-id]
       [:span (count units) " " (if (= 1 (count units))
                                  (tr [:land :unit])
                                  (tr [:land :units]))]]
      [Collapse
       {:in (boolean (open-estates estate-id))
        :mount-on-enter true}
       [estate-group-form e! estate
        (r/partial land-controller/->UpdateEstateForm owner-set estate) (get estate-forms estate-id)]]])
   [:div {:class (<class plot-group-container)}
    (mapc
     (r/partial cadastral-unit e!
                (r/partial land-controller/->UpdateCadastralForm owner-set)
                cadastral-forms)
      units)]])

(defn owner-group
  [e! open-estates owner-compensation-form cadastral-forms estate-forms [owner units]]
  ^{:key (str owner)}
  [:div {:style {:margin-bottom "2rem"}}
   (let [owners (get-in (first units) [:estate :omandiosad])]
     [:div.heading {:class (<class cadastral-heading-container-style theme-colors/gray theme-colors/white)}
      [:div {:class (<class group-style)}
       [typography/SectionHeading (if (not= (count owners) 1)
                                    (str (count owners) " owners")
                                    (:nimi (first owners)))]
       [:span (count units) " " (if (= 1 (count units))
                                  (tr [:land :unit])
                                  (tr [:land :units]))]]])
   [:div {:class (<class plot-group-container)}
    (mapc
     (fn [unit-group]
       [estate-group e! open-estates owner cadastral-forms estate-forms unit-group])
     (group-by
      (fn [unit]
        (get-in unit [:estate :estate-id]))
      units))]])

(defn filter-units
  [e! {:keys [estate-search-value quality impact-search-value] :as filter-params}]
  ;; filter-params will come from appdb :land-acquisition-filters key
  ;; (println "filter-params:" filter-params)
  (r/with-let [on-change (fn [kw-or-e field]
                           (println "on-change got" kw-or-e)
                           ;; select-enum gives us the kw straight, textfields pass event
                           (let [value (if (keyword? kw-or-e)
                                           kw-or-e
                                           (-> kw-or-e .-target .-value))]
                             (e! (land-controller/->SearchOnChange field value))))]
    [:div {:style {:margin-bottom "1rem"}}
     [TextField {:label (tr [:land :estate-filter-label])
                 :value estate-search-value
                 :on-change #(on-change % :estate-search-value)}]
     [TextField {:label (tr [:land :owner-filter-label])
                 :value (:owner-search-value filter-params)
                 :on-change #(on-change % :owner-search-value)}]
     [TextField {:label (tr [:land :cadastral-filter-label])
                 :value (:cadastral-search-value filter-params)
                 :on-change #(on-change % :cadastral-search-value)}]

     [select/select-enum {:e! e!
                          :attribute :land-acquisition/impact
                          :show-empty-selection? true
                          :value impact-search-value
                          :on-change #(on-change % :impact)}]
     ;; this isn't implemented yet (as of 2020-05-12)
     #_[select/select-enum {:e! e!
                            :attribute :land-acquisition/process
                          :show-empty-selection? false
                          :on-change #(on-change % :process-search-value)}]
     [select/form-select
      {:label (tr [:land :filter :quality])
       :name "Quality"
       :value quality
       :items [{:value nil :label (tr [:land :quality :any])}
               {:value :good :label (tr [:land :quality :good])}
               {:value :bad :label (tr [:land :quality :bad])}
               {:value :questionable :label (tr [:land :quality :questionable])}]
       :on-change (fn [val]
                    (e! (land-controller/->SearchOnChange :quality val)))}]]))

(defn cadastral-groups
  [e! _ _]
  (e! (land-controller/->SearchOnChange :estate-search-value ""))
  (fn [e! project units]
    (let [land-acquisitions (into {}
                                  (map
                                    (juxt :land-acquisition/cadastral-unit
                                          #(update % :land-acquisition/impact :db/ident)))
                                  (:land-acquisitions project))
          ids (:land/filtered-unit-ids project) ;; set by the cadastral search field
          units (map (fn [unit]
                       (assoc unit :land-acquisition (get land-acquisitions (:teet-id unit))))
                     units)
          units (if (empty? ids)
                  []
                  (filter #(ids (:teet-id %)) units))
          grouped (group-by
                    (fn [unit]
                      (into #{}
                            (map
                              (fn [owner]
                                (if (:r_kood owner)
                                  (select-keys owner [:r_kood :r_riik ])
                                  (select-keys owner [:isiku_tyyp :nimi]))))
                            (get-in unit [:estate :omandiosad])))
                    units)]
      [:div
       (mapc
        (fn [[owner _units :as group]]
          [owner-group e!
           (or (:land/open-estates project) #{})
           (get-in project [:land/forms owner :land/owner-compensation-form])
           (get-in project [:land/forms owner :land/cadastral-forms])
           (get-in project [:land/forms owner :land/estate-forms])
           group])
        grouped)])))


(defn related-cadastral-units-info
  [e! _app project]
  (e! (land-controller/->FetchRelatedEstates))
  (e! (land-controller/->FetchLandAcquisitions (:thk.project/id project)))
  (fn [e! _app project]
    (let [fetched-count (:fetched-estates-count project)
          related-estate-count (count (:land/related-estate-ids project))]
      [:div
       [:div {:style {:margin-top "1rem"}
              :class (<class common-styles/heading-and-button-style)}
        [typography/Heading2 (tr [:project :cadastral-units-tab])]
        [buttons/button-secondary {:href (url/set-query-param :configure "cadastral-units")}
         (tr [:buttons :edit])]]
       (if (:land/estate-info-failure project)
         [:div
          [:p (tr [:land :estate-info-fetch-failure])]
          [buttons/button-primary {:on-click (e! land-controller/->FetchRelatedEstates)}
           "Try again"]]
         (if (not= fetched-count related-estate-count)
           [:div
            [:p (tr [:land :fetching-land-units]) " " (str fetched-count " / " related-estate-count)]
            [LinearProgress {:variant :determinate
                             :value (* 100 (/ fetched-count related-estate-count))}]]
           [:div

            [filter-units e! (:land-acquisition-filters project)]
            [cadastral-groups e! (dissoc project :land-acquisition-filters) (:land/units project)]]))])))
