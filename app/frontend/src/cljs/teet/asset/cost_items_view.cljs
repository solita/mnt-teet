(ns teet.asset.cost-items-view
  "Cost items view"
  (:require [clojure.string :as str]
            [herb.core :refer [<class]]
            [reagent.core :as r]
            [teet.asset.asset-model :as asset-model]
            [teet.asset.asset-type-library :as asset-type-library]
            [teet.asset.asset-ui :as asset-ui
             :refer [tr* label label-for select-fgroup-and-fclass]]
            [teet.asset.cost-items-controller :as cost-items-controller]
            [teet.asset.cost-items-map-view :as cost-items-map-view]
            [teet.authorization.authorization-check :refer [when-authorized]]
            [teet.common.common-controller :as common-controller]
            [teet.common.common-styles :as common-styles]
            [teet.localization :refer [tr tr-enum] :as localization]
            [teet.project.project-view :as project-view]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.breadcrumbs :as breadcrumbs]
            [teet.ui.buttons :as buttons]
            [teet.ui.common :as common]
            [teet.ui.container :as container]
            [teet.ui.context :as context]
            [teet.ui.date-picker :as date-picker]
            [teet.ui.form :as form]
            [teet.ui.format :as fmt]
            [teet.ui.icons :as icons]
            [teet.ui.material-ui :refer [Grid CircularProgress Link]]
            [teet.ui.panels :as panels]
            [teet.ui.project-context :as project-context]
            [teet.ui.query :as query]
            [teet.ui.select :as select]
            [teet.ui.table :as table]
            [teet.ui.text-field :as text-field]
            [teet.ui.typography :as typography]
            [teet.ui.url :as url]
            [teet.user.user-model :as user-model]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]))



