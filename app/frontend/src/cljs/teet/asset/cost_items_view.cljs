(ns teet.asset.cost-items-view
  "Cost items view"
  (:require [teet.project.project-view :as project-view]
            [teet.ui.typography :as typography]
            [teet.localization :refer [tr]]
            [teet.ui.buttons :as buttons]
            [reagent.core :as r]
            [teet.ui.form :as form]
            [teet.ui.select :as select]
            [teet.asset.asset-library-view :as asset-library-view :refer [tr*]]
            [teet.ui.material-ui :refer [Grid Link CircularProgress IconButton]]
            [teet.ui.text-field :as text-field]
            [clojure.string :as str]
            [teet.util.string :as string]
            [teet.util.collection :as cu]
            [teet.ui.context :as context]
            [teet.ui.icons :as icons]
            [teet.common.responsivity-styles :as responsivity-styles]
            [herb.core :refer [<class]]
            [teet.ui.common :as common]
            [teet.ui.container :as container]
            [teet.asset.cost-items-controller :as cost-items-controller]
            [teet.asset.asset-type-library :as asset-type-library]
            [teet.asset.asset-model :as asset-model]
            [teet.ui.url :as url]
            [teet.common.common-controller :as common-controller]
            [teet.common.common-styles :as common-styles]
            [teet.ui.breadcrumbs :as breadcrumbs]
            [teet.theme.theme-colors :as theme-colors]
            [teet.map.openlayers.drag :as drag]
            [teet.map.map-view :as map-view]
            [teet.map.map-layers :as map-layers]
            [teet.map.map-features :as map-features]
            [teet.ui.table :as table]))

(defn- label [m]
  (let [l (tr* m)]
    (if (str/blank? l)
      (str (:db/ident m))
      l)))

(defn- label-for* [item rotl]
  [:span (label (rotl item))])

(defn- label-for [item]
  [context/consume :rotl [label-for* item]])

