(ns teet.asset.asset-library-view
  "Asset type library view"
  (:require [reagent.core :as r]
            [teet.ui.typography :as typography]
            [teet.localization :as localization :refer [tr]]
            [teet.ui.table :as table]
            [teet.util.collection :as cu]
            [teet.ui.material-ui :refer [Card CardHeader CardContent IconButton
                                         Paper Grid]]
            [teet.ui.icons :as icons]
            [teet.util.datomic :as du]
            [teet.common.common-styles :as common-styles]
            [herb.core :refer [<class]]
            [teet.ui.format :as format]
            [teet.ui.container :as container]
            [teet.ui.url :as url]
            [teet.theme.theme-colors :as theme-colors]
            [teet.asset.asset-type-library :as asset-type-library]
            [teet.theme.theme-spacing :as theme-spacing]))

(defn tr*
  ([m]
   (tr* m :asset-schema/label))
  ([m key]
   (get-in m [key (case @localization/selected-language
                    :et 0
                    :en 1)])))

(defn- collapsible [open item header content]
  (let [open? (@open (:db/ident item))]
    [Card {:variant :outlined
           :data-ident (str (:db/ident item))}
     [CardHeader {:disableTypography true
                  :title (r/as-element
                           (condp du/enum= (:asset-schema/type item)
                             :asset-schema.type/fgroup [typography/Heading2 header]
                             :asset-schema.type/fclass [typography/Heading4 header]
                             :asset-schema.type/ctype [typography/Heading5 header]
                             [typography/BoldGrayText header]))
                  :action (r/as-element
                           [IconButton {:on-click #(swap! open cu/toggle (:db/ident item))}
                            (if open?
                              [icons/navigation-expand-less]
                              [icons/navigation-expand-more])])}]
     (when (@open (:db/ident item))
       [CardContent {:class [(<class common-styles/margin-left 1)
                             (<class common-styles/margin-bottom 0.5)]}
        content])]))

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
        [:li
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
    (str (tr [:asset :type-library :fclass]) " " (tr* fclass))]
   [:div
    (tr* fclass :asset-schema/description)
    [attribute-table open attributes]
    [child-links (:ctype/_parent fclass)]]])

(defn- fgroup [open fgroup]
  [:<>
   [typography/Heading3
    (str (tr [:asset :type-library :fgroup]) " " (tr* fgroup))]
   [:div
    (tr* fgroup :asset-schema/description)
    [child-links (:fclass/_fgroup fgroup)]]])

(defn- rotl-type-badge-style []
  {:font-size "70%"
   :font-variant :all-small-caps
   :margin-left "1rem"
   :padding "0px 5px 0px 5px"
   :border-radius "10px"
   :display :inline-block
   :background theme-colors/gray-lightest})

(defn- rotl-tree [{:keys [open toggle!] :as opts} {ident :db/ident :as item}]
  (let [children (concat (:fclass/_fgroup item)
                         (:ctype/_parent item))]
    [container/collapsible-container
     {:container-attrs {:data-ident (str ident)}
      :open? (open ident)
      :disabled? (empty? children)
      :on-toggle (r/partial toggle! ident)}
     [url/Link {:page :asset-type-library
                :query {:item (str ident)}}
      [:<>
       (tr* item)
       [:div {:class (<class rotl-type-badge-style)}
        (tr [:asset :type-library (-> ident namespace keyword)])]]]
     [:div {:class (<class common-styles/indent-rem 1)}
      (if (empty? children)
        (tr [:asset :type-library :no-child-items])
        (doall
         (for [child children]
           ^{:key (str (:db/ident child))}
           [rotl-tree opts child])))]]))

(defn rotl-item [atl {type :asset-schema/type :as item}]
  (def *item item)
  (r/with-let [open (r/atom #{})]
    (case (:db/ident type)
      :asset-schema.type/fgroup
      [fgroup open item]

      :asset-schema.type/fclass
      [fclass open item]

      :asset-schema.type/ctype
      [ctype open item]

      [:div "no such thing"])))


(defn- scrollable-grid [xs content]
  [Grid {:item true :xs xs
         :classes #js {:item (<class common-styles/content-scroll-max-height "60px")}}
   content])

(defn asset-library-page [_e! app {:keys [fgroups] common :ctype/common
                                    modified :tx/schema-imported-at
                                    :as atl}]
  (r/with-let [open (r/atom #{})
               toggle! #(swap! open cu/toggle %)]
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
        (doall
         (for [fg fgroups]
           ^{:key (str (:db/ident fg))}
           [rotl-tree {:open @open :toggle! toggle!} fg]))]
       [scrollable-grid 8
        [:div {:style {:padding "1rem"}}
         (when-let [item (->> app :query :item cljs.reader/read-string
                              (asset-type-library/item-by-ident atl))]
           [rotl-item atl item])]]]]]))
