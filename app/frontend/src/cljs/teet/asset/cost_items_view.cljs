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
            [datafrisk.core :as df]
            [teet.ui.material-ui :refer [Grid Link Breadcrumbs]]
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
            [teet.ui.url :as url]
            [teet.ui.query :as query]
            [teet.common.common-controller :as common-controller]
            [teet.common.common-styles :as common-styles]
            [teet.util.datomic :as du]
            [teet.ui.breadcrumbs :as breadcrumbs]
            [teet.theme.theme-colors :as theme-colors]))


(defonce next-id (atom 0))

(defn- next-id! [prefix]
  (str prefix (swap! next-id inc)))

(defn- label [m]
  (let [l (tr* m)]
    (if (str/blank? l)
      (str (:db/ident m))
      l)))

(defn- label-for* [item rotl]
  [:span (label (rotl item))])

(defn- label-for [item]
  [context/consume :rotl [label-for* item]])

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
      (let [n (js/parseFloat v)]
        (cond
          (and min-value (< n min-value))
          (tr [:asset :validate :min-value] {:min-value min-value})

          (and max-value (> n max-value))
          (tr [:asset :validate :max-value] {:max-value max-value})))

      ;; no validation otherwise
      nil)))

(defn- attribute-group [{ident :db/ident
                         cost-grouping? :attribute/cost-grouping?}]
  (cond
    (= :common/name ident) ; PENDING: location coming to this group
    :name-and-location

    cost-grouping?
    :cost-grouping

    (= "common" (namespace ident))
    :common

    :else
    :details))

(defn- attributes* [e! attrs rotl]
  (r/with-let [open? (r/atom #{:name-and-location :cost-grouping :common :details})
               toggle-open! #(swap! open? cu/toggle %)]
    (let [common-attrs (:attribute/_parent (:ctype/common rotl))
          attrs-groups (->> (concat common-attrs attrs)
                            (group-by attribute-group)
                            (cu/map-vals
                             (partial sort-by (juxt (complement :attribute/mandatory?)
                                                    label))))]
      [:<>
       (doall
        (for [g [:name-and-location :cost-grouping :common :details]
              :let [attrs (attrs-groups g)]
              :when (seq attrs)]
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
                   :let [type (:db/ident valueType)]]
               [Grid {:item true
                      :md 4
                      :xs 12
                      :style {:padding "0.2rem"}}
                [form/field {:attribute ident
                             :required? mandatory?
                             :validate (r/partial validate (:db/ident valueType) min-value max-value)}
                 (if (= type :db.type/ref)
                   ;; Selection value
                   [select/form-select
                    {:label (label attr)
                     :show-empty-selection? true
                     :items (mapv :db/ident (:enum/_attribute attr))
                     :format-item (comp label rotl)}]

                   ;; Text field
                   [text-field/TextField
                    ;; parse based on type
                    (merge
                     {:label (label attr)
                      :end-icon (when unit
                                  (text-field/unit-end-icon unit))}
                     (case type
                       (:db.type/long :db.type/bigdec) {:type :number}
                       nil))])]]))]]))])))

(defn- attributes
  "Render grid of attributes."
  [e! attrs]
  [context/consume :rotl [attributes* e! attrs]])

(declare components)

