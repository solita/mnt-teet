(ns teet.asset.asset-library-view
  "Asset type library view"
  (:require [reagent.core :as r]
            [teet.ui.typography :as typography]
            [teet.localization :as localization :refer [tr]]
            [teet.ui.table :as table]
            [teet.util.collection :as cu]
            [teet.ui.material-ui :refer [Paper Grid CircularProgress]]
            [teet.common.common-styles :as common-styles]
            [herb.core :refer [<class]]
            [teet.ui.format :as format]
            [teet.ui.container :as container]
            [teet.ui.url :as url]
            [teet.theme.theme-colors :as theme-colors]
            [teet.asset.asset-type-library :as asset-type-library]
            [teet.asset.asset-ui :refer [tr*]]))


(defn- attribute-values [open {values :enum/_attribute id :db/ident :as a}]
  (when (seq values)
    (if-not (@open id)
      [:a {:on-click #(swap! open conj id)} (str (count values) " values")]
      [:ul
       (for [v values]
         ^{:key (str (:db/ident v))}
         [:li
          [tr* v] " (" (str (:db/ident v)) ")"])])))

(defn- attribute-table [open attributes]
  (when (seq attributes)
    [:<>
     [typography/BoldGrayText (tr [:asset :type-library :attributes])]
     [table/simple-table
      [[(tr [:asset :type-library :name])]
       [(tr [:asset :type-library :datatype])]
       [(tr [:asset :type-library :label])]
       [(str (tr [:asset :type-library :unit]) " / "
             (tr [:asset :type-library :values]))]]
      (for [a attributes]
        [[(str (:db/ident a))]
         [(tr [:asset :type-library (-> a :db/valueType :db/ident)])]
         [(tr* a)]
         [[:<>
           (:asset-schema/unit a)
           [attribute-values open a]]]])]]))

(defn- child-links [children]
  (when (seq children)
    [:ul
     (doall
      (for [child children]
        ^{:key (str (:db/id child))}
        [:li {:data-cy (str "link-" (str (:db/ident child)))}
         [url/Link {:page :asset-type-library
                    :query {:item (str (:db/ident child))}}
          (tr* child)]]))]))

(defn- ctype [open {label ::label
                    attributes :attribute/_parent
                    child-ctypes :ctype/_parent
                    :component/keys [inherits-location? quantity-unit]
                    :as ct}]
  [:<>
   [typography/Heading3
    (or label (str (tr [:asset :type-library :ctype]) " " (tr* ct)))]
   [:div
    (tr* ct :asset-schema/description)
    (when inherits-location?
      [typography/SmallGrayText
       (tr [:asset :type-library :component-inherits-location])])
    (when quantity-unit
      [typography/SmallGrayText
       (tr [:asset :type-library :component-quantity-unit] {:unit quantity-unit})])
    [attribute-table open attributes]
    [child-links child-ctypes]]])

(defn- fclass [open {attributes :attribute/_parent :as fclass}]
  [:<>
   [typography/Heading3
    (str (tr [:asset :type-library :fclass]) " " (tr* fclass)
         (when-let [op (:fclass/oid-prefix fclass)]
           (str " (" op ")")))]
   [:div
    (tr* fclass :asset-schema/description)
    [attribute-table open attributes]
    [child-links (:ctype/_parent fclass)]]])

(defn- fgroup [_open fgroup]
  [:<>
   [typography/Heading3
    (str (tr [:asset :type-library :fgroup]) " " (tr* fgroup))]
   [:div
    (tr* fgroup :asset-schema/description)
    [child-links (:fclass/_fgroup fgroup)]]])

(defn- material [_open material]
  [:<>
   [typography/Heading3
    (str (tr [:asset :type-library :material]) " " (tr* material))]
   [:div
    (tr* material :asset-schema/description)
    [child-links (:material/fgroups material)]]])


(defn- rotl-type-badge-style []
  {:font-size "70%"
   :font-variant :all-small-caps
   :margin-left "1rem"
   :padding "0px 5px 0px 5px"
   :border-radius "10px"
   :display :inline-block
   :background theme-colors/gray-lightest})

(defn- rotl-tree [{:keys [open toggle! focus] :as opts} {ident :db/ident :as item}]
  (let [children (or (:children item)
                     (concat (:fclass/_fgroup item)
                             (:ctype/_parent item)))
        label (or (:label opts) (tr* item))]
    [container/collapsible-container
     {:container-attrs {:data-ident (str ident)}
      :open? (open ident)
      :disabled? (empty? children)
      :on-toggle (r/partial toggle! ident)}
     [url/Link {:page :asset-type-library
                :query {:item (str ident)}}
      [:<>
       (if (= focus ident)
         [:b label]
         label)
       [:div {:class (<class rotl-type-badge-style)}
        (tr [:asset :type-library (-> ident namespace keyword)])]]]
     [:div {:class (<class common-styles/indent-rem 1)}
      (doall
       (for [child children]
         ^{:key (str (:db/ident child))}
         [rotl-tree
          ;; Children do not inherit the label
          (dissoc opts :label)
          child]))]]))

(defn- rotl-item [{type :asset-schema/type :as item}]
  (r/with-let [open (r/atom #{})]
    (case (:db/ident type)
      :asset-schema.type/fgroup
      [fgroup open item]

      :asset-schema.type/fclass
      [fclass open item]

      :asset-schema.type/ctype
      [ctype open item]

      :asset-schema.type/material
      [material open item]

      ;; Empty span, shouldn't get here
      [:span])))


(defn- scrollable-grid [xs content]
  [Grid {:item true :xs xs
         :classes #js {:item (<class common-styles/content-scroll-max-height "60px")}}
   content])

(defn- with-top-level [item th]
  (cond (or (asset-type-library/fclass? item)
            (asset-type-library/ctype? item)
            (asset-type-library/fgroup? item))
        (conj th :fgroup/fgroups)

        ;; This does not actually work yet since `type-hierarchy` assumes fgroups at top level
        (asset-type-library/material? item)
        (conj th :material/materials)

        :else
        th))

(defn- ensure-tree-open [atl open item-kw]
  (let [current-open @open
        hierarchy (asset-type-library/type-hierarchy atl item-kw)
        item (last hierarchy)
        th (with-top-level item (mapv :db/ident hierarchy))]
    (when-not (every? current-open th)
      (swap! open into th))))

(defn- focus-on-ident [app]
  (some->> app :query :item cljs.reader/read-string))

(defn asset-library-page [_e! app]
  (let [open (r/atom #{})
        toggle! #(swap! open cu/toggle %)
        focus (atom (focus-on-ident app))]
    (r/create-class
     {:component-will-receive-props
      (fn [_this [_ _ {atl :asset-type-library :as app} _]]
        (let [new-focus (focus-on-ident app)]
          (when (not= new-focus @focus)
            (ensure-tree-open atl
                              open
                              new-focus)
            (reset! focus new-focus))))
      :reagent-render
      (fn [_e! {atl :asset-type-library :as _app}]
        (if-not atl
          [CircularProgress {}]
          (let [{:keys [fgroups materials] common :ctype/common
                 modified :tx/schema-imported-at} atl]
            [:<>
             [:div {:class (<class common-styles/flex-row-space-between)
                    :style {:align-items :center}}

              [typography/Heading1 (tr [:asset :type-library :header])]
              (when modified
                [:div {:style {:margin "1rem"}}
                 (tr [:common :last-modified]) ": "
                 (format/date-time modified)])]
             [Paper {}
              [Grid {:container true :spacing 0 :wrap :wrap}
               [scrollable-grid 4
                [:<>
                 ^{:key "common"}
                 [rotl-tree {:open @open :toggle! toggle!
                             :focus @focus
                             :label (tr [:asset :type-library :common-ctype])}
                  common]
                 ^{:key "fgroups"}
                 [rotl-tree {:open @open :toggle! toggle!
                             :focus @focus
                             :label (tr [:asset :type-library :fgroup])}
                  {:db/ident :fgroup/fgroups
                   :children fgroups}]
                 ^{:key "materials"}
                 [rotl-tree {:open @open :toggle! toggle!
                             :focus @focus
                             :label (tr [:asset :type-library :material])}
                  {:db/ident :material/materials
                   :children materials}]]]
               [scrollable-grid 8
                [:div {:style {:padding "1rem"}}
                 (when-let [item-kw @focus]
                   (let [item (if (= item-kw :ctype/common)
                                common
                                (asset-type-library/item-by-ident atl item-kw))]
                     [rotl-item item]))]]]]])))})))
