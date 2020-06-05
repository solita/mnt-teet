(ns teet.project.land-view
  (:require [teet.ui.typography :as typography]
            [herb.core :refer [<class]]
            [teet.common.common-styles :as common-styles]
            [teet.ui.buttons :as buttons]
            [teet.localization :refer [tr tr-enum tr-or]]
            [teet.ui.util :refer [mapc]]
            [reagent.core :as r]
            [teet.ui.panels :as panels]
            [teet.ui.material-ui :refer [ButtonBase Collapse LinearProgress Grid Link Divider Chip]]
            [teet.ui.text-field :refer [TextField] :as text-field]
            [teet.project.land-controller :as land-controller]
            [teet.theme.theme-colors :as theme-colors]
            [garden.color :refer [darken]]
            [teet.land.land-model :as land-model]
            [teet.ui.url :as url]
            [teet.land.land-specs]
            [teet.project.project-controller :as project-controller]
            [teet.ui.form :as form]
            [teet.ui.select :as select]
            [teet.util.datomic :as du]
            [teet.ui.common :as common]
            [teet.comments.comments-view :as comments-view]
            [teet.ui.table :as table]))

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
     :background-color bg-color}))

(defn impact-form-style
  []
  {:background-color theme-colors/gray-lighter
   :padding "1.5rem"})

