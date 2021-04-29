(ns teet.asset.asset-type-library
  "Code for handling asset type library and generated forms data."
  (:require [teet.util.collection :as cu]
            [clojure.walk :as walk]
            [teet.util.datomic :as du]
            [clojure.string :as str]
            #?(:clj
               [teet.util.coerce :refer [->long ->bigdec]])))

(defn rotl-map
  "Return a flat mapping of all ROTL items, by :db/ident."
  [rotl]
  (into {}
        (map (juxt :db/ident identity))

        ;; collect maps that have :db/ident and more fields apart from identity
        (cu/collect #(and (map? %)
                          (contains? % :db/ident)
                          (seq (dissoc % :db/id :db/ident)))
                    rotl)))

(defn fgroup-for-fclass
  "Find fgroup which fclass belongs to."
  [atl fclass]
  (some (fn [fg]
          (when (some #(du/enum= fclass %)
                      (:fclass/_fgroup fg))
            fg))
        (:fgroups atl)))

(defn fclass-for-ctype
  "Find fclass which ctype belongs to."
  [atl ctype]
  (some (fn [fg]
          (some (fn [fc]
                  (when (some #(du/enum= ctype %)
                              (:ctype/_parent fc))
                    fc))
                (:fclass/_fgroup fg)))
        (:fgroups atl)))

(defn type-hierarchy
  "Find each hierarchy parent of given fclass or ctype."
  [atl node]
  (vec
   (drop 1                              ; drop 1st :fgroups level
         (cu/find-path #(concat (:fgroups %)
                                (:fclass/_fgroup %)
                                (:ctype/_parent %))
                       #(du/enum= node %)
                       atl))))

(defn- has-type? [type x]
  (and (map? x)
       (= type (get-in x [:asset-schema/type :db/ident]))))

(def fclass? (partial has-type? :asset-schema.type/fclass))
(def ctype? (partial has-type? :asset-schema.type/ctype))

(defn allowed-component-types
  "Return all ctypes that are allowed for the given fclass or ctype."
  [atl fclass-or-ctype]
  (let [ident (if (keyword? fclass-or-ctype)
                fclass-or-ctype
                (:db/ident fclass-or-ctype))]
    (:ctype/_parent (cu/find-matching #(and (or (fclass? %) (ctype? %))
                                            (= (:db/ident %) ident))
                                      atl))))

(defn item-by-ident
  "Find any type of ATL item based on ident."
  [atl ident]
  (cu/find-matching #(and (map? %)
                          (contains? % :asset-schema/type)
                          (= ident (:db/ident %)))
                    atl))

#?(:clj
   (defn coerce-fn [value-type]
     (case value-type
       :db.type/bigdec ->bigdec
       :db.type/long ->long

       ;; Remove blank values (will be retracted)
       :db.type/string #(when-not (str/blank? %) %)

       ;; No parsing
       identity)))
#?(:clj
   (defn coerce-tuple [tuple-type value]
     (let [v (cond
               (vector? value) value
               (string? value) (str/split value #"\s*,\s*")
               :else (throw (ex-info "Unsupported tuple value"
                                     {:tuple-type tuple-type
                                      :value value})))]
       (mapv (coerce-fn tuple-type) v))))
#?(:clj
   (defn form->db
     "Prepare data from asset form to be saved in the database"
     [rotl form-data]
     (walk/prewalk
      (fn [x]
        (if-let [attr (and (map-entry? x)
                           (get rotl (first x)))]
          (update
           x 1
           (if-let [tuple-type (:db/tupleType attr)]
             (partial coerce-tuple tuple-type)
             (coerce-fn (get-in attr [:db/valueType :db/ident]))))
          x))
      form-data)))

#?(:clj
   (defn db->form
     "Prepare asset data fetched from db for frontend form"
     [rotl asset]
     (walk/prewalk
      (fn [x]
        (let [attr (and (map-entry? x)
                        (get rotl (first x)))]
          (cond
            attr
            (update
             x 1
             (if (:db/tupleType attr)
               #(str/join "," %)
               (case (get-in attr [:db/valueType :db/ident])
                 (:db.type/bigdec :db.type/long) str
                 identity)))

            (and (map? x)
                 (= #{:db/id :db/ident} (set (keys x))))
            (:db/ident x)

            :else x)))
      asset)))

(defn label [language schema-item]
  (get-in schema-item [:asset-schema/label (case language
                                             :et 0
                                             :en 1)]))

(defn format-properties
  "Format properties map for summary view.
  Returns collection of property values with name, value and
  optional unit."
  [language atl properties]
  (let [;; status is part of cost grouping, but shown in a separate column
        properties (dissoc properties :common/status)
        id->def (partial item-by-ident atl)
        attr->val (dissoc (cu/map-keys id->def properties) nil)
        label (partial label language)]
    (map (fn [[k v]]
           (let [u (:asset-schema/unit k)]
             [(label k)
              (case (get-in k [:db/valueType :db/ident])
                :db.type/ref (some-> v :db/ident id->def label)
                (str v))
              u]))
         (sort-by (comp label key) attr->val))))