(defn- component
  "Render one component."
  [{:keys [e! component on-change]} rotl]
  ;; form uses the on-change-event constructor given at the 1st render
  ;; so we need to keep track of the on-change here and use it in the
  ;; change event
  (r/with-let [on-change-atom (atom on-change)
               on-change-event (form/callback-change-event #(@on-change-atom %))
               expanded? (r/atom true)
               toggle-expand! #(swap! expanded? not)
               delete! #(@on-change-atom {:deleted? true})]
    (reset! on-change-atom on-change)
    (let [ctype (get rotl (:component/ctype component))]
      [container/collapsible-container
       {:on-toggle toggle-expand!
        :open? @expanded?
        :side-component (when @expanded?
                          [buttons/button-warning
                           {:on-click delete!
                            :start-icon (r/as-element
                                         [icons/action-delete])}
                           (tr [:buttons :delete])])}

       [typography/Heading2 (str (label ctype)
                                 (when-let [name (:common/name component)]
                                   (str ": " name)))]
       [form/form2 {:e! e!
                    :on-change-event on-change-event
                    :value component}
        [attributes e! (:attribute/_parent ctype)]

        [form/field :component/components
         [components {:e! e! :allowed-components (:ctype/_parent ctype)}]]]])))

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

(defn group-and-class-selection [{:keys [e! on-change value fgroups read-only? name]}]
  (let [[fg fc] value]
    (if read-only?
      [typography/Heading3 (str (format-fg-and-fc value)
                                (when name
                                  (str ": " name)))]
      [Grid {:container true}
       [Grid {:item true :xs 12 :class (<class responsivity-styles/visible-desktop-only)}
        [select/select-search
         {:e! e!
          :on-change on-change
          :placeholder (tr [:asset :feature-group-and-class-placeholder])
          :no-results (tr [:asset :no-matching-feature-classes])
          :value value
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
          :on-change #(on-change [% nil])}]]

       [Grid {:item true :xs 6 :class (<class responsivity-styles/visible-mobile-only)}
        (when-let [{fclasses :fclass/_fgroup} fg]
          [select/select-with-action
           {:show-empty-selection? true
            :items fclasses
            :format-item tr*
            :value fc
            :on-change #(on-change [fg %])}])]])))

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

(defn- component-rows [{:keys [e! level components]}]
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
           [Link {:href (url/set-query-param :component (str (:db/id c)))}
            (:common/name c)]]
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
             :action (e! cost-items-controller/->DeleteComponent (:db/id c))}
            (tr [:buttons :delete])]]]
         [component-rows {:e! e!
                          :components (:component/components c)
                          :level (inc (or level 0))}]]))]))

(defn- components-tree
  "Show listing of all components (and their subcomponents recursively) for the asset."
  [{:keys [e! asset allowed-components]}]
  [:<>
   [typography/Heading3 (tr [:asset :components :label])
    (str " (" (cu/count-matching-deep :component/ctype (:asset/components asset)) ")")]
   [component-rows {:e! e!
                    :components (:asset/components asset)}]])

(defn- form-paper
  "Wrap the form input portion in a light gray paper."
  [component]
  [:div.cost-item-form {:style {:background-color theme-colors/gray-lightest
                                :margin-top "1rem"
                                :padding "1rem"}}
   component])

(defn- cost-item-form [e! atl initial-data]
  (r/with-let [new? (nil? initial-data)
               form-data (r/atom (or initial-data
                                     {:db/id (next-id! "costitem")}))
               on-change-event (form/update-atom-event
                                form-data
                                cu/deep-merge)
               save-event (cost-items-controller/save-asset-event form-data)
               cancel-event (if new?
                              #(common-controller/->SetQueryParam :id nil)
                              (form/update-atom-event form-data (constantly initial-data)))]
    (let [[_feature-group feature-class] (some-> @form-data :feature-group-and-class)]
      [:<>

       [form/form2
        {:e! e!
         :on-change-event on-change-event
         :value @form-data
         :save-event save-event
         :cancel-event cancel-event
         :disable-buttons? (= initial-data @form-data)}

        [form/field {:attribute :feature-group-and-class}
         [group-and-class-selection {:e! e!
                                     :fgroups (:fgroups atl)
                                     :read-only? (seq (dissoc @form-data :feature-group-and-class :db/id))
                                     :name (:common/name @form-data)}]]

        (when feature-class
          ;; Attributes for asset
          [form-paper [attributes e! (some-> feature-class :attribute/_parent)]])

        [form/footer2]]

       ;; Components
       (when initial-data
         [:<>
          [components-tree {:e! e!
                            :asset initial-data
                            :allowed-components (:ctype/_parent feature-class)}]

          [add-component-menu
           (asset-type-library/allowed-component-types atl feature-class)
           #(e! (common-controller/->SetQueryParam :component (str "-" (name %))))]])

       ;; FIXME: remove when finalizing form
       [df/DataFriskView @form-data]])))

(defn- edit-cost-item-form [e! asset-type-library {fclass-ident :asset/fclass :as cost-item-data}]
  (let [fclass= (fn [fc] (= (:db/ident fc) fclass-ident))
        fgroup (cu/find-> (:fgroups asset-type-library)
                          #(cu/find-> (:fclass/_fgroup %) fclass=))
        fclass (cu/find-> (:fclass/_fgroup fgroup) fclass=)]
    [cost-item-form e! asset-type-library
     (assoc cost-item-data
            :feature-group-and-class [fgroup fclass])]))

(defn- with-cost-item [e! id view-component]
  [query/query {:e! e!
                :query :asset/cost-item
                :args {:db/id (common-controller/->long id)}
                :simple-view view-component}])

(defn- find-component-path
  "Return vector containing all parents of component from asset to the component.
  For example:
  [a c1 c2 c3]
  where a is the asset, that has component c1
  c1 has child component c2
  and c2 has child component c3 (the component we want)"
  [asset component-id]
  (let [containing
        (fn containing [path here]
          (let [cs (concat (:asset/components here)
                           (:component/components here))]
            (if-let [c (some #(when (du/id= component-id
                                            (:db/id %))
                                %) cs)]
              ;; we found the component at this level
              (into path [here c])

              ;; not found here, recurse
              (first
               (for [sub cs
                     :let [sub-path (containing (conj path here) sub)]
                     :when sub-path]
                 sub-path)))))]

    (containing [] asset)))

(defn- component-form-navigation [atl [asset :as component-path]]
  [:<>
   [breadcrumbs/breadcrumbs
    (for [p component-path]
      {:link [url/Link {:page :cost-items
                        :query {:id (str (:db/id asset))
                                :component (when-not (:asset/fclass p)
                                             (str (:db/id p)))}}
              (:common/name p)]
       :title (if-let [name (:common/name p)]
                name
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

(defn- component-form [e! atl asset-id component-id cost-item-data]
  ;; Component id can have two parts (separated by dash)
  ;; <component id number>-<subcomponent type name>
  ;; If subcomponent type name is specified, then we are adding a component
  ;; of that type. If component id number is omitted, then the component
  ;; is being added to the asset itself.
  (r/with-let [[component-id subcomponent-name] (str/split component-id #"-")

               ;; If component-id is blank, adding this component to asset level
               component-path (if (str/blank? component-id)
                                [cost-item-data]
                                (find-component-path cost-item-data component-id))

               ;; When adding a subcomponent, add it to the path
               component-path (if subcomponent-name
                                (conj component-path
                                      {:db/id (next-id! subcomponent-name)
                                       :component/ctype (keyword "ctype" subcomponent-name)})
                                component-path)
               component-data (last component-path)
               new? (not (number? (:db/id component-data)))
               ctype (cu/find-matching #(and (= :asset-schema.type/ctype (get-in % [:asset-schema/type :db/ident]))
                                             (= (:db/ident %) (:component/ctype component-data)))
                                       atl)
               form-data (r/atom component-data)
               on-change-event (form/update-atom-event
                                form-data
                                cu/deep-merge)
               save-event #(cost-items-controller/->SaveComponent
                            ;; Take id of the parent component or asset
                            (:db/id (nth component-path (- (count component-path) 2)))
                            @form-data)
               cancel-event (if new?
                              #(common-controller/->SetQueryParam :component nil)
                              (form/update-atom-event form-data (constantly component-data)))]
    [:<>
     [component-form-navigation atl component-path]

     [form/form2
      {:e! e!
       :on-change-event on-change-event
       :value @form-data
       :save-event save-event
       :cancel-event cancel-event
       :disable-buttons? (= component-data @form-data)}

      ;; Attributes for component
      [form-paper
       [:<>
        (label ctype)
        [attributes e! (some-> ctype :attribute/_parent)]]]

      [:div {:class (<class common-styles/flex-row-space-between)
             :style {:align-items :center}}
       [url/Link {:page :cost-items
                  :query {:id asset-id
                          :component nil}}
        (tr [:asset :back-to-cost-item] {:name (:common/name cost-item-data)})]
       [form/footer2]]]

     (when (not new?)
       (let [allowed-components (asset-type-library/allowed-component-types atl ctype)]
         (when (seq allowed-components)
           [add-component-menu allowed-components
            #(e! (common-controller/->SetQueryParam
                  :component (str component-id "-" (name %))))])))

     ;; FIXME: remove when finalizing form
     [df/DataFriskView @form-data]]))

(defn- cost-item-hierarchy
  "Show hierarchy of existing cost items, grouped by fgroup and fclass."
  [{:keys [e! cost-items add?]}]
  (r/with-let [open (r/atom #{})
               toggle-open! #(swap! open cu/toggle %)]
    [:div
     [buttons/button-secondary {:element "a"
                                :href (url/set-query-param :id "new")
                                :disabled? add?
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
                (for [{id :db/id :common/keys [name]} cost-items]
                  ^{:key (str id)}
                  [:div
                   [Link {:href (url/set-query-param :id (str id)
                                                     :component nil)} name]])]]))]]))]]))

(defn cost-items-page [e!
                       {query :query :as app}
                       {:keys [cost-items project
                               asset-type-library]}]
  (let [{:keys [id component]} query
        add? (= id "new")]
    [context/provide :rotl (asset-type-library/rotl-map asset-type-library)
     [project-view/project-full-page-structure
      {:e! e!
       :app app
       :project project
       :left-panel [cost-item-hierarchy {:e! e!
                                         :add? add?
                                         :cost-items cost-items}]
       :main [:div
              [typography/Heading2 (tr [:project :tabs :cost-items])]

              (cond
                component
                ^{:key component}
                [with-cost-item e! id
                 [component-form e! asset-type-library id component]]

                add?
                [cost-item-form e! asset-type-library nil]

                id
                ^{:key id}
                [with-cost-item e! id
                 [edit-cost-item-form e! asset-type-library]]

                :else
                [:div "TODO: default view"])]}]]))