(def ^:private integer-pattern #"^-?\d*$")
(def ^:private decimal-pattern #"^-?\d+((,|\.)\d*)?$")
(def ^:private only-whitespace-pattern #"^\s+$")

(defn- extremum-value-by-ref
  "Find the extremum (min or max) value by searching for the given
  `extremum-value-ref` attribute, starting from the current component
  defined by `component-oid` and walking up the hierarchy up to the
  asset. Return the first value of the attribute encountered."
  [component-oid cost-item-data extremum-value-ref]
  (let [path (reverse (asset-model/find-component-path cost-item-data component-oid))]
    (some (du/enum->kw extremum-value-ref) path)))

(def ^:private maximum-decimal-precision 6)

(defn- too-many-decimal-digits? [dec-string]
  (< maximum-decimal-precision
     (-> dec-string
         (str/split #",|\.")
         second
         count)))

(defn- validate [valueType component-oid cost-item-data {:attribute/keys [min-value max-value min-value-ref max-value-ref]} v]
  (when (some? v)
    (let [min-value-by-ref (when min-value-ref (extremum-value-by-ref component-oid cost-item-data min-value-ref))
          max-value-by-ref (when max-value-ref (extremum-value-by-ref component-oid cost-item-data max-value-ref))]
      (or (when (and (string? v)
                     (re-matches only-whitespace-pattern v))
            (tr [:asset :validate :only-whitespace]))
          (case valueType
            ;; Check length for strings
            :db.type/string
            (cond
              (and min-value (< (count v) min-value))
              (tr [:asset :validate :min-length] {:min-length min-value})

              (and max-value (> (count v) max-value))
              (tr [:asset :validate :max-length] {:max-length max-value})

              ;; TODO: own error messages for refs?
              (and min-value-by-ref (< (count v) min-value-by-ref))
              (tr [:asset :validate :min-length] {:min-length min-value-by-ref})

              (and max-value-by-ref (> (count v) max-value-by-ref))
              (tr [:asset :validate :max-length] {:max-length max-value-by-ref}))

            (:db.type/long :db.type/bigdec)
            (let [v (str/trim v)]
              (cond
                (and (= valueType :db.type/long)
                     (not (re-matches integer-pattern v)))
                (tr [:asset :validate :integer-format])

                (and (= valueType :db.type/bigdec)
                     (not (re-matches decimal-pattern v)))
                (tr [:asset :validate :decimal-format])

                (and (= valueType :db.type/bigdec)
                     (too-many-decimal-digits? v))
                (tr [:asset :validate :decimal-precision] {:precision maximum-decimal-precision})

                :else
                (let [n (js/parseFloat v)]
                  (cond
                    (and min-value (< n min-value))
                    (tr [:asset :validate :min-value] {:min-value min-value})

                    (and max-value (> n max-value))
                    (tr [:asset :validate :max-value] {:max-value max-value})

                    ;; TODO: own error messages for refs?
                    (and min-value-by-ref (< n min-value-by-ref))
                    (tr [:asset :validate :min-value] {:min-value min-value-by-ref})

                    (and max-value-by-ref (> n max-value-by-ref))
                    (tr [:asset :validate :max-value] {:max-value max-value-by-ref})))))

            ;; no validation otherwise
            nil)))))

(defn- attribute-group [common-attr-idents
                        {ident :db/ident
                         cost-grouping? :attribute/cost-grouping?}]
  (cond
    cost-grouping?
    :cost-grouping

    (common-attr-idents ident)
    :common

    :else
    :details))

(defn- attribute-grid-item
  [content]
  [Grid {:item true
         :md 4
         :xs 12
         :style {:padding "0.2rem"}}
   content])

(defn- display-list-item [{:keys [rotl value] :as opts}]
  [:label {:class (<class common-styles/input-label-style false false)}
   [typography/Text2Bold (:label opts)]
   (-> value rotl label)])

(defn- display-input [{:keys [value unit label format]
                       :or {format str}}]
  [:label {:class (<class common-styles/input-label-style false false)}
   [typography/Text2Bold label]
   (if (some? value)
     (str (format value) (when unit (str "\u00a0" unit)))
     "\u2400")])

(defn- road-nr-format [relevant-roads]
  (let [road-nr->item-name (->> relevant-roads
                                (map (juxt :road-nr
                                           #(str (:road-nr %) " " (:road-name %))))
                                (into {}))]
    (fn [select-value]
      (get road-nr->item-name
           select-value
           ""))))

(defn- with-relevant-roads* [{e! :e!} _ {project-id :thk.project/id}]
  (e! (cost-items-controller/->FetchRelevantRoads project-id))
  (fn [_ component _]
    (conj component
          (get @cost-items-controller/relevant-road-cache project-id))))

(defn- with-relevant-roads [opts component]
  [project-context/consume
   [with-relevant-roads* opts component]])

(defn- number-value
  ([opts]
   (number-value [] opts))
  ([extra-opts opts]
   (update opts :value
           #(or
             (some (fn [extra-opt]
                     (when (= (str extra-opt) (str %))
                       extra-opt)) extra-opts)
             (if (string? %)
               (js/parseInt %)
               %)))))

(defn- relevant-road-select* [{:keys [empty-label extra-opts extra-opts-label] :as opts
                               :or {empty-label ""}} relevant-roads]
  (let [items (->> relevant-roads (map :road-nr) sort vec)
        fmt (road-nr-format relevant-roads)]
    [select/form-select
     (->> opts
          (merge {:show-empty-selection? (if extra-opts false true)
                  :empty-selection-label empty-label
                  :items (if extra-opts
                           (into extra-opts items)
                           items)
                  :format-item (if extra-opts
                                 #(or (extra-opts-label %)
                                      (fmt %))
                                 fmt)})
          (number-value extra-opts))]))

(defn- relevant-road-select [opts]
  [with-relevant-roads opts
   [relevant-road-select* opts]])

(defn- carriageway-for-road-select* [opts selected-road-nr relevant-roads]
  [select/form-select
   (merge (number-value opts)
          {:show-empty-selection? true
           :items (or (cost-items-controller/carriageways-for-road
                       selected-road-nr
                       relevant-roads)
                      [1])
           :format-item str})])

(defn- carriageway-for-road-select [opts selected-road-nr]
  [with-relevant-roads
   opts
   [carriageway-for-road-select* opts selected-road-nr]])

(defn- location-entry [e! locked? selected-road-nr single-point?]
  (let [input-textfield (if locked? display-input text-field/TextField)]
    [:<>
     [Grid {:item true
            :md 10
            :xs 12
            :style {:padding "0.2rem"}}
      [form/field :location/road-nr
       [relevant-road-select {:e! e!}]]]

     [Grid {:item true
            :md 2
            :xs 12
            :style {:padding "0.2rem"}}
      [form/field :location/carriageway
       [carriageway-for-road-select {:e! e!} selected-road-nr]]]

     [Grid {:item true
            :md 4
            :xs 12
            :style {:padding "0.2rem"}}
      [form/field {:attribute :location/start-point
                   :required? true}
       [input-textfield {}]]]

     [Grid {:item true
            :md 4
            :xs 12
            :style {:padding "0.2rem"}}
      [form/field {:attribute :location/start-km
                   :required? true}
       [input-textfield {:end-icon (text-field/unit-end-icon "km")}]]]

     [Grid {:item true
            :md 4
            :xs 12
            :style {:padding "0.2rem"}}
      [form/field :location/start-offset-m
       [input-textfield {:end-icon (text-field/unit-end-icon "m")}]]]

     (when-not single-point?
       [Grid {:item true
              :md 4
              :xs 12
              :style {:padding "0.2rem"}}
        [form/field :location/end-point
         [input-textfield {}]]])

     (when-not single-point?
       [Grid {:item true
              :md 4
              :xs 12
              :style {:padding "0.2rem"}}
        [form/field :location/end-km
         [input-textfield {:end-icon (text-field/unit-end-icon "km")}]]])

     (when-not single-point?
       [Grid {:item true
              :md 4
              :xs 12
              :style {:padding "0.2rem"}}
        [form/field :location/end-offset-m
         [input-textfield {:end-icon (text-field/unit-end-icon "m")}]]])

     [Grid {:item true
            :md 12 :xs 12}
      [form/field :location/single-point?
       [select/checkbox {}]]]]))

(defn- attributes* [{:keys [e! attributes component-oid cost-item-data inherits-location?
                            common ctype]}
                    rotl locked?]
  (r/with-let [open? (r/atom #{:location :cost-grouping :common :details})
               toggle-open! #(swap! open? cu/toggle %)]
    (let [common-attrs (some-> common rotl :attribute/_parent)
          common-attr-idents (into #{} (map :db/ident) common-attrs)
          attrs-groups (->> (concat common-attrs attributes)
                            (group-by (partial attribute-group common-attr-idents))
                            (cu/map-vals
                             (partial sort-by (juxt (complement :attribute/mandatory?)
                                                    label))))
          map-open? (:location/map-open? cost-item-data)]
      [:<>
       ;; Show location group if not inherited from parent
       (when (not inherits-location?)
         [container/collapsible-container
          {:open? (@open? :location)
           :on-toggle (r/partial toggle-open! :location)}
          [:<>
           (tr [:asset :field-group :location])
           [buttons/button-text
            {:style {:float :right}
             :on-click (e! cost-items-controller/->UpdateForm
                           {:location/map-open? (not map-open?)})}
            (if map-open?
              (tr [:asset :location :hide-map])
              (tr [:asset :location :show-map]))]]
          [Grid {:container true
                 :justify :flex-start
                 :alignItems :flex-end}
           [location-entry e! locked?
            (:location/road-nr cost-item-data)
            (:location/single-point? cost-item-data)]]])
       (doall
        (for [g [:cost-grouping :common :details]
              :let [attrs (attrs-groups g)]
              :when (seq attrs)]
          ^{:key (str g)}
          [container/collapsible-container
           {:open? (@open? g)
            :on-toggle (r/partial toggle-open! g)
            :size :small}
           (tr [:asset :field-group g])
           [Grid {:container true
                  :justify :flex-start
                  :alignItems :flex-end}
            (doall
             (for [{:db/keys [ident valueType]
                    :attribute/keys [mandatory?]
                    :asset-schema/keys [unit] :as attr} attrs
                   :let [type (:db/ident valueType)
                         unit (if (= ident :component/quantity)
                                (:component/quantity-unit ctype)
                                unit)]]
               ^{:key (str ident)}
               [attribute-grid-item
                [form/field {:attribute ident
                             :required? mandatory?
                             :validate (r/partial validate
                                                  (:db/ident valueType)
                                                  component-oid
                                                  cost-item-data
                                                  (select-keys attr
                                                               [:attribute/min-value
                                                                :attribute/max-value
                                                                :attribute/min-value-ref
                                                                :attribute/max-value-ref]))}
                 (cond
                   ;; Selection value
                   (= type :db.type/ref)
                   (if locked?
                     [display-list-item {:label (label attr)
                                         :rotl rotl}]
                     [select/form-select
                      {:id ident
                       :label (label attr)
                       :show-empty-selection? true
                       :items (mapv :db/ident (:enum/_attribute attr))
                       :format-item (comp label rotl)}])

                   (= type :db.type/instant)
                   (if locked?
                     [display-input {:label (label attr)
                                     :format fmt/date}]
                     [date-picker/date-input {:label (label attr)}])

                   ;; Text field
                   :else
                   (if locked?
                     [display-input {:label (label attr)
                                     :unit unit}]
                     [text-field/TextField
                      {:label (label attr)
                       :read-only? locked?
                       :end-icon (when unit
                                   (text-field/unit-end-icon unit))}]))]]))]]))])))

(defn- attributes
  "Render grid of attributes."
  [opts]
  [context/consume-many [:rotl :locked?] [attributes* opts]])

(defn- add-component-menu* [menu-label allowed-components add-component! locked?]
  (when-not locked?
    [:<>
     (if (> (count allowed-components) 3)
       [common/context-menu
        {:label menu-label
         :icon [icons/content-add-circle-outline]
         :items (for [c allowed-components]
                  {:label (label c)
                   :icon [icons/content-add]
                   :on-click (r/partial add-component! (:db/ident c))})}]
       (doall
        (for [c allowed-components]
          ^{:key (str (:db/ident c))}
          [Grid {:item true :xs 12 :md 4}
           [buttons/button-secondary {:size :small
                                      :on-click (r/partial add-component! (:db/ident c))
                                      :start-icon (r/as-element [icons/content-add])}
            (label c)]])))]))

(defn- add-component-menu [menu-label allowed-components add-component!]
  [context/consume :locked?
   [add-component-menu* menu-label allowed-components add-component!]])





(defn- component-tree-level-indent [level]
  [:<>
   (when level
     (doall
      (for [i (range level)]
        ^{:key i}
        [icons/navigation-chevron-right
         {:style (merge
                  {:color :lightgray}
                  (when (pos? i)
                    {:margin-left "-0.9rem"}))}])))])

(defn- component-rows [{:keys [e! level components locked?
                               delete-fn children-label-fn]}]
  (when (seq components)
    [:<>
     (doall
      (for [c components]
        ^{:key (str (:db/id c))}
        [:span
         [:div {:class (<class common-styles/flex-row)}
          [:div {:class (<class common-styles/flex-table-column-style
                                40 :flex-start 1 nil)}
           [component-tree-level-indent level]
           [url/Link {:page :cost-item
                      :params {:id (:asset/oid c)}}
            (children-label-fn c)]]
          [:div {:class (<class common-styles/flex-table-column-style
                                35 :flex-start 0 nil)}
           [label-for (:component/ctype c)]]
          [:div {:class (<class common-styles/flex-table-column-style
                                25 :flex-end 0 nil)}
           (when-not locked?
             [when-authorized
              :asset/delete-component nil
              [buttons/delete-button-with-confirm
               {:small? true
                :icon-position :start
                :action (r/partial delete-fn (:db/id c))}
               (tr [:buttons :delete])]])]]
         [component-rows {:e! e!
                          :locked? locked?
                          :components (:component/components c)
                          :children-label-fn children-label-fn
                          :delete-fn delete-fn
                          :level (inc (or level 0))}]]))]))

(defn- children-tree*
  "Show listing of all components (and their subcomponents recursively) for the asset."
  [{:keys [e! parent label-fn children-fn children-label-fn delete-fn]} locked?]
  [:<>
   [typography/Heading3 (label-fn parent)]
   [component-rows {:e! e!
                    :locked? locked?
                    :components (children-fn parent)
                    :children-label-fn children-label-fn
                    :delete-fn delete-fn}]])

(defn- components-tree
  "Relevant options:
   - label-fn takes the asset, produces
   - delete-fn when called with db/id deletes the said entity"
  [asset {:keys [e!] :as opts}]
  [context/consume :locked?
   [children-tree* (assoc opts
                          :parent asset
                          :label-fn #(str (tr [:asset :components :label])
                                          " ("
                                          (cu/count-matching-deep :component/ctype
                                                                  (:asset/components %))
                                          ")")
                          :children-label-fn :asset/oid
                          :children-fn :asset/components
                          :delete-fn (e! cost-items-controller/->DeleteComponent))]])

(defn- material-label [atl m]
  (->> m :material/type (asset-type-library/item-by-ident atl) tr*))

(defn- materials-list
  [component {:keys [e! atl] :as opts}]
  [context/consume :locked?
   [children-tree* (assoc opts
                          :parent component
                          :label-fn (constantly (tr [:asset :materials :label]))
                          :children-fn :component/materials
                          :children-label-fn #(str (:asset/oid %) " " (material-label atl %))
                          :delete-fn (e! cost-items-controller/->DeleteMaterial))]])

(defn- form-paper
  "Wrap the form input portion in a light gray paper."
  [component]
  [:div.cost-item-form {:style {:background-color theme-colors/gray-lightest
                                :margin-top "1rem"
                                :padding "1rem"}}
   component])

(defn- cost-item-form [e! atl relevant-roads {:asset/keys [fclass] :as form-data}]
  (r/with-let [initial-data form-data]
    (let [new? (nil? (:asset/oid form-data))
          cancel-event (if new?
                         #(common-controller/->NavigateWithSameParams :cost-items)

                         ;; Update with initial data to cancel
                         (r/partial
                          cost-items-controller/->UpdateForm initial-data))
          feature-class (when fclass
                          (asset-type-library/item-by-ident atl fclass))]
      [:<>
       (when-let [oid (:asset/oid form-data)]
         [typography/Heading2 oid])
       [form/form2
        {:e! e!
         :on-change-event cost-items-controller/->UpdateForm
         :value form-data
         :save-event cost-items-controller/->SaveCostItem
         :cancel-event cancel-event
         :disable-buttons? (= initial-data form-data)}

        [form/field {:attribute [:fgroup :asset/fclass]}
         [select-fgroup-and-fclass
          {:e! e!
           :atl atl
           :read-only? (seq (dissoc form-data :asset/fclass :fgroup :db/id))}]]

        (when feature-class
          ;; Attributes for asset
          [form-paper [attributes
                       {:e! e!
                        :attributes (some-> feature-class :attribute/_parent)
                        :cost-item-data form-data
                        :common :ctype/feature
                        :inherits-location? false
                        :relevant-roads relevant-roads}]])

        [form/footer2]]

       ;; Components (show only for existing)
       (when initial-data
         [:<>
          [components-tree  form-data
                            {:e! e!}]

          [add-component-menu
           (tr [:asset :add-component])
           (into []
                 (asset-type-library/allowed-component-types atl fclass))
           (e! cost-items-controller/->AddComponent)]])])))

(defn- component-form-navigation [atl component-path]
  [:<>
   [breadcrumbs/breadcrumbs
    (for [p component-path]
      {:link [url/Link {:page :cost-item
                        :params {:id (:asset/oid p)}}
              (:asset/oid p)]
       :title (if (number? (:db/id p))
                (:asset/oid p)
                (str (tr [:common :new]) " " (label (asset-type-library/item-by-ident atl (:component/ctype p)))))})]

   (into [:div]
         (butlast
          (interleave
           (for [p (into [(:db/ident (asset-type-library/fgroup-for-fclass
                                      atl
                                      (:asset/fclass (first component-path))))]
                         (map #(or (:asset/fclass %)
                                   (:component/ctype %)
                                   (:material/type %)))
                         component-path)]
             [label-for p])
           (repeat " / "))))])

(defn- component-form* [e! atl component-oid cost-item-data]
  (r/with-let [initial-component-data
               (last (asset-model/find-component-path cost-item-data
                                                      component-oid))
               new? (string? (:db/id initial-component-data))
               ctype (asset-type-library/item-by-ident
                      atl
                      (:component/ctype initial-component-data))

               cancel-event (if new?
                              #(common-controller/->SetQueryParam :component nil)
                              #(cost-items-controller/->UpdateForm initial-component-data))]
    (let [component-path (asset-model/find-component-path cost-item-data component-oid)
          component-data (last component-path)]
      [:<>
       (when (asset-model/component-oid? component-oid)
         [typography/Heading2 component-oid])
       [component-form-navigation atl component-path]

       [form/form2
        {:e! e!
         :on-change-event cost-items-controller/->UpdateForm
         :value component-data
         :save-event cost-items-controller/->SaveComponent
         :cancel-event cancel-event
         :disable-buttons? (= component-data initial-component-data)}

        ;; Attributes for component
        [form-paper
         [:<>
          (label ctype)
          [attributes {:e! e!
                       :attributes (some-> ctype :attribute/_parent)
                       :inherits-location? (:component/inherits-location? ctype)
                       :component-oid component-oid
                       :cost-item-data cost-item-data
                       :common :ctype/component
                       :ctype ctype}]]]

        (when (:component/materials component-data)
          [materials-list component-data {:e! e! :atl atl}])

        (when (not new?)
          [:div
           ;; Should have only either, never both
           (when-let [allowed-components (not-empty (asset-type-library/allowed-component-types atl ctype))]
             [add-component-menu
              (tr [:asset :add-component])
              allowed-components
              (e! cost-items-controller/->AddComponent)])
           (when-let [allowed-materials (asset-type-library/allowed-material-types atl ctype)]
             [add-component-menu
              (tr [:asset :add-material])
              allowed-materials
              (e! cost-items-controller/->AddMaterial)])])

        [:div {:class (<class common-styles/flex-row-space-between)
               :style {:align-items :center}}
         [url/Link {:page :cost-item
                    :params {:id (:asset/oid cost-item-data)}}
          (tr [:asset :back-to-cost-item] {:name (:common/name cost-item-data)})]
         [form/footer2]]]])))

(defn component-form
  [e! atl component-oid cost-item-data]
  ;; Delay mounting of component form until it is in app state.
  ;; This is needed when creating a new component and navigating to it
  ;; after save, as refresh fetch will happen after navigation.
  (if (nil? (asset-model/find-component-path cost-item-data component-oid))
    [CircularProgress]
    [component-form* e! atl component-oid cost-item-data]))


(defn- material-form* [e! atl material-oid cost-item-data]
  (r/with-let [initial-material-data
               (last (asset-model/find-component-path cost-item-data
                                                      material-oid))
               new? (string? (:db/id initial-material-data))
               material-type (asset-type-library/item-by-ident
                              atl
                              (:material/type initial-material-data))
               cancel-event (if new?
                              #(common-controller/->SetQueryParam :material nil)
                              #(cost-items-controller/->ResetMaterialForm initial-material-data))]
    (let [material-path (asset-model/find-component-path cost-item-data material-oid)
          material-data (last material-path)]
      [:<>
       (when (asset-model/material-oid? material-oid)
         [typography/Heading2 material-oid])
       [component-form-navigation atl material-path]

       [form/form2
        {:e! e!
         :on-change-event cost-items-controller/->UpdateMaterialForm
         :value material-data
         :save-event cost-items-controller/->SaveMaterial
         :cancel-event cancel-event
         :disable-buttons? (= material-data initial-material-data)}

        ;; Attributes for material
        [form-paper
         [:<>
          (label material-type)
          [attributes {:e! e!
                       :attributes (some-> material-type :attribute/_parent)
                       :inherits-location? true
                       :material-oid material-oid
                       :cost-item-data cost-item-data
                       :common? false
                       :material-type material-type}]]]

        [:div {:class (<class common-styles/flex-row-space-between)
               :style {:align-items :center}}
         [url/Link {:page :cost-item
                    :params {:id (:asset/oid cost-item-data)}}
          (tr [:asset :back-to-cost-item] {:name (or (:common/name cost-item-data)
                                                     (:asset/oid cost-item-data))})]
         [form/footer2]]]])))

(defn material-form
  [e! atl material-oid cost-item-data]
  (if (nil? (asset-model/find-component-path cost-item-data material-oid))
    [CircularProgress]
    [material-form* e! atl material-oid cost-item-data]))


(defn- add-cost-item [app version]
  (when-not (asset-model/locked? version)
    (let [add? (= "new" (get-in app [:params :id]))
          project (get-in app [:params :project])]
      [buttons/button-secondary {:element "a"
                                 :href (url/cost-item project "new")
                                 :disabled add?
                                 :start-icon (r/as-element
                                              [icons/content-add])}
       (tr [:asset :add-cost-item])])))

(defn- cost-item-hierarchy
  "Show hierarchy of existing cost items, grouped by fgroup and fclass."
  [{:keys [_e! _app cost-items fgroup-link-fn fclass-link-fn list-features?]
    :or {list-features? true}}]
  (r/with-let [open (r/atom #{})
               toggle-open! #(swap! open cu/toggle %)]
    [:div.cost-items-by-fgroup
     (doall
      (for [[{ident :db/ident :as fgroup} fclasses] cost-items
            :let [label (str (tr* fgroup)
                             " (" (apply + (map (comp count val) fclasses)) ")")]]
        ^{:key (str ident)}
        [container/collapsible-container
         {:on-toggle (r/partial toggle-open! ident)
          :open? (contains? @open ident)}
         (if fgroup-link-fn
           [Link {:href (fgroup-link-fn fgroup)} label]
           label)
         [:div.cost-items-by-fclass {:data-fgroup (str ident)
                                     :style {:margin-left "0.5rem"}}
          (doall
           (for [[{ident :db/ident :as fclass} cost-items] fclasses
                 :let [label (str/upper-case (tr* fclass))]]
             ^{:key (str ident)}
             [:div {:style {:margin-top "1rem"
                            :margin-left "1rem"}}
              [typography/Text2Bold
               (if fclass-link-fn
                 [Link {:href (fclass-link-fn fclass)} label]
                 label)]
              (when list-features?
                [:div.cost-items {:style {:margin-left "1rem"}}
                 (for [{oid :asset/oid} cost-items]
                   ^{:key oid}
                   [:div
                    [url/Link {:page :cost-item
                               :params {:id oid}} oid]])])]))]]))]))

(defn- cost-items-navigation [e! {:keys [page params]}]
  [:div {:class (<class common-styles/padding 1 1)}
   [select/form-select
    {:on-change #(e! (common-controller/->Navigate % params nil))
     :items [:cost-items :cost-items-totals]
     :value page
     :format-item #(tr [:asset :page %])}]])

(defn- save-boq-version-dialog [{:keys [e! on-close]}]
  (r/with-let [form-state (r/atom {})
               form-change (form/update-atom-event form-state merge)
               save-event #(cost-items-controller/->SaveBOQVersion on-close @form-state)]
    [panels/modal {:title (tr [:asset :save-boq-version])
                   :on-close on-close}

     [form/form {:e! e!
                 :value @form-state
                 :on-change-event form-change
                 :save-event save-event
                 :cancel-event (form/callback-event on-close)}
      ^{:attribute :boq-version/type
        :required? true}
      [select/select-enum {:e! e!
                           :attribute :boq-version/type
                           :database :asset}]

      ^{:attribute :boq-version/explanation
        :required? true
        :validate (fn [v]
                    (when (> (count v) 2000)
                      (tr [:asset :validate :max-length] {:max-length 2000})))}
      [text-field/TextField {:multiline true
                             :rows 4}]]]))

(defn- unlock-for-edits-dialog [{:keys [e! on-close version]}]
  [panels/modal {:title (tr [:asset :unlock-for-edits])
                 :on-close on-close}
   [:<>
    (when-let [warn (condp du/enum= (:boq-version/type version)
                      :boq-version.type/tender
                      (tr [:asset :unlock-tender-boq-warning])

                      :boq-version.type/contract
                      (tr [:asset :unlock-contract-boq-warning])

                      nil)]
      [common/info-box {:variant :warning
                        :content warn}])
    [:div {:class (<class common-styles/flex-row-space-between)}
     [buttons/button-secondary {:on-click on-close}
      (tr [:buttons :cancel])]
     [buttons/button-primary {:on-click (e! cost-items-controller/->UnlockForEdits on-close)}
      (tr [:asset :confirm-unlock-for-edits])]]]])

(defn- boq-version-statusline [e! {:keys [latest-change version]}]
  (r/with-let [dialog (r/atom nil)
               set-dialog! #(reset! dialog %)]
    (let [{:keys [user timestamp] :as chg} latest-change
          locked? (asset-model/locked? version)
          action (if locked?
                   :asset/unlock-for-edits
                   :asset/lock-version)]
      [:div {:class (<class common-styles/flex-row)
             :style {:background-color theme-colors/gray-lightest
                     :width "100%"}}

       (cond
         (asset-model/locked? version)
         [:<>
          (tr-enum (:boq-version/type version))
          " v." (:boq-version/number version)]
         chg
         [:<>
          [:b (tr [:common :last-modified]) ": "]
          (fmt/date-time timestamp)])

       (when chg
         [common/popper-tooltip
          {:title (tr [:common :last-modified])
           :variant :info
           :body [:<>
                  (fmt/date-time timestamp)
                  [:br]
                  (user-model/user-name user)]}
          [icons/alert-error-outline]])

       ;; Save or unlock button
       [when-authorized action nil
        [buttons/button-secondary
         {:disabled (some? @dialog)
          :size :small
          :on-click (r/partial set-dialog! action)}
         (tr [:asset (case action
                       :asset/unlock-for-edits :unlock-for-edits
                       :asset/lock-version :save-boq-version)])]]

       (when-let [dialog @dialog]
         (case dialog
           :asset/unlock-for-edits
           [unlock-for-edits-dialog {:e! e!
                                     :on-close (r/partial set-dialog! nil)
                                     :version version}]

           :asset/lock-version
           [save-boq-version-dialog
            {:e! e!
             :on-close (r/partial set-dialog! nil)}]))])))

(defn- export-boq-form [e! on-close project versions]
  (r/with-let [export-options
               (r/atom {:thk.project/id project
                        :boq-export/unit-prices? true
                        :boq-export/version (first versions)})
               change-event (form/update-atom-event export-options merge)
               format-version (fn [{:boq-version/keys [type number] :as v}]
                                (if-not v
                                  (tr [:asset :unofficial-version])
                                  (str (tr-enum type) " v" number)))]
    [:<>
     [form/form {:e! e!
                 :value @export-options
                 :on-change-event change-event}
      ^{:attribute :boq-export/unit-prices?}
      [select/checkbox {}]

      ^{:attribute :boq-export/version}
      [select/form-select
       {:items (into [nil] versions)
        :format-item format-version}]]

     [:div {:class (<class common-styles/flex-row-space-between)}
      [buttons/button-secondary {:on-click on-close}
       (tr [:buttons :cancel])]
      [buttons/button-primary
       {:element :a
        :target :_blank
        :href (common-controller/query-url
               :asset/export-boq
               (cu/without-nils
                (merge
                 {:boq-export/language @localization/selected-language}
                 (update @export-options
                         :boq-export/version :db/id))))}
       (tr [:asset :export-boq])]]]))

(defn- export-boq-dialog [e! app close-export-dialog!]
  [panels/modal {:title (tr [:asset :export-boq])
                 :on-close close-export-dialog!}
   [query/query
    {:e! e!
     :query :asset/version-history
     :args {:thk.project/id (get-in app [:params :project])}
     :simple-view [export-boq-form e! close-export-dialog!
                   (get-in app [:params :project])]}]])

(defn cost-items-page-structure
  [{:keys [e! app state left-panel-action hierarchy right-panel]} main-content]
  (r/with-let [export-dialog-open? (r/atom false)
               open-export-dialog! #(reset! export-dialog-open? true)
               close-export-dialog! #(reset! export-dialog-open? false)]
    [:<>
     (when @export-dialog-open?
       [export-boq-dialog e! app close-export-dialog!])

     (let [{:keys [asset-type-library]} app
           {:keys [cost-items project version] :as page-state} state
           _ (println "have right panel? " (boolean right-panel))]
       [context/provide :rotl (asset-type-library/rotl-map asset-type-library)
        [context/provide :locked? (asset-model/locked? version)
         [project-view/project-full-page-structure
          {:e! e!
           :app app
           :project project
           :export-menu-items
           [{:id "export-boq"
             :label (tr [:asset :export-boq])
             :icon [icons/file-download]
             :on-click open-export-dialog!}]
           :left-panel
           [:div {:style {:overflow-y :scroll}}
            [cost-items-navigation e! app]
            left-panel-action
            [cost-item-hierarchy
             (merge hierarchy
                    {:e! e!
                     :app app
                     :add? (= :new-cost-item (:page app))
                     :project project
                     :cost-items cost-items})]]
           :main
           [:<>
            [boq-version-statusline e! page-state]
            main-content]
           :right-panel right-panel}]]])]))

(defn- format-properties [atl properties]
  (into [:<>]
        (map (fn [[k v u]]
               [:div
                [typography/BoldGrayText k ": "]
                v
                (when u
                  (str "\u00a0" u))]))
        (asset-type-library/format-properties @localization/selected-language
                                              atl properties)))

(defn format-euro [val]
  (when val
    (str val "\u00A0€")))

(defn- cost-group-unit-price [e! value row]
  (r/with-let [price (r/atom value)
               change! #(reset! price (-> % .-target .-value))
               saving? (r/atom false)
               ;; Called after both success and error responses
               finish-saving! (fn []
                                ;; Reset price to nil, so current `value` is shown and reset to `price` on field focus
                                ;; Successful save does refresh, so the new properly formatted value is fetched from the backend
                                (reset! price nil)
                                (reset! saving? false))
               save! (fn [current-value row]
                       (when (not= current-value @price)
                         (reset! saving? true)
                         (e! (cost-items-controller/->SaveCostGroupPrice finish-saving! row @price))))
               save-on-enter! (fn [current-value row e]
                                (when (= "Enter" (.-key e))
                                  (save! current-value row)))]
    [:div {:class (<class common-styles/flex-row)
           :style {:justify-content :flex-end
                   ;; to have same padding as header for alignment
                   :padding-right "16px"}}
     [text-field/TextField {:input-style {:text-align :right
                                          :width "8rem"}
                            :value (or @price value)
                            :on-change change!
                            :on-key-down (r/partial save-on-enter! value row)
                            :on-focus #(reset! price value)
                            :on-blur (r/partial save! value row)
                            :disabled @saving?
                            :end-icon [text-field/euro-end-icon]}]]))

(defn- table-section-header [e! query listing-opts closed-set {ident :db/ident :as header-type} subtotal]
  [table/listing-table-body-component listing-opts
   [container/collapsible-container-heading
    {:container-class [(<class common-styles/flex-row)
                       (when (= "fclass" (namespace ident))
                         (<class common-styles/indent-rem 1))]
     :open? (not (closed-set ident))
     :on-toggle (e! cost-items-controller/->ToggleOpenTotals ident)}
    [:<>
     [url/Link {:page :cost-items-totals
                :query (merge query {:filter (str ident)})}
      (label header-type)]
     [:div {:style {:float :right :font-weight 700
                    :font-size "80%"}} subtotal]]]])


(defn- format-cost-table-column [{:keys [e! atl locked?]} column value row]
  (case column
    :type (label (asset-type-library/item-by-ident atl value))
    :common/status (label (asset-type-library/item-by-ident
                           atl (:db/ident value)))
    :properties (format-properties atl row)
    :quantity (str value
                   (when-let [qu (:quantity-unit row)]
                     (str " " qu)))
    :cost-per-quantity-unit (if locked?
                              (format-euro value)
                              [cost-group-unit-price e! value row])
    :total-cost (format-euro value)
    (str value)))

(defn- filter-breadcrumbs [atl query filter-fg-or-fc]
  (when-let [hierarchy (some->> filter-fg-or-fc
                                (asset-type-library/type-hierarchy atl))]
    [breadcrumbs/breadcrumbs
     (into
      [{:link [url/Link {:page :cost-items-totals :query {:filter nil}}
               (tr [:asset :totals-table :all-components])]
        :title (tr [:asset :totals-table :all-components])}]
      (for [h hierarchy
            :let [title (label h)]]
        {:link [url/Link {:page :cost-items-totals
                          :query (merge query {:filter (str (:db/ident h))})}
                title]
         :title title}))]))

(defn- wrap-atl-loader [page-fn e! {atl :asset-type-library :as app} state]
  (if-not atl
    [CircularProgress {}]
    [cost-items-map-view/with-map-context
     app (:project state)
     [page-fn e! app state]]))

(defn- cost-items-totals-page*
  [e! {query :query atl :asset-type-library :as app}
   {totals :cost-totals version :version
    closed-totals :closed-totals
    :or {closed-totals #{}}
    :as state}]
  (r/with-let [listing-state (table/listing-table-state)]
    (let [locked? (asset-model/locked? version)
          listing-opts {:columns asset-model/cost-totals-table-columns
                        :column-align asset-model/cost-totals-table-align
                        :column-label-fn #(if (= % :common/status)
                                            (label (asset-type-library/item-by-ident atl %))
                                            (tr [:asset :totals-table %]))
                        :format-column (r/partial format-cost-table-column
                                                  {:e! e! :atl atl :locked? locked?})}

          [filter-fg-or-fc filtered-cost-groups]
          (->> totals :cost-groups
               (cost-items-controller/filtered-cost-group-totals app atl))

          grouped-totals (->> filtered-cost-groups
                              (group-by (comp first :ui/group))
                              ;; sort by translated fgroup label
                              (sort-by (comp label first)))
          filter-link-fn #(url/cost-items-totals
                           {:project (get-in app [:params :project])
                            ::url/query (merge query {:filter (str (:db/ident %))})})]
      [cost-items-page-structure
       {:e! e!
        :app  app
        :state state
        :hierarchy {:fclass-link-fn filter-link-fn
                    :fgroup-link-fn filter-link-fn
                    :list-features? false}}
       [:div.cost-items-totals
        [filter-breadcrumbs atl query filter-fg-or-fc]
        [:div {:style {:max-width "25vw"}}
         [relevant-road-select
          {:e! e!
           :extra-opts ["all-roads" "no-road-reference"]
           :extra-opts-label {"all-roads" (tr [:asset :totals-table :all-roads])
                              "no-road-reference" (tr [:asset :totals-table :no-road-reference])}
           :value (get-in app [:query :road])

           :on-change (e! cost-items-controller/->SetTotalsRoadFilter)}]]
        [:div {:style {:float :right}}
         [:b
          (tr [:asset :totals-table :project-total]
              {:total (:total-cost totals)})]]

        [table/listing-table-container
         [table/listing-header (assoc listing-opts :state listing-state)]
         (doall
          (for [[fg fgroup-rows] grouped-totals
                :let [ident (:db/ident fg)
                      open? (not (closed-totals ident))]]
            ^{:key (str ident)}
            [:<>
             [table-section-header e! query listing-opts closed-totals fg
              (get-in totals [:fclass-and-fgroup-totals (:db/ident fg)])]
             (when open?
               [:<>
                (doall
                 (for [[fc fclass-rows] (group-by (comp second :ui/group)
                                                  fgroup-rows)
                       :let [ident (:db/ident fc)
                             open? (not (closed-totals ident))]]
                   ^{:key (str ident)}
                   [:<>
                    [table-section-header e! query listing-opts closed-totals fc
                     (get-in totals [:fclass-and-fgroup-totals (:db/ident fc)])]
                    (when open?
                      [table/listing-body (assoc listing-opts :rows fclass-rows)])]))])]))]]])))

(defn cost-items-totals-page [e! app state]
  [wrap-atl-loader cost-items-totals-page* e! app state])



(defn- cost-items-page* [e! app {:keys [version project] :as state}]
  [cost-items-page-structure
   {:e! e!
    :app app
    :state state
    :left-panel-action [add-cost-item app version]}
   [cost-items-map-view/project-map {:e! e!}]])

(defn cost-items-page [e! app state]
  [wrap-atl-loader cost-items-page* e! app state])

(defn cost-item-map-panel [e! cost-item]
  (when (:location/map-open? cost-item)
    [cost-items-map-view/location-map
     {:e! e!
      :value (cost-items-controller/location-form-value cost-item)
      :on-change (e! cost-items-controller/location-form-change)}]))

(defn- new-cost-item-page*
  [e! {atl :asset-type-library :as app}
   {cost-item :cost-item
    version :version relevant-roads :relevant-roads :as state}]
  [cost-items-page-structure
   {:e! e!
    :app app
    :state state
    :left-panel-action [add-cost-item app version]
    :right-panel (cost-item-map-panel e! cost-item)}
   [cost-item-form e! atl relevant-roads cost-item]])

(defn new-cost-item-page [e! app state]
  [wrap-atl-loader new-cost-item-page* e! app state])

(defn- cost-item-page*
  [e! {:keys [query params asset-type-library] :as app} {:keys [cost-item version relevant-roads] :as state}]
  (let [oid (:id params)
        component (or (get query :component)
                      (and (asset-model/component-oid? oid) oid))
        material (or (get query :material)
                     (and (asset-model/material-oid? oid) oid))]
    (if (= "new" oid)
      [new-cost-item-page e! app state]

      [cost-items-page-structure
       {:e! e!
        :app app
        :state state
        :left-panel-action [add-cost-item app version]
        :right-panel (cost-item-map-panel e! cost-item)}
       (cond material
             ^{:key material}
             [material-form e! asset-type-library material cost-item]

             component
             ^{:key component}
             [component-form e! asset-type-library component cost-item]

             :else
             ^{:key oid}
             [cost-item-form e! asset-type-library relevant-roads cost-item])])))

(defn cost-item-page [e! app state]
  [wrap-atl-loader cost-item-page* e! app state])
