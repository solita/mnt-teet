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
            [teet.ui.material-ui :refer [Grid Paper]]
            [teet.ui.text-field :as text-field]
            [clojure.string :as str]
            [teet.util.string :as string]
            [teet.util.collection :as cu]
            [teet.ui.context :as context]
            [teet.ui.common :as common]
            [teet.ui.icons :as icons]
            [teet.common.responsivity-styles :as responsivity-styles]
            [herb.core :refer [<class]]))

(defn- label [m]
  (let [l (tr* m)]
    (if (str/blank? l)
      (str (:db/ident m))
      l)))

(defn- attributes
  "Render grid of attributes."
  [e! attrs]
  (when (seq attrs)
    [Grid {:container true
           :justify :space-evenly
           :alignItems :flex-end}
     (doall
      (for [{:db/keys [ident valueType]
             :asset-schema/keys [unit] :as attr} attrs
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


(defn component-attributes [component rotl]
  (let [ctype (cu/find-> rotl)])
  )

(defn- component
  "Render one component."
  [{:keys [e! component on-change]}]
  ;; form uses the on-change-event constructor given at the 1st render
  ;; so we need to keep track of the on-change here and use it in the
  ;; change event
  (r/with-let [on-change-atom (atom on-change)]
    (reset! on-change-atom on-change)
    [Paper {:style {:padding "0.5rem" :margin-bottom "1rem"}}
     [typography/Heading3 (str (:component/ctype component))]
     [form/form2 {:e! e!
                  :on-change-event (form/callback-change-event #(@on-change-atom %))
                  :value component}
      [attributes e! (get-in component [:ctype :attribute/_parent])]]]))

(defn- components
  "Render field for components, with add component if there are allowed components.

  Renders each component with it's own set
  allowed-components is a collection of component types this entity can have, if empty
  no new components can be added."
  [{:keys [e! value on-change allowed-components]}]
  [:<>
   (doall
    (map-indexed
     (fn [i {id :db/id :as c}]
       ^{:key (str id)}
       [component {:e! e!
                   :component c
                   :on-change #(on-change (update value i merge %))}])
     value))
   [Grid {:container true}
    (doall
     (for [c allowed-components]
       ^{:key (str :db/ident c)}
       [Grid {:item true :xs 3}
        [buttons/button-secondary {:size :small
                                   :on-click #(on-change (conj (or value [])
                                                               {:ctype c
                                                                :component/ctype (:db/ident c)}))
                                   :start-icon (r/as-element [icons/content-add])}
         (label c)]]))]])

(defn- format-fg-and-fc [[fg fc]]
  (if (and (nil? fg)
           (nil? fc))
    ""
    (str (label fg) " / " (label fc))))

(defn group-and-class-selection [{:keys [e! on-change value asset-type-library read-only?]}]
  (let [[fg fc] value]
    (if read-only?
      [typography/Heading3 (format-fg-and-fc value)]
      [Grid {:container true}
       [Grid {:item true :xs 12 :class (<class responsivity-styles/visible-desktop-only)}
        [select/select-search
         {:e! e!
          :on-change on-change
          :value value
          :format-result format-fg-and-fc
          :show-empty-selection? true
          :query (fn [text]
                   #(vec
                     (for [fg asset-type-library
                           fc (:fclass/_fgroup fg)
                           :let [result [fg fc]]
                           :when (string/contains-words? (format-fg-and-fc result) text)]
                       result)))}]]

       [Grid {:item true :xs 6 :class (<class responsivity-styles/visible-mobile-only)}
        [select/select-with-action
         {:show-empty-selection? true
          :items asset-type-library
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
    [:<>

     [form/form2
      {:e! e!
       :on-change-event on-change-event
       :value @form-data}

      [form/field {:attribute :feature-group-and-class}
       [group-and-class-selection {:e! e!
                                   :asset-type-library asset-type-library
                                   :read-only? (seq (dissoc @form-data :feature-group-and-class))}]]


      ;; Show attributes for
      [attributes e! (some-> @form-data :feature-group-and-class second :attribute/_parent)]

      [form/field {:attribute :asset/components}
       [components {:e! e!
                    :allowed-components (get-in @form-data [:feature-group-and-class 1 :ctype/_parent])}]]]

     [df/DataFriskView @form-data]
     ]))


(defn cost-items-page [e! app {:keys [fgroups project
                                      asset-type-library]}]
  (r/with-let [add? (r/atom false)
               add-cost-item! #(reset! add? true)]
    [context/provide :rotl asset-type-library
     [project-view/project-full-page-structure
      {:e! e!
       :app app
       :project project
       :left-panel [:div (tr [:project :tabs :cost-items])]
       :main [:div
              [typography/Heading2 (tr [:project :tabs :cost-items])]
              [buttons/button-primary {:on-click add-cost-item!
                                       :disabled @add?}
               "add cost item"]

              (when @add?
                [add-cost-item-form e! asset-type-library])]}]]))