(def ^:private integer-pattern #"^\d*$")
(def ^:private decimal-pattern #"^\d+((,|\.)\d*)?$")

(defn- validate [valueType min-value max-value v]
  (when-not (str/blank? v)
    (case valueType
      ;; Check length for strings
      :db.type/string
      (cond
        (and min-value (< (count v) min-value))
        (tr [:asset :validate :min-length] {:min-length min-value})

        (and max-value (> (count v) max-value))
        (tr [:asset :validate :max-length] {:max-length max-value}))

      (:db.type/long :db.type/bigdec)
      (let [v (str/trim v)]
        (cond
          (and (= valueType :db.type/long)
               (not (re-matches integer-pattern v)))
          (tr [:asset :validate :integer-format])

          (and (= valueType :db.type/bigdec)
               (not (re-matches decimal-pattern v)))
          (tr [:asset :validate :decimal-format])

          :else
          (let [n (js/parseFloat v)]
            (cond
              (and min-value (< n min-value))
              (tr [:asset :validate :min-value] {:min-value min-value})

              (and max-value (> n max-value))
              (tr [:asset :validate :max-value] {:max-value max-value})))))

      ;; no validation otherwise
      nil)))

(defn- attribute-group [{ident :db/ident
                         cost-grouping? :attribute/cost-grouping?}]
  (cond
    cost-grouping?
    :cost-grouping

    (= "common" (namespace ident))
    :common

    :else
    :details))

(defn- attribute-grid-item [content]
  [Grid {:item true
          :md 4
          :xs 12
         :style {:padding "0.2rem"}}
   content])

(defn- location-entry []
  [:<>
   [attribute-grid-item
    [form/field :location/start-point
     [text-field/TextField {}]]]

   [attribute-grid-item
    [form/field :location/end-point
     [text-field/TextField {}]]]

   [attribute-grid-item
    [form/field :location/road-nr
     [text-field/TextField {:type :number}]]]

   [attribute-grid-item
    [form/field :location/carriageway
     [text-field/TextField {:type :number}]]]

   [attribute-grid-item
    [form/field :location/start-m
     [text-field/TextField {:type :number}]]]

   [attribute-grid-item
    [form/field :location/end-m
     [text-field/TextField {:type :number}]]]])

(defn- location-map [{:keys [e! value on-change]}]
  (r/with-let [current-value (atom value)
               dragging? (atom false)]
    ;;(project-map-view/create-project-map e! app project)
    (let [geojson (last value)]
      (reset! current-value value)
      [map-view/map-view e!
       {:on-click (fn [{c :coordinate}]
                    (let [[start end :as v] @current-value]
                      (cond
                        ;; If no start point, set it
                        (nil? start) (on-change (assoc v 0 c))

                        ;; If no end point, set it
                        (nil? end) (on-change (assoc v 1 c))

                        ;; Otherwise do nothing
                        :else nil)))
        :event-handlers (drag/drag-feature
                         {:accept (comp :map/feature :geometry)
                          :on-drag drag/on-drag-set-coordinates
                          :on-drop
                          (fn [target to]
                            (when-let [p (some-> target :geometry :map/feature
                                                 .getProperties (aget "start/end"))]
                              (on-change
                               (assoc @current-value
                                      (case p
                                        "start" 0
                                        "end" 1)
                                      to))))
                          :dragging? dragging?})
        :layers {:selected-road-geometry
                 (when-let [g geojson]
                   (map-layers/geojson-data-layer
                    "selected-road-geometry"
                    geojson
                    map-features/asset-road-line-style
                    {:fit-on-load? true
                     :fit-condition
                     (fn [_]
                       (not @dragging?))}))}}])))

(defn- attributes* [{:keys [e! attributes inherits-location? common? ctype]} rotl]
  (r/with-let [open? (r/atom #{:location :cost-grouping :common :details})
               toggle-open! #(swap! open? cu/toggle %)]
    (let [common-attrs (:attribute/_parent (:ctype/common rotl))
          attrs-groups (->> (concat (when common? common-attrs) attributes)
                            (group-by attribute-group)
                            (cu/map-vals
                             (partial sort-by (juxt (complement :attribute/mandatory?)
                                                    label))))]
      [:<>
       ;; Show location group if not inherited from parent
       (when (not inherits-location?)
         [container/collapsible-container
          {:open? (@open? :location)
           :on-toggle (r/partial toggle-open! :location)}
          [:<>
           (tr [:asset :field-group :location])
           [buttons/button-text {:style {:float :right}
                                 :on-click (r/partial toggle-open! :map)}
            (if (@open? :map)
              (tr [:asset :location :hide-map])
              (tr [:asset :location :show-map]))]]
          [Grid {:container true
                 :justify :flex-start
                 :alignItems :flex-end}
           (when (@open? :map)
             [Grid {:item true :xs 12 :md 12}
              [form/field {:attribute [:location/start-point :location/end-point
                                       :location/road-nr :location/carriageway
                                       :location/start-m :location/end-m
                                       :location/geojson]}

               [location-map {:e! e!}]]])
           [location-entry]]])
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
                    :attribute/keys [mandatory? min-value max-value]
                    :asset-schema/keys [unit] :as attr} attrs
                   :let [type (:db/ident valueType)
                         unit (if (= ident :common/quantity)
                                (:component/quantity-unit ctype)
                                unit)]]
               ^{:key (str ident)}
               [attribute-grid-item
                [form/field {:attribute ident
                             :required? mandatory?
                             :validate (r/partial validate (:db/ident valueType) min-value max-value)}
                 (if (= type :db.type/ref)
                   ;; Selection value
                   [select/form-select
                    {:id ident
                     :label (label attr)
                     :show-empty-selection? true
                     :items (mapv :db/ident (:enum/_attribute attr))
                     :format-item (comp label rotl)}]

                   ;; Text field
                   [text-field/TextField
                    {:label (label attr)
                     :end-icon (when unit
                                 (text-field/unit-end-icon unit))}])]]))]]))])))

(defn- attributes
  "Render grid of attributes."
  [opts]
  [context/consume :rotl [attributes* opts]])

