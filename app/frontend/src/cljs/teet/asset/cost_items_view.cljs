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
            [teet.ui.material-ui :refer [Grid]]
            [teet.ui.text-field :as text-field]
            [clojure.string :as str]
            [teet.util.string :as string]
            [teet.util.collection :as cu]
            [teet.ui.context :as context]
            [teet.ui.icons :as icons]
            [teet.common.responsivity-styles :as responsivity-styles]
            [herb.core :refer [<class]]
            [teet.ui.common :as common]
            [teet.ui.container :as container]))

(defn- label [m]
  (let [l (tr* m)]
    (if (str/blank? l)
      (str (:db/ident m))
      l)))

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

(defn- attributes* [e! attrs rotl]
  (let [common-attrs (:attribute/_parent (:ctype/common rotl))]
    [Grid {:container true
           :justify :space-evenly
           :alignItems :flex-end}
     (doall
      (for [{:db/keys [ident valueType]
             :attribute/keys [mandatory? min-value max-value]
             :asset-schema/keys [unit] :as attr} (concat common-attrs attrs)
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
            [select/select-enum {:e! e!
                                 :id ident
                                 :attribute ident
                                 :database :asset
                                 :format-enum-fn (fn [enum-values]
                                                   (let [by-value (into {}
                                                                        (map (juxt :db/ident identity))
                                                                        enum-values)]
                                                     #(-> % by-value label)))}]

            ;; Text field
            [text-field/TextField
             ;; parse based on type
             (merge
              {:label (label attr)
               :end-icon (when unit
                           (text-field/unit-end-icon unit))}
              (case type
                (:db.type/long :db.type/bigdec) {:type :number}
                nil))])]]))]))

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
               delete! #(@on-change-atom {::deleted? true})]
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

(defn- components
  "Render field for components, with add component if there are allowed components.

  Renders each component with it's own set
  allowed-components is a collection of component types this entity can have, if empty
  no new components can be added."
  [{:keys [e! value on-change allowed-components]}]
  [:<>
   (doall
    (keep-indexed
     (fn [i {id :db/id deleted? ::deleted? :as c}]
       (when (not deleted?)
         ^{:key (str id)}
         [context/consume :rotl
          [component {:e! e!
                      :component c
                      :on-change #(on-change (update value i merge %))}]]))
     value))
   [Grid {:container true}
    (if (> (count allowed-components) 3)
      [common/context-menu
       {:label "add component"
        :icon [icons/content-add-circle-outline]
        :items (for [c allowed-components]
                 {:label (label c)
                  :icon [icons/content-add]
                  :on-click (r/partial on-change (conj (or value [])
                                                       {:ctype c
                                                        :component/ctype (:db/ident c)}))})}]
      (doall
       (for [c allowed-components]
         ^{:key (str :db/ident c)}
         [Grid {:item true :xs 12 :md 4}
          [buttons/button-secondary {:size :small
                                     :on-click (r/partial on-change (conj (or value [])
                                                                          {:component/ctype (:db/ident c)}))
                                     :start-icon (r/as-element [icons/content-add])}
           (label c)]])))]])

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

(defn- add-cost-item-form [e! asset-type-library]
  (r/with-let [form-data (r/atom {})
               on-change-event (form/update-atom-event
                                form-data
                                cu/deep-merge)]
    (let [[_feature-group feature-class] (some-> @form-data :feature-group-and-class)]
      [:<>

       [form/form2
        {:e! e!
         :on-change-event on-change-event
         :value @form-data}

        [form/field {:attribute :feature-group-and-class}
         [group-and-class-selection {:e! e!
                                     :fgroups (:fgroups asset-type-library)
                                     :read-only? (seq (dissoc @form-data :feature-group-and-class))
                                     :name (:common/name @form-data)}]]

        (when feature-class
          [:<>
           ;; Attributes for asset
           [attributes e! (some-> feature-class :attribute/_parent)]

           ;; Components
           [form/field {:attribute :asset/components}
            [components {:e! e!
                         :allowed-components (:ctype/_parent feature-class)}]]])]

       ;; FIXME: remove when finalizing form
       [df/DataFriskView @form-data]])))

(defn- rotl-map
  "Return a flat mapping of all ROTL items, by :db/ident."
  [rotl]
  (into {}
        (map (juxt :db/ident identity))

        ;; collect maps that have :db/ident and more fields apart from identity
        (cu/collect #(and (map? %)
                          (contains? % :db/ident)
                          (seq (dissoc % :db/id :db/ident)))
                    rotl)))

(defn- cost-item-hierarchy
  "Show hierarchy of existing cost items, grouped by fgroup and fclass."
  [{:keys [e! fgroups add?]}]
  (r/with-let [add-cost-item! #(reset! add? true)]
    [:div
     [buttons/button-secondary {:on-click add-cost-item!
                                :disabled? @add?
                                :start-icon (r/as-element
                                             [icons/content-add])}
      (tr [:asset :add-cost-item])]]))

(defn cost-items-page [e! app {:keys [fgroups project
                                      asset-type-library]}]
  (r/with-let [add? (r/atom false)]
    [context/provide :rotl (rotl-map asset-type-library)
     [project-view/project-full-page-structure
      {:e! e!
       :app app
       :project project
       :left-panel [cost-item-hierarchy {:e! e!
                                         :add? add?
                                         :fgroups fgroups}]
       :main [:div
              [typography/Heading2 (tr [:project :tabs :cost-items])]

              (when @add?
                [add-cost-item-form e! asset-type-library])]}]]))
