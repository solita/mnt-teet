(ns teet.asset.asset-model
  "Asset model stuff.

  **Asset**

  Asset is a physical feature that is on the road network, like
  road pavement, lighting or bridge. The feature groups, classes
  and properties are all defined in the ROTL (Road Object Type Library).
  See asset-schema.edn.

  Assets are just regular Datomic entities with attributes defined in
  the ROTL plus an asset object identification code (OID) and some other
  metadata fields defined in the base schema.

  Assets may have components that are similar entities and components
  may have subcomponents.

  ***Asset/component OID***

  The OID for an asset or component is automatically generated by the
  system using an incrementing counter per feature class.

  The OID format is:
  N40-NNN-FFFFFF(-CCCCC)
  where
  N40 is owner code, default is N40 = Transport authority
  NNN is a three-letter Feature class code, shortened from the Estonian name of the feature class.
  FFFFFF is a 6 digit zero padded numeric feature counter within a Feature class.

  For components:
  CCCCC is a 5 digit zero padded numeric component counter within a particular Feature.


  **Cost groups**

  The ROTL defines some properties with `:attribute/cost-grouping?` true.
  Components in the project with the same type, status and set of cost grouping
  attribute values constitute a \"cost group\". The cost groups are
  automatically determined from the attribute values when summarizing costs.

  ***Cost group price***

  Cost group prices are calculated by summing up the quantity of the
  components in the cost group and multiplied by the price.

  Cost group price is an entity that has the same attribute values
  as the components in the cost group plus a `:cost-group/project` and
  `:cost-group/price`. Prices are set per project.

  "
  (:require #?(:cljs [goog.string :as gstr])
            [clojure.string :as str]
            [teet.util.collection :as cu]))

#?(:cljs (def ^:private format gstr/format))


(def ^:private asset-pattern #"^...-\w{3}-\d{6}$")
(def ^:private component-pattern #"^...-\w{3}-\d{6}-\d{5}$")

(defn asset-oid?
  "Check if given OID refers to asset."
  [oid]
  (and (string? oid)
       (boolean (re-matches asset-pattern oid))))

(defn component-oid?
  "Check if given OID refers to a component."
  [oid]
  (and (string? oid)
       (boolean (re-matches component-pattern oid))))

(defn asset-oid->fclass-oid-prefix
  "Extract the asset fclass OID prefix from an asset OID instance."
  [oid]
  {:pre [(asset-oid? oid)]}
  (subs oid 4 7))

(defn asset-oid
  "Format asset OID for feature class prefix and seq number."
  [owner-code fclass-oid-prefix sequence-number]
  {:post [(asset-oid? %)]}
  (format "%s-%s-%06d" owner-code fclass-oid-prefix sequence-number))

(defn component-oid
  "Format component OID for asset OID and seq number."
  [asset-oid sequence-number]
  {:post [(component-oid? %)]}
  (format "%s-%05d" asset-oid sequence-number))

(defn component-asset-oid
  "Given component OID, return the OID of the parent asset."
  [component-oid]
  {:pre [(component-oid? component-oid)]}
  (-> component-oid
      (str/split #"-")
      butlast
      (->> (str/join "-"))))

(defn find-component-path
  "Return vector containing all parents of component from asset to the component.
  For example:
  [a c1 c2 c3]
  where a is the asset, that has component c1
  c1 has child component c2
  and c2 has child component c3 (the component we want)"
  [asset component-oid]
  (cu/find-path #(concat (:asset/components %)
                         (:component/components %))
                #(= component-oid (:asset/oid %))
                asset))

(def cost-totals-table-columns
  [:type :properties :common/status :quantity :cost-per-quantity-unit :total-cost])

(def cost-totals-table-align
  {:quantity "right"
   :cost-per-quantity-unit "right"
   :total-cost "right"})


(def locked? "Key to check if version is locked"
  :boq-version/locked?)


(def assets-listing-columns
  "Columns to show in asset manager search results listing"
  [:asset/oid :location/road-nr])
