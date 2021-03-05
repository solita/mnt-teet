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
            [teet.ui.material-ui :refer [Grid Collapse Card CardHeader CardContent CardActions IconButton]]
            [teet.ui.text-field :as text-field]
            [clojure.string :as str]
            [teet.util.string :as string]
            [teet.util.collection :as cu]
            [teet.ui.context :as context]
            [teet.ui.icons :as icons]
            [teet.common.responsivity-styles :as responsivity-styles]
            [herb.core :refer [<class]]
            [teet.ui.common :as common]))

(defn- label [m]
  (let [l (tr* m)]
    (if (str/blank? l)
      (str (:db/ident m))
      l)))

(defn- attributes* [e! attrs rotl]
  (let [common-attrs (:attribute/_parent (:ctype/common rotl))]
    [Grid {:container true
           :justify :space-evenly
           :alignItems :flex-end}
     (doall
      (for [{:db/keys [ident valueType]
             :asset-schema/keys [unit] :as attr} (concat common-attrs attrs)
            :let [type (:db/ident valueType)]]
        [Grid {:item true :xs 4 :style {:padding "0.2rem"}}
         [form/field {:attribute ident}
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
      [Card {:style {:margin-bottom "1rem"}}
       [CardHeader {:title (str (label ctype)
                                (when-let [name (:common/name component)]
                                  (str ": " name)))
                    :action (r/as-element
                             [CardActions
                              [IconButton {:on-click delete!}
                               [icons/action-delete]]
                              [IconButton {:on-click toggle-expand!}
                               (if @expanded?
                                 [icons/navigation-expand-less]
                                 [icons/navigation-expand-more])]])}]

       [Collapse {:in @expanded? :unmountOnExit true}
        [CardContent
         [form/form2 {:e! e!
                      :on-change-event on-change-event
                      :value component}
          [attributes e! (:attribute/_parent ctype)]

          [form/field :component/components
           [components {:e! e! :allowed-components (:ctype/_parent ctype)}]]]]]])))

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
    (doall
     (for [c allowed-components]
       ^{:key (str :db/ident c)}
       [Grid {:item true :xs 3}
        [buttons/button-secondary {:size :small
                                   :on-click #(on-change (conj (or value [])
                                                               {:component/ctype (:db/ident c)}))
                                   :start-icon (r/as-element [icons/content-add])}
         (label c)]]))]])

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

(defn cost-items-page [e! app {:keys [fgroups project
                                      asset-type-library]}]
  (r/with-let [add? (r/atom false)
               add-cost-item! #(reset! add? true)]
    [context/provide :rotl (rotl-map asset-type-library)
     [project-view/project-full-page-structure
      {:e! e!
       :app app
       :project project
       :left-panel [:div (tr [:project :tabs :cost-items])]
       :main [:div
              [typography/Heading2 (tr [:project :tabs :cost-items])]
              [buttons/button-primary {:on-click add-cost-item!
                                       :disabled @add?}
               (tr [:asset :add-cost-item])]

              (when @add?
                [add-cost-item-form e! asset-type-library])]}]]))
