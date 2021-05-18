(ns teet.asset.cost-items-view
  "Cost items view"
  (:require [teet.project.project-view :as project-view]
            [teet.ui.typography :as typography]
            [teet.localization :refer [tr tr-enum] :as localization]
            [teet.ui.buttons :as buttons]
            [reagent.core :as r]
            [teet.ui.form :as form]
            [teet.ui.select :as select]
            [teet.asset.asset-ui :as asset-ui
             :refer [tr* label label-for select-fgroup-and-fclass]]
            [teet.ui.material-ui :refer [Grid CircularProgress Link]]
            [teet.ui.text-field :as text-field]
            [clojure.string :as str]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]
            [teet.ui.context :as context]
            [teet.ui.icons :as icons]
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
            [teet.ui.table :as table]
            [teet.user.user-model :as user-model]
            [teet.ui.format :as fmt]
            [teet.ui.panels :as panels]
            [teet.ui.query :as query]
            [teet.ui.date-picker :as date-picker]
            [teet.authorization.authorization-check :refer [when-authorized]]
            [teet.ui.project-context :as project-context]
            [teet.asset.cost-items-map-view :as cost-items-map-view]))



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

(defn- attribute-group [{ident :db/ident
                         cost-grouping? :attribute/cost-grouping?}]
  (cond
    cost-grouping?
    :cost-grouping

    (= "common" (namespace ident))
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

(defn- location-entry [e! locked? selected-road-nr]
  (let [input-textfield (if locked? display-input text-field/TextField)]
    [:<>
     [Grid {:item true
            :md 5
            :xs 12
            :style {:padding "0.2rem"}}
      [form/field :location/road-nr
       [relevant-road-select {:e! e!}]]]

     [Grid {:item true
            :container true
            :md 7
            :xs 12
            :style {:padding "0.2rem"}}
      [Grid {:item true
             :md 2
             :xs 12}
       [form/field :location/carriageway
        [carriageway-for-road-select {:e! e!} selected-road-nr]]]]

     [Grid {:item true
            :md 3
            :xs 12
            :style {:padding "0.2rem"}}
      [form/field :location/start-point
       [input-textfield {}]]]

     [Grid {:item true
            :md 3
            :xs 12
            :style {:padding "0.2rem"}}
      [form/field :location/start-km
       [input-textfield {}]]]

     [Grid {:item true
            :md 3
            :xs 12
            :style {:padding "0.2rem"}}
      [form/field :location/start-offset-m
       [input-textfield {}]]]

     [Grid {:item true
            :md 3
            :xs 12
            :style {:padding "0.2rem"}}
      [form/field :location/end-point
       [input-textfield {}]]]

     [Grid {:item true
            :md 3
            :xs 12
            :style {:padding "0.2rem"}}
      [form/field :location/end-km
       [input-textfield {}]]]

     [Grid {:item true
            :md 3
            :xs 12
            :style {:padding "0.2rem"}}
      [form/field :location/end-offset-m
       [input-textfield {}]]]]))

(defn- attributes* [{:keys [e! attributes component-oid cost-item-data inherits-location?
                            common? ctype]}
                    rotl locked?]
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
                                       :location/start-km :location/end-km
                                       :location/geojson]}

               [cost-items-map-view/location-map {:e! e!}]]])
           [location-entry e! locked? (:location/road-nr cost-item-data)]]])
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
                         unit (if (= ident :common/quantity)
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

(defn- add-component-menu* [allowed-components add-component! locked?]
  (when-not locked?
    [:<>
     (if (> (count allowed-components) 3)
       [common/context-menu
        {:label (tr [:asset :add-component])
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

(defn- add-component-menu [allowed-components add-component!]
  [context/consume :locked?
   [add-component-menu* allowed-components add-component!]])





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
                               delete-component!]}]
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
            (:asset/oid c)]]
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
                :action (r/partial delete-component! (:db/id c))}
               (tr [:buttons :delete])]])]]
         [component-rows {:e! e!
                          :locked? locked?
                          :components (:component/components c)
                          :level (inc (or level 0))}]]))]))

(defn- components-tree*
  "Show listing of all components (and their subcomponents recursively) for the asset."
  [{:keys [e! asset]} locked?]
  [:<>
   [typography/Heading3 (tr [:asset :components :label])
    (str " (" (cu/count-matching-deep :component/ctype (:asset/components asset)) ")")]
   [component-rows {:e! e!
                    :locked? locked?
                    :components (:asset/components asset)
                    :delete-component! (e! cost-items-controller/->DeleteComponent)}]])

