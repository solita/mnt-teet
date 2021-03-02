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
            [teet.util.string :as string]))

(defn- label [m]
  (let [l (tr* m)]
    (if (str/blank? l)
      (str (:db/ident m))
      l)))

(defn- attributes [attrs ]
  (when (seq attrs)
    [Grid {:container true}
     (doall
      (for [{:db/keys [ident valueType] :as attr} attrs
            :let [type (:db/ident valueType)]]
        [form/field {:attribute ident }
         (case type
           :db.type/string [text-field/TextField {:label (label attr)}]

           ;; fallback
           [text-field/TextField {:label (label attr )}]
           )]))]))

(defn- format-fg-and-fc [[fg fc]]
  (if (and (nil? fg)
           (nil? fc))
    ""
    (str (label fg) " / " (label fc))))

(defn- add-cost-item-form [e! asset-type-library]
  (r/with-let [form-data (r/atom {})
               on-change-event (form/update-atom-event
                                form-data
                                (partial merge-with merge))]
    [:<>

     [form/form2
      {:e! e!
       :on-change-event on-change-event
       :value @form-data}

      [Grid {:container true}

       [Grid {:item true :xs 12}
        [form/field {:attribute :feature-group-and-class}
         [select/select-search
          {:e! e!
           :format-result format-fg-and-fc
           :show-empty-selection? true
           :query (fn [text]
                    #(vec
                      (for [fg asset-type-library
                            fc (:fclass/_fgroup fg)
                            :let [result [fg fc]]
                            :when (string/contains-words? (format-fg-and-fc result) text)]
                        result)))}]]]
       ;; allow changing fgroup/fclass only if no other information
       ;; has been changed
       #_[Grid {:item true :xs 6}
        [form/field {:attribute :fgroup}
         [select/select-with-action
          {:show-empty-selection? true
           :items asset-type-library
           :format-item tr*}]]]

       #_[Grid {:item true :xs 6}
        (when-let [{fclasses :fclass/_fgroup} (:fgroup @form-data)]
          [form/field {:attribute :asset/fclass}
           [select/select-with-action
            {:show-empty-selection? true
             :items fclasses
             :format-item tr*}]])]]

      ;; Show attributes for
      [attributes (some-> @form-data :feature-group-and-class second :attribute/_parent)]

      ]

     [df/DataFriskView @form-data]
     ]))

(defn cost-items-page [e! app {:keys [fgroups project
                                      asset-type-library]}]
  (r/with-let [add? (r/atom false)
               add-cost-item! #(reset! add? true)]
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
               [add-cost-item-form e! asset-type-library])]}]))