(defn impact-form-footer
  [disabled? {:keys [validate cancel]}]
  [:div {:style {:display :flex
                 :flex-direction :row
                 :justify-content :flex-end
                 :background-color theme-colors/gray-lighter
                 :padding "0 1.5rem 1.5rem 0"}}
   [buttons/button-secondary {:style {:margin-right "1rem"}
                              :on-click cancel
                              :disabled disabled?}
    (tr [:buttons :cancel])]
   [buttons/button-primary {:disabled disabled?
                            :type :submit
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
  [e! {:keys [estate-id]} on-change form-data]
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
                  :cancel-event #(land-controller/->CancelEstateForm estate-id)
                  :disable-buttons? (not (boolean (:saved-data form-data)))}
      [:div
       [:div {:style {:padding "0.2rem"}}
        [form/field :estate-procedure/type
         [select/select-enum {:e! e!
                              :label-element typography/BoldGreyText
                              :attribute :estate-procedure/type}]]
        (when (= procedure-type :estate-procedure.type/acquisition-negotiation)
          [form/many {:attribute :estate-procedure/process-fees
                      :before [typography/BoldGreyText (tr [:fields :estate-procedure/process-fees])]
                      :after [buttons/link-button
                              {:on-click #(add-row! :estate-procedure/process-fees)}
                              (tr [:land :add-owner])]}
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
          [field-with-title {:title (tr [:fields :estate-procedure/motivation-bonus])
                             :field-name :estate-procedure/motivation-bonus
                             :type :number
                             :placeholder 0
                             :end-icon text-field/euro-end-icon}])

        (when (#{:estate-procedure.type/urgent}
               procedure-type)
          [field-with-title {:title (tr [:fields :estate-procedure/urgent-bonus])
                             :field-name :estate-procedure/urgent-bonus
                             :type :number
                             :placeholder 0
                             :end-icon text-field/euro-end-icon}])

        (when (#{:estate-procedure.type/acquisition-negotiation :estate-procedure.type/expropriation} procedure-type)
          [form/many {:attribute :estate-procedure/compensations
                      :before [typography/BoldGreyText (tr [:fields :estate-procedure/compensations])]
                      :after [buttons/link-button
                              {:on-click #(add-row! :estate-procedure/compensations)}
                              (tr [:land :add-compensation])]
                      :atleast-once? true}
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
                            (tr [:land :add-compensation])]
                    :atleast-once? true}
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
           [form/many {:before [typography/BoldGreyText {:style {:margin-bottom "0.5rem"}}
                                (tr [:fields :estate-procedure/land-exchanges])]
                       :attribute :estate-procedure/land-exchanges
                       :atleast-once? true}
            [Grid {:container true :spacing 3}
             [Grid {:item true :xs 12}
              [form/field {:attribute :land-exchange/cadastral-unit-id}
               [TextField {:label-element typography/BoldGreyText
                           :placeholder (tr [:land :cadastral-unit-number])}]]]
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
       (form/footer2 form/form-footer)]]]))


(defn land-acquisition-form
  [e! {:keys [teet-id PINDALA] :as unit} quality estate-procedure-type on-change-event form-data]
  (let [show-extra-fields? (du/enum= (:land-acquisition/impact form-data) :land-acquisition.impact/purchase-needed)
        estate-id (get-in unit [:estate :estate-id])]
    [:div
     [form/form {:e! e!
                 :value form-data
                 :on-change-event (fn [form-data]
                                    (on-change-event teet-id form-data)) ; update-impact-form
                 :save-event #(land-controller/->SubmitLandAcquisitionForm form-data
                                                                           (:teet-id unit)
                                                                           estate-procedure-type
                                                                           estate-id)
                 :spec :land-acquisition/form
                 :cancel-event #(land-controller/->CancelLandAcquisition unit)
                 :footer (partial impact-form-footer (not (boolean (:saved-data form-data))))
                 :class (<class impact-form-style)}
      ^{:attribute :land-acquisition/impact}
      [select/select-enum {:e! e!
                           :attribute :land-acquisition/impact}]
      (when (not= :land-acquisition.impact/purchase-not-needed (:land-acquisition/impact form-data))
        ^{:attribute :land-acquisition/status}
        [select/select-enum {:e! e!
                             :attribute :land-acquisition/status
                             :show-empty-selection? true}])
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
        [TextField {:type :number :input-style {:width "50%"}}])
      (when (and show-extra-fields? (not= :estate-procedure.type/urgent estate-procedure-type))
        ^{:attribute :land-acquisition/price-per-sqm}
        [TextField {:type :number}])
      (when show-extra-fields?
        ^{:attribute :land-acquisition/registry-number}
         [TextField {}])]]))

(defn acquisition-impact-status
  [impact status]
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
    (assert (keyword? impact))
    (when status
      (assert keyword? status))
    [:div {:class (<class common-styles/flex-align-center)}
     [:div {:class (<class common-styles/status-circle-style color)
            :title impact}]
     [:span {:style {:text-align :left}
             :class (<class common-styles/gray-text)}
      (tr-enum impact)
      (when status
        (str " - " (tr-enum status)))]]))

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
  [e! update-cadastral-form-event estate-procedure-type cadastral-forms {:keys [teet-id TUNNUS KINNISTU MOOTVIIS MUUDET quality selected?] :as unit}]
  ^{:key (str TUNNUS)}
  (let [cadastral-form (get cadastral-forms teet-id)]
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
      [typography/SectionHeading {:style {:text-align :left}} (str (:L_AADRESS unit) " (" (land-controller/cadastral-purposes TUNNUS unit) ")")]
      [:div {:class (<class common-styles/space-between-center)}
       [acquisition-impact-status (get-in cadastral-form [:land-acquisition/impact]) (get-in cadastral-form [:land-acquisition/status])]
       [:span {:class (<class common-styles/gray-text)}
        TUNNUS]]]
     [Collapse
      {:in selected?
       :mount-on-enter true}
      [land-acquisition-form
       e!
       unit
       quality
       estate-procedure-type
       update-cadastral-form-event
       cadastral-form]]]))

(defn group-style
  []
  {:width "100%"
   :justify-content :space-between
   :flex-direction :column
   :align-items :flex-start
   :padding "0.5rem"})