(defn- add-component-menu [allowed-components add-component!]
  [:<>
   (if (> (count allowed-components) 3)
     [common/context-menu
      {:label "add component"
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
          (label c)]])))])

(defn- format-fg-and-fc [[fg fc]]
  (if (and (nil? fg)
           (nil? fc))
    ""
    (str (label fg) " / " (label fc))))

(defn group-and-class-selection [{:keys [e! on-change value atl read-only?]}]
  (let [[fg-ident fc-ident] value
        fg (if fg-ident
             (asset-type-library/item-by-ident atl fg-ident)
             (when fc-ident
               (asset-type-library/fgroup-for-fclass atl fc-ident)))
        fc (and fc-ident (asset-type-library/item-by-ident atl fc-ident))
        fgroups (:fgroups atl)]
    (if read-only?
      [typography/Heading3 (format-fg-and-fc [fg fc])]
      [Grid {:container true}
       [Grid {:item true :xs 12 :class (<class responsivity-styles/visible-desktop-only)}
        [select/select-search
         {:e! e!
          :on-change #(on-change (mapv :db/ident %))
          :placeholder (tr [:asset :feature-group-and-class-placeholder])
          :no-results (tr [:asset :no-matching-feature-classes])
          :value (when fc [fg fc])
          :format-result format-fg-and-fc
          :show-empty-selection? true
          :query (fn [text]
                   #(vec
                     (for [fg fgroups
                           fc (:fclass/_fgroup fg)
                           :let [result [fg fc]]
                           :when (string/contains-words? (format-fg-and-fc result) text)]
                       result)))}]]

       [Grid {:item true :xs 6 :class (<class responsivity-styles/visible-mobile-only)}
        [select/select-with-action
         {:show-empty-selection? true
          :items fgroups
          :format-item tr*
          :value fg
          :on-change #(on-change [(:db/ident %) nil])}]]

       [Grid {:item true :xs 6 :class (<class responsivity-styles/visible-mobile-only)}
        (when-let [{fclasses :fclass/_fgroup} fg]
          [select/select-with-action
           {:show-empty-selection? true
            :items fclasses
            :format-item tr*
            :value fc
            :on-change #(on-change [fg-ident (:db/ident %)])}])]])))

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

(defn- component-rows [{:keys [e! level components
                               delete-component!]}]
  (when (seq components)
    [:<>
     (doall
      (for [c components]
        ^{:key (str (:db/id c))}
        [:span
         [:div {:class (<class common-styles/flex-row)}
          [:div {:class (<class common-styles/flex-table-column-style
                                20 :flex-start 1 nil)}
           [component-tree-level-indent level]
           [url/Link {:page :cost-item
                      :params {:id (:asset/oid c)}}
            (:asset/oid c)]]
          [:div {:class (<class common-styles/flex-table-column-style
                                20 :flex-start 0 nil)}
           [label-for (:component/ctype c)]]
          [:div {:class (<class common-styles/flex-table-column-style
                                20 :flex-start 0 nil)}
           (str "id: " (:db/id c))]
          [:div {:class (<class common-styles/flex-table-column-style
                                40 :flex-end 0 nil)}
           [buttons/delete-button-with-confirm
            {:small? true
             :icon-position :start
             :action (r/partial delete-component! (:db/id c))}
            (tr [:buttons :delete])]]]
         [component-rows {:e! e!
                          :components (:component/components c)
                          :level (inc (or level 0))}]]))]))

(defn- components-tree
  "Show listing of all components (and their subcomponents recursively) for the asset."
  [{:keys [e! asset]}]
  [:<>
   [typography/Heading3 (tr [:asset :components :label])
    (str " (" (cu/count-matching-deep :component/ctype (:asset/components asset)) ")")]
   [component-rows {:e! e!
                    :components (:asset/components asset)
                    :delete-component! (e! cost-items-controller/->DeleteComponent)}]])

(defn- form-paper
  "Wrap the form input portion in a light gray paper."
  [component]
  [:div.cost-item-form {:style {:background-color theme-colors/gray-lightest
                                :margin-top "1rem"
                                :padding "1rem"}}
   component])

(defn- cost-item-form [e! atl {:asset/keys [fclass] :as form-data}]
  (r/with-let [initial-data form-data
               new? (nil? form-data)

               cancel-event (if new?
                              #(common-controller/->SetQueryParam :id nil)

                              ;; Update with initial data to cancel
                              (r/partial
                               cost-items-controller/->UpdateForm initial-data))]
    (let [feature-class (when fclass
                          (asset-type-library/item-by-ident atl fclass))]
      [:<>
       [form/form2
        {:e! e!
         :on-change-event cost-items-controller/->UpdateForm
         :value form-data
         :save-event cost-items-controller/->SaveCostItem
         :cancel-event cancel-event
         :disable-buttons? (= initial-data form-data)}

        [form/field {:attribute [:fgroup :asset/fclass]}
         [group-and-class-selection
          {:e! e!
           :atl atl
           :read-only? (seq (dissoc form-data :asset/fclass :fgroup :db/id))}]]

        (when feature-class
          ;; Attributes for asset
          [form-paper [attributes
                       {:e! e!
                        :attributes (some-> feature-class :attribute/_parent)
                        :common? false
                        :inherits-location? false}]])

        [form/footer2]]

       ;; Components (show only for existing)
       (when initial-data
         [:<>
          [components-tree {:e! e!
                            :asset form-data
                            :allowed-components (:ctype/_parent feature-class)}]

          [add-component-menu
           (asset-type-library/allowed-component-types atl fclass)
           (e! cost-items-controller/->AddComponent)]])])))

(defn- component-form-navigation [atl [asset :as component-path]]
  [:<>
   [breadcrumbs/breadcrumbs
    (for [p component-path]
      {:link [url/Link {:page :cost-item
                        :params {:id (:asset/oid asset)}}
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
                                   (:component/ctype %)))
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
                       :common? true
                       :ctype ctype}]]]

        [:div {:class (<class common-styles/flex-row-space-between)
               :style {:align-items :center}}
         [url/Link {:page :cost-item
                    :params {:id (:asset/oid cost-item-data)}}
          (tr [:asset :back-to-cost-item] {:name (:common/name cost-item-data)})]
         [form/footer2]]]

       (when (not new?)
         (let [allowed-components (asset-type-library/allowed-component-types atl ctype)]
           (when (seq allowed-components)
             [add-component-menu allowed-components
              (e! cost-items-controller/->AddComponent)])))])))

(defn component-form
  [e! atl component-oid cost-item-data]
  ;; Delay mounting of component form until it is in app state.
  ;; This is needed when creating a new component and navigating to it
  ;; after save, as refresh fetch will happen after navigation.
  (if (nil? (asset-model/find-component-path cost-item-data component-oid))
    [CircularProgress]
    [component-form* e! atl component-oid cost-item-data]))

(defn- cost-item-hierarchy
  "Show hierarchy of existing cost items, grouped by fgroup and fclass."
  [{:keys [e! app cost-items add? project]}]
  (r/with-let [open (r/atom #{})
               toggle-open! #(swap! open cu/toggle %)]
    [:div
     [buttons/button-secondary {:element "a"
                                :href (url/cost-item (:thk.project/id project) "new")
                                :disabled add?
                                :start-icon (r/as-element
                                             [icons/content-add])}
      (tr [:asset :add-cost-item])]

     [:div.cost-items-by-fgroup
      (doall
       (for [[{ident :db/ident :as fgroup} fclasses] cost-items]
         ^{:key (str ident)}
         [container/collapsible-container
          {:on-toggle (r/partial toggle-open! ident)
           :open? (contains? @open ident)}
          (str (tr* fgroup) " (" (apply + (map (comp count val) fclasses)) ")")
          [:div.cost-items-by-fclass {:data-fgroup (str ident)
                                      :style {:margin-left "0.5rem"}}
           (doall
            (for [[{ident :db/ident :as fclass} cost-items] fclasses]
              ^{:key (str ident)}
              [:div {:style {:margin-top "1rem"
                             :margin-left "1rem"}}
               [typography/Text2Bold (str/upper-case (tr* fclass))]
               [:div.cost-items {:style {:margin-left "1rem"}}
                (for [{oid :asset/oid} cost-items]
                  ^{:key oid}
                  [:div
                   [url/Link {:page :cost-item
                              :params {:id oid}} oid]])]]))]]))]]))

(defn cost-items-page-structure
  [e! app {:keys [cost-items asset-type-library project]}
   main-content]
  [context/provide :rotl (asset-type-library/rotl-map asset-type-library)
   [project-view/project-full-page-structure
    {:e! e!
     :app app
     :project project
     :left-panel [cost-item-hierarchy {:e! e!
                                       :app app
                                       :add? (= :new-cost-item (:page app))
                                       :project project
                                       :cost-items cost-items}]
     :main main-content}]])

(defn- format-properties [atl properties]
  (let [id->def (partial asset-type-library/item-by-ident atl)
        attr->val (dissoc (cu/map-keys id->def properties) nil)]
    (into [:<>]
          (map (fn [[k v]]
                 [:div
                  [typography/BoldGrayText (label k) ": "]
                  (case (get-in k [:db/valueType :db/ident])
                    :db.type/ref (some-> v :db/ident id->def label)
                    (str v))
                  (when-let [u (:asset-schema/unit k)]
                    (str " " u))]))
          (sort-by (comp label key) attr->val))))

(defn format-euro [val]
  (when val
    (str val " €")))

(defn cost-group-unit-price [e! initial-value row]
  (r/with-let [price (r/atom initial-value)
               change! #(reset! price (-> % .-target .-value))
               save! #(when (not= initial-value @price)
                        (e! (cost-items-controller/->SaveCostGroupPrice row @price)))
               save-on-enter! #(when (= "Enter" (.-key %))
                                 (save!))]
    (let [changed? (not= @price initial-value)]
      [:div {:class (<class common-styles/flex-row)}
       [text-field/TextField {:value @price
                              :on-change change!
                              :on-key-down save-on-enter!
                              :end-icon [text-field/euro-end-icon]}]
       (when changed?
         [IconButton {:size :small
                      :on-click save!}
          [icons/action-done {:font-size :small}]])])))

(defn cost-items-totals-page
  [e! app {atl :asset-type-library totals :cost-totals :as state}]
  [cost-items-page-structure
   e! app state
   [:div.cost-items-totals
    [:div {:style {:float :right}} "total cost: " (:total-cost totals) " €"]
    [:div
     [table/listing-table
      {:label "Cost summary"
       :data (:cost-groups totals)
       :columns asset-model/cost-totals-table-columns
       :column-align asset-model/cost-totals-table-align
       :format-column (fn [column value row]
                        (case column
                          :type (label (asset-type-library/item-by-ident atl value))
                          :properties (format-properties atl row)
                          :quantity (str value
                                         (when-let [qu (:quantity-unit row)]
                                           (str " " qu)))
                          :cost-per-quantity-unit [cost-group-unit-price e! value row]
                          :total-cost (format-euro value)
                          (str value)))}]]]])

(defn cost-items-page [e! app state]
  [cost-items-page-structure
   e! app state
   [:div "TODO: map here"]])

(defn new-cost-item-page
  [e! app {atl :asset-type-library cost-item :cost-item :as state}]
  [cost-items-page-structure
   e! app state
   [cost-item-form e! atl cost-item]])

(defn cost-item-page
  [e! {:keys [query params] :as app} {:keys [asset-type-library cost-item] :as state}]
  (let [oid (:id params)
        component (or (get query :component)
                      (and (asset-model/component-oid? oid) oid))]
    (if (= "new" oid)
      [new-cost-item-page e! app state]

      [cost-items-page-structure
       e! app state
       (if component
         ^{:key component}
         [component-form e! asset-type-library component cost-item]

         ^{:key oid}
         [cost-item-form e! asset-type-library cost-item])])))
