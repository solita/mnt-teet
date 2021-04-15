(ns teet.asset.asset-type-library
  "Code for handling asset type library and generated forms data."
  (:require [teet.util.collection :as cu]
            [clojure.walk :as walk]
            [teet.util.datomic :as du]
            [clojure.string :as str]))

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
   (defn ->bigdec [x]
     (if (string? x)
       (when-not (str/blank? x)
         (-> x str/trim (str/replace "," ".") bigdec))
       (bigdec x))))

#?(:clj
   (defn ->long [x]
     (if (string? x)
       (when-not (str/blank? x)
         (-> x str/trim Long/parseLong))
       (long x))))
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