(defn estate-group
  [e! project open-estates cadastral-forms estate-forms [estate-id units]]
  (let [estate (:estate (first units))

        ;;these are all done just to calculate the total cost for the estate, there might be an easier way
        estates-land-acquisitions (land-model/estate-land-acquisitions estate-id
                                                                       (:land/units project)
                                                                       (:land-acquisitions project))
        estate-procedure (land-model/estate-compensation estate-id (:estate-compensations project))
        total-estate-cost (land-model/total-estate-cost estate-procedure
                                                        estates-land-acquisitions)]
    [common/hierarchical-container
     {:heading-color theme-colors/gray-lighter
      :heading-text-color :inherit
      :heading-content
      [:<>
       [ButtonBase {:class (<class group-style)
                    :on-click (e! land-controller/->ToggleOpenEstate estate-id)}

        [typography/SectionHeading (tr [:land :estate]) " " estate-id]
        [:span (count units) " " (if (= 1 (count units))
                                   (tr [:land :unit])
                                   (tr [:land :units]))]]
       [Collapse
        {:in (boolean (open-estates estate-id))
         :mount-on-enter true}
        [:div {:class (<class estate-compensation-form)}
         [estate-group-form e! estate
          (r/partial land-controller/->UpdateEstateForm estate) (get estate-forms estate-id)]
         [Divider {:style {:margin "1rem 0"}}]
         [typography/BoldGreyText {:style {:text-transform :uppercase}}
          (tr [:land :estate-acquisition-cost])]
         [:div {:style {:display :flex
                        :justify-content :space-between}}
          [:span (str (tr [:common :total]) ": ") (str total-estate-cost " €")]
          [Link {:style {:display :block}
                 :href (url/set-query-param :modal "estate" :modal-target estate-id :modal-page "costs")}
           (tr [:common :show-details])]]
         [Divider {:style {:margin "1rem 0"}}]
         [typography/BoldGreyText {:style {:text-transform :uppercase}}
          (tr [:land :estate-data])]
         [Link {:style {:display :block}
                :href (url/set-query-param :modal "estate" :modal-target estate-id :modal-page "burdens")}
          [common/count-chip {:label (count (:jagu3 estate))}]
          (tr [:estate-modal :page :burdens])]
         [Link {:style {:display :block}
                :href (url/set-query-param :modal "estate" :modal-target estate-id :modal-page "mortgages")}
          [common/count-chip {:label (count (:jagu4 estate))}]
          (tr [:estate-modal :page :mortgages])]]]]
      :children
      (mapc
        (r/partial cadastral-unit e!
                   land-controller/->UpdateCadastralForm
                   (get-in estate-forms [estate-id :estate-procedure/type])
                   cadastral-forms)
        units)}]))

(defn owner-group
  [e! project open-estates cadastral-forms estate-forms [owner units]]
  ^{:key (str owner)}
  [:div {:style {:margin-bottom "2rem"}}
   (let [owners (get-in (first units) [:estate :omandiosad])]

     [common/hierarchical-container
      {:heading-color theme-colors/gray
       :heading-text-color theme-colors/white
       :heading-content
       [:div {:class (<class group-style)}
        [typography/SectionHeading (if (not= (count owners) 1)
                                     (str (count owners) " owners")
                                     (:nimi (first owners)))]
        [:span (count units) " " (if (= 1 (count units))
                                   (tr [:land :unit])
                                   (tr [:land :units]))]]
       :children
       (mapc
         (fn [unit-group]
           [estate-group e! project open-estates cadastral-forms estate-forms unit-group])
         (group-by
           (fn [unit]
             (get-in unit [:estate :estate-id]))
           units))}])])