(defn- components-tree [opts]
  [context/consume :locked?
   [components-tree* opts]])

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
                        ;; TODO: cost-item-data here as well
                        :common? false
                        :inherits-location? false
                        :relevant-roads relevant-roads}]])

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
  [{:keys [e! app cost-items fgroup-link-fn fclass-link-fn list-features?]
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
                  [:div (fmt/date-time timestamp)]
                  [:div (user-model/user-name user)]]}
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
  [{:keys [e! app state left-panel-action hierarchy]} main-content]
  (r/with-let [export-dialog-open? (r/atom false)
               open-export-dialog! #(reset! export-dialog-open? true)
               close-export-dialog! #(reset! export-dialog-open? false)]
    [:<>
     (when @export-dialog-open?
       [export-boq-dialog e! app close-export-dialog!])

     (let [{:keys [asset-type-library]} app
           {:keys [cost-items project version] :as page-state} state]
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
            main-content]}]]])]))

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

(defn cost-group-unit-price [e! value row]
  (r/with-let [initial-value value
               price (r/atom initial-value)
               change! #(reset! price (-> % .-target .-value))
               saving? (r/atom false)
               save! (fn [row]
                       (when (not= initial-value @price)
                         (reset! saving? true)
                         (e! (cost-items-controller/->SaveCostGroupPrice row @price))))
               save-on-enter! (fn [row e]
                                (when (= "Enter" (.-key e))
                                  (save! row)))]
    (when (not= value initial-value)
      (reset! saving? false))
    [:div {:class (<class common-styles/flex-row)
           :style {:justify-content :flex-end
                   ;; to have same padding as header for alignment
                   :padding-right "16px"}}
     [text-field/TextField {:input-style {:text-align :right
                                          :width "8rem"}
                            :value @price
                            :on-change change!
                            :on-key-down (r/partial save-on-enter! row)
                            :on-blur (r/partial save! row)
                            :disabled @saving?
                            :end-icon [text-field/euro-end-icon]}]]))

(defn- table-section-header [e! listing-opts closed-set {ident :db/ident :as header-type} subtotal]
  [table/listing-table-body-component listing-opts
   [container/collapsible-container-heading
    {:container-class [(<class common-styles/flex-row)
                       (when (= "fclass" (namespace ident))
                         (<class common-styles/indent-rem 1))]
     :open? (not (closed-set ident))
     :on-toggle (e! cost-items-controller/->ToggleOpenTotals ident)}
    [:<>
     [url/Link {:page :cost-items-totals
                :query {:filter (str ident)}}
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

(defn- filter-breadcrumbs [atl filter-fg-or-fc]
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
                          :query {:filter (str (:db/ident h))}}
                title]
         :title title}))]))

(defn- wrap-atl-loader [page-fn e! {atl :asset-type-library :as app} state]
  (if-not atl
    [CircularProgress {}]
    [cost-items-map-view/with-map-context
     app (:project state)
     [page-fn e! app state]]))

(defn- cost-items-totals-page*
  [e! {atl :asset-type-library :as app}
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
                            ::url/query {:filter (str (:db/ident %))}})]
      [cost-items-page-structure
       {:e! e!
        :app  app
        :state state
        :hierarchy {:fclass-link-fn filter-link-fn
                    :fgroup-link-fn filter-link-fn
                    :list-features? false}}
       [:div.cost-items-totals
        [filter-breadcrumbs atl filter-fg-or-fc]
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
             [table-section-header e! listing-opts closed-totals fg
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
                    [table-section-header e! listing-opts closed-totals fc
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

(defn- new-cost-item-page*
  [e! {atl :asset-type-library :as app}
   {cost-item :cost-item
    version :version relevant-roads :relevant-roads :as state}]
  [cost-items-page-structure
   {:e! e!
    :app app
    :state state
    :left-panel-action [add-cost-item app version]}
   [cost-item-form e! atl relevant-roads cost-item]])

(defn new-cost-item-page [e! app state]
  [wrap-atl-loader new-cost-item-page* e! app state])

(defn- cost-item-page*
  [e! {:keys [query params asset-type-library] :as app} {:keys [cost-item version relevant-roads] :as state}]
  (let [oid (:id params)
        component (or (get query :component)
                      (and (asset-model/component-oid? oid) oid))]
    (if (= "new" oid)
      [new-cost-item-page e! app state]

      [cost-items-page-structure
       {:e! e!
        :app app
        :state state
        :left-panel-action [add-cost-item app version]}
       (if component
         ^{:key component}
         [component-form e! asset-type-library component cost-item]

         ^{:key oid}
         [cost-item-form e! asset-type-library relevant-roads cost-item])])))

(defn cost-item-page [e! app state]
  [wrap-atl-loader cost-item-page* e! app state])
