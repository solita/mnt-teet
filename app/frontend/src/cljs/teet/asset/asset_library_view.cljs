(ns teet.asset.asset-library-view
  "Asset type library view"
  (:require [reagent.core :as r]
            [teet.ui.typography :as typography]
            [teet.localization :as localization :refer [tr]]
            [teet.ui.table :as table]
            [teet.util.collection :as cu]
            [teet.ui.material-ui :refer [Card CardHeader CardContent IconButton]]
            [teet.ui.icons :as icons]
            [teet.util.datomic :as du]
            [teet.common.common-styles :as common-styles]
            [herb.core :refer [<class]]))

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

(defn- ctype [open {label ::label
                    attributes :attribute/_parent
                    child-ctypes :ctype/_parent
                    inherits-location? :component/inherits-location?
                    :as ct}]
  [collapsible open ct
   (or label (str (tr [:asset :type-library :ctype]) " " (tr* ct)))
   [:div
    (tr* ct :asset-schema/description)
    (when inherits-location?
      [typography/SmallGrayText
       (tr [:asset :type-library :component-inherits-location])])
    [attribute-table open attributes]
    (when (seq child-ctypes)
      [:div.child-ctypes
       (doall
        (for [ct child-ctypes]
          ^{:key (str (:db/id ct))}
          [ctype open ct]))])]])

(defn- fclass [open {attributes :attribute/_parent :as fclass}]
  [collapsible open fclass
   (str (tr [:asset :type-library :fclass]) " " (tr* fclass))
   [:div
    (tr* fclass :asset-schema/description)
    [attribute-table open attributes]
    (doall
     (for [ct (:ctype/_parent fclass)]
       ^{:key (str (:db/id ct))}
       [ctype open ct]))]])

(defn- fgroup [open fgroup]
  [collapsible open fgroup
   (str (tr [:asset :type-library :fgroup]) " " (tr* fgroup))
   [:div
    (tr* fgroup :asset-schema/description)
    (doall
     (for [fc (:fclass/_fgroup fgroup)]
       ^{:key (str (:db/id fc))}
       [fclass open fc]))]])

(defn asset-library-page [_e! _app {:keys [fgroups] common :ctype/common}]
  (r/with-let [open (r/atom #{})]
    [:<>
     [typography/Heading1 (tr [:asset :type-library :header])]
     [ctype open (assoc common ::label (tr [:asset :type-library :common-ctype]))]
     (doall
      (for [fg fgroups]
        ^{:key (str (:db/id fg))}
        [fgroup open fg]))]))