(defn filter-units
  [e! {:keys [estate-search-value quality status impact] :as filter-params}]
  ;; filter-params will come from appdb :land-acquisition-filters key
  ;; (println "filter-params:" filter-params)
  (r/with-let [on-change (fn [kw-or-e field]
                           ;; select-enum gives us the kw straight, textfields pass event
                           (let [value (if (or (keyword? kw-or-e) (nil? kw-or-e))
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
                          :empty-option-label (tr [:land :quality :any])
                          :attribute :land-acquisition/impact
                          :show-empty-selection? true
                          :value impact
                          :on-change #(on-change % :impact)}]
     ;; this isn't implemented yet (as of 2020-05-12)
     [select/select-enum {:e! e!
                          :attribute :land-acquisition/status
                          :value status
                          :show-empty-selection? true
                          :on-change #(on-change % :status)}]
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
    (let [ids (:land/filtered-unit-ids project) ;; set by the cadastral search field
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
        (fn [group]
          [owner-group e!
           project
           (or (:land/open-estates project) #{})
           (:land/cadastral-forms project)
           (:land/estate-forms project)
           group])
        grouped)])))

(defn modal-left-panel-navigation
  [current title pages]
  [:div
   [typography/Heading2 {:style {:color :white
                                 :margin-bottom "1rem"}}
    title]
   (mapc
     (fn [p]
       (if (= current (name p))
         [:strong {:style {:color :white}} (tr [:estate-modal :page p])]
         [Link {:href (url/set-query-param :modal-page (name p))
                :style {:display :block
                        :color :white}}
          (tr [:estate-modal :page p])]))
     pages)])

(defmulti estate-modal-content (fn [{:keys [page]}]
                                 (keyword page)))

(defmethod estate-modal-content :default
  [{:keys [page]}]
  [:span "usupported page: " page])

(defmethod estate-modal-content :burdens
  [{:keys [estate-info]}]
  (let [burdens (:jagu3 estate-info)]
    [:div {:class (<class common-styles/gray-container-style)}
     (if (not-empty burdens)
       (for [burden burdens]
         [common/estate-detail {:title (:kande_liik_tekst burden)
                                :date (:kande_alguskuupaev burden)
                                :body (first (second (first (:kande_tekst burden))))
                                }])
       [:p (tr [:land :no-active-burdens])])]))

(defmethod estate-modal-content :mortgages
  [{:keys [estate-info]}]
  (let [mortgages (:jagu4 estate-info)]
    [:div {:class (<class common-styles/gray-container-style)}
     (if (not-empty mortgages)
       (for [mortgage mortgages]
         [common/estate-detail {:title (str
                                         (:kande_liik_tekst mortgage)
                                         " "
                                         (:koormatise_rahaline_vaartus mortgage)
                                         " "
                                         (:koormatise_rahalise_vaartuse_valuuta mortgage))
                                :date (:kande_alguskuupaev mortgage)
                                :body (first (second (first (:kande_tekst mortgage))))
                                }])
       [:p (tr [:land :no-active-mortgages])])]))


(defn create-land-acquisition-row
  [la]
  {:name-id {:address (get-in la [:unit-data :L_AADRESS])
             :id (get-in la [:unit-data :TUNNUS])}
   :price-per-sqm (:land-acquisition/price-per-sqm la)
   :area-to-obtain (:land-acquisition/area-to-obtain la)
   :total (* (double (:land-acquisition/price-per-sqm la))
             (double (:land-acquisition/area-to-obtain la)))})

(defmethod estate-modal-content :costs
  [{:keys [e! project estate-info]}]
  (let [estate-id (:estate-id estate-info)
        estates-land-acquisitions (land-model/estate-land-acquisitions estate-id
                                                                       (:land/units project)
                                                                       (:land-acquisitions project))
        estate-procedure (land-model/estate-compensation estate-id (:estate-compensations project))
        parsed-estate-comps (land-model/estate-procedure-costs estate-procedure)
        total-estate-cost (land-model/total-estate-cost estate-procedure
                                                        estates-land-acquisitions)
        land-exchanges (:estate-procedure/land-exchanges estate-procedure)]
    [:div {:class (<class common-styles/gray-container-style)}
     [typography/Heading3 {:style {:margin-bottom "1rem"}}
      (tr [:land :unit-valuations])]

     (if (not-empty estates-land-acquisitions)
       [table/simple-table
        [[(tr [:land :unit-name-id]) {:align :left}]
         [(tr [:fields :land-acquisition/price-per-sqm]) {:align :right}]
         [(tr [:fields :land-acquisition/area-to-obtain]) {:align :right}]
         [(tr [:common :total]) {:align :right}]]
        (for [la estates-land-acquisitions
              :let [data (create-land-acquisition-row la)]]

          [[[:div
             [:span {:style {:display :block}}
              (get-in data [:name-id :address])]
             [typography/GreyText
              (get-in data [:name-id :id])]]
            {:align :left}]
           [(str (:price-per-sqm data) " €") {:align :right}]
           [(str (:area-to-obtain data) " m²") {:align :right}]
           [(str (:total data) " €") {:align :right}]])]
       [typography/GreyText (tr [:land :no-land-acquisitions])])

     [:div
      [typography/Heading3 {:style {:margin "1rem 0"}}
       (tr [:fields :estate-procedure/compensations])]
      (if (not-empty parsed-estate-comps)
        [table/simple-table
         [[(tr [:land :table-heading :type]) {:align :left}]
          [(tr [:land :table-heading :area]) {:align :right}]]
         (for [{:keys [reason description amount]} parsed-estate-comps]
           [[(or description (tr-or [:enum reason] [:fields reason])) {:align :left}]
            [(str amount " €") {:align :right}]])]

        [typography/GreyText (tr [:land :no-estate-compensations])])]

     (when land-exchanges
       [:div
        [typography/Heading3 {:style {:margin "1rem 0"}}
         (tr [:land :land-exchange])]
        [table/simple-table
         [[(tr [:land :table-heading :type]) {:align :left}]
          [(tr [:land :table-heading :area]) {:align :right}]
          [(tr [:fields :land-acquisition/price-per-sqm]) {:align :right}]]
         (for [land-exchange land-exchanges]
           [[[:div
              [:span {:style {:display :block}}
               (tr [:land :land-exchange])]
              [typography/GreyText
               (:land-exchange/cadastral-unit-id land-exchange)]]
             {:align :left}]
            [(str (:land-exchange/area land-exchange) " m²") {:align :right}]
            [(str (:land-exchange/price-per-sqm land-exchange) " €") {:align :right}]])]])

     [:div
      [typography/Heading3 {:style {:margin "1rem 0"}}
       (tr [:land :total-cost])]
      [:div {:style {:display :flex
                     :justify-content :space-between}}
       [:span (tr [:land :total-excluding-process])]
       [typography/BoldGreyText (str total-estate-cost " €")]]]]))


(defmethod estate-modal-content :comments
  [{:keys [e! app project estate-info]}]
  (let [estate-id (:estate-id estate-info)]
    [:div {:style {:padding "1.5rem"}}
     [comments-view/lazy-comments {:e! e!
                                   :app app
                                   :entity-type :estate-comments
                                   :entity-id [:estate-comments/project+estate-id [(:db/id project) estate-id]]}]]))

(defmulti land-view-modal (fn [{:keys [modal]}]
                            (keyword modal)))

(defmethod land-view-modal :default
  [{:keys [modal]}]
  [:div "Unsupported land view dialog " modal])

(defmethod land-view-modal :estate
  [{:keys [e! app page project estate-info] :as opts}]
  {:title (tr [:estate-modal :page (keyword page)])
   :left-panel [modal-left-panel-navigation
                page
                (tr [:land :estate-data])
                [:burdens :mortgages :costs :comments]]
   :right-panel [estate-modal-content {:e! e!
                                       :page page
                                       :app app
                                       :project project
                                       :estate-info estate-info}]})

(defn land-view-modals [e! app project]
  (let [modal (get-in app [:query :modal])
        target (get-in app [:query :modal-target])
        modal-page (get-in app [:query :modal-page])
        estate-info (some
                      (fn [unit]
                        (when (= (get-in unit [:estate :estate-id]) target)
                          (:estate unit)))
                      (:land/units project))]
    [panels/modal+ (merge {:title (tr [:land-acquisition modal])
                           :open-atom (r/wrap (boolean modal) :_)
                           :on-close (e! project-controller/->CloseDialog)
                           :max-width :sm}
                          (land-view-modal {:e! e!
                                            :app app
                                            :modal modal
                                            :target target
                                            :page modal-page
                                            :project project
                                            :estate-info estate-info}))]))


(defn related-cadastral-units-info
  [e! _app project]

  (r/create-class
    {:component-did-mount
     (do
       (e! (land-controller/->FetchEstateCompensations (:thk.project/id project)))
       (e! (land-controller/->FetchLandAcquisitions (:thk.project/id project))))

     :component-did-update (fn [this [_ _ _ _]]
                             (let [[_ _ _ project] (r/argv this)]
                               (when (nil? (:land/related-estate-ids project))
                                 (e! (land-controller/->FetchRelatedEstates)))))
     :reagent-render
     (fn [e! app project]
       (let [fetched-count (:fetched-estates-count project)
             related-estate-count (count (:land/related-estate-ids project))]
         [:div
          (when (= fetched-count related-estate-count)      ;;TODO needs to also check for the required form informations
            [land-view-modals e! app project])
          [:div {:style {:margin-top "1rem"}
                 :class (<class common-styles/heading-and-action-style)}
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
               [cadastral-groups e! (dissoc project :land-acquisition-filters) (:land/units project)]]))]))}))
