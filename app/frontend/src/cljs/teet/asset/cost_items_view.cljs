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
            [teet.localization :refer [tr] :as localization]
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
            [teet.ui.select :as select]
            [teet.ui.text-field :as text-field]
            [teet.ui.typography :as typography]
            [teet.ui.url :as url]
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

(defn- carriageway-for-road-select* [opts selected-road-nr relevant-roads]
  [select/form-select
   (merge (asset-ui/number-value opts)
          {:show-empty-selection? true
           :items (or (cost-items-controller/carriageways-for-road
                       selected-road-nr
                       relevant-roads)
                      [1])
           :format-item str})])

(defn- carriageway-for-road-select [opts selected-road-nr]
  [asset-ui/with-relevant-roads
   opts
   [carriageway-for-road-select* opts selected-road-nr]])

(defn- location-entry [e! locked? selected-road-nr single-point?]
  (let [input-textfield (if locked? display-input text-field/TextField)]
    [:<>
     [Grid {:item true
            :md 4}]
     [Grid {:item true
            :md (if single-point? 8 4)
            :xs 12
            :style {:padding "0.2rem"}}
      [form/field {:attribute :location/start-point
                   :required? true}
       [input-textfield {}]]]

     (when-not single-point?
       [Grid {:item true
              :md 4
              :xs 12
              :style {:padding "0.2rem"}}
        [form/field {:attribute :location/end-point
                     :required? true}
         [input-textfield {}]]])

     [Grid {:item true
            :md 3
            :xs 12
            :style {:padding "0.2rem"}}
      [form/field :location/road-nr
       (if locked?
         [input-textfield {}]
         [asset-ui/relevant-road-select {:e! e!}])]]

     [Grid {:item true
            :md 1
            :xs 12
            :style {:padding "0.2rem"}}
      [form/field :location/carriageway
       (if locked?
         [input-textfield {}]
         [carriageway-for-road-select {:e! e!} selected-road-nr])]]

     [Grid {:item true
            :md (if single-point? 4 2)
            :xs 12
            :style {:padding "0.2rem"}}
      [form/field {:attribute :location/start-km
                   :required? true}
       [input-textfield {:end-icon (text-field/unit-end-icon "km")}]]]

     [Grid {:item true
            :md (if single-point? 4 2)
            :xs 12
            :style {:padding "0.2rem"}}
      [form/field :location/start-offset-m
       [input-textfield {:end-icon (text-field/unit-end-icon "m")}]]]


     (when-not single-point?
       [Grid {:item true
              :md 2
              :xs 12
              :style {:padding "0.2rem"}}
        [form/field :location/end-km
         [input-textfield {:end-icon (text-field/unit-end-icon "km")}]]])

     (when-not single-point?
       [Grid {:item true
              :md 2
              :xs 12
              :style {:padding "0.2rem"}}
        [form/field :location/end-offset-m
         [input-textfield {:end-icon (text-field/unit-end-icon "m")}]]])

     (when-not locked?
       [Grid {:item true
              :md 12 :xs 12}
        [form/field :location/single-point?
         [select/checkbox {}]]])]))

(defn attributes* [{:keys [e! attributes component-oid cost-item-data inherits-location?
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
                               delete-fn children-label-fn
                               link-fn]}]
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
           [url/Link (if link-fn
                       (link-fn (:asset/oid c))
                       {:page :cost-item
                        :params {:id (:asset/oid c)}})
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
                          :level (inc (or level 0))
                          :link-fn link-fn}]]))]))

(defn- children-tree*
  "Show listing of all components (and their subcomponents recursively) for the asset."
  [{:keys [e! parent label-fn children-fn children-label-fn delete-fn
           link-fn]} locked?]
  [:<>
   [typography/Heading3 (label-fn parent)]
   [component-rows {:e! e!
                    :locked? locked?
                    :components (children-fn parent)
                    :children-label-fn children-label-fn
                    :delete-fn delete-fn
                    :link-fn link-fn}]])

(defn components-tree
  "Relevant options:
   - label-fn takes the asset, produces
   - delete-fn when called with db/id deletes the said entity
   - link-fn  fn from OID to link description map (including :page and :params)"
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
                          :children-fn #(or (not-empty (:asset/components %))
                                            (not-empty (:component/components %)))
                          :delete-fn (e! cost-items-controller/->DeleteComponent))]])

(defn- material-label [atl m]
  (->> m :material/type (asset-type-library/item-by-ident atl) tr*))

(defn materials-list
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
           [components-tree component-data {:e! e!}]
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
                       :common :ctype/material
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

(defn- cost-items-page* [e! app {:keys [version project] :as state}]
  [asset-ui/cost-items-page-structure
   {:e! e!
    :app app
    :state state
    :left-panel-action [add-cost-item app version]}
   [cost-items-map-view/project-map {:e! e!}]])

(defn cost-items-page [e! app state]
  [asset-ui/wrap-atl-loader cost-items-page* e! app state])

(defn cost-item-map-panel [e! form-state]
  (when (:location/map-open? form-state)
    [cost-items-map-view/location-map
     {:e! e!
      :value (cost-items-controller/location-form-value form-state)
      :on-change (e! cost-items-controller/location-form-change)}]))

(defn- new-cost-item-page*
  [e! {atl :asset-type-library :as app}
   {cost-item :cost-item
    version :version relevant-roads :relevant-roads :as state}]
  [asset-ui/cost-items-page-structure
   {:e! e!
    :app app
    :state state
    :left-panel-action [add-cost-item app version]
    :right-panel (cost-item-map-panel e! cost-item)}
   [cost-item-form e! atl relevant-roads cost-item]])

(defn new-cost-item-page [e! app state]
  [asset-ui/wrap-atl-loader new-cost-item-page* e! app state])

(defn- cost-item-page*
  [e! {:keys [query params asset-type-library] :as app} {:keys [cost-item version relevant-roads] :as state}]
  (let [oid (:id params)
        component (or (get query :component)
                      (and (asset-model/component-oid? oid) oid))
        material (or (get query :material)
                     (and (asset-model/material-oid? oid) oid))]
    (if (= "new" oid)
      [new-cost-item-page e! app state]

      [asset-ui/cost-items-page-structure
       {:e! e!
        :app app
        :state state
        :left-panel-action [add-cost-item app version]
        :right-panel (cost-item-map-panel e! (if component
                                               (last (asset-model/find-component-path cost-item component))
                                               cost-item))}
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
  [asset-ui/wrap-atl-loader cost-item-page* e! app state])
