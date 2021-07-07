(ns teet.asset.asset-type-library
  "Code for handling asset type library and generated forms data."
  (:require [teet.util.collection :as cu]
            [teet.util.datomic :as du]
            [teet.util.string :as string]
            #?@(:clj [[clojure.string :as str]
                      [clojure.walk :as walk]
                      [teet.util.coerce :refer [->long ->bigdec]]])))

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

(defn- item-by-ident*
  [atl ident]
  (cu/find-matching #(and (map? %)
                          (contains? % :asset-schema/type)
                          (= ident (:db/ident %)))
                    atl))

(def item-by-ident
  "Find any type of ATL item based on ident."
  ;; NOTE: we can memoize this without fear of unbounded growth
  ;; as asset type library will never change during the lifetime
  ;; of the process (either page in browser or deployment in ions)
  (memoize item-by-ident*))

(defn- has-type? [type x]
  (and (map? x)
       (= type (get-in x [:asset-schema/type :db/ident]))))

(def fgroup? (partial has-type? :asset-schema.type/fgroup))
(def fclass? (partial has-type? :asset-schema.type/fclass))
(def ctype? (partial has-type? :asset-schema.type/ctype))
(def material? (partial has-type? :asset-schema.type/material))

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
  (if (:ctype/parent ctype)
    (loop [parent (item-by-ident atl (-> ctype :ctype/parent :db/ident))]
      (if (or (nil? parent)
              (fclass? parent))
        parent
        (recur (item-by-ident atl (-> parent :ctype/parent :db/ident)))))
    ;; TODO the old implementation doesn't work for ctypes that are
    ;; children of other ctypes
    (some (fn [fg]
            (some (fn [fc]
                    (when (some #(du/enum= ctype %)
                                (:ctype/_parent fc))
                      fc))
                  (:fclass/_fgroup fg)))
          (:fgroups atl))))

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

(defn leaf-ctype?
  "Is `ctype`
   - a component type
   - that has no child component types"
  [ctype]
  (and (ctype? ctype)
       (empty? (:ctype/_parent ctype))))

(defn allowed-component-types
  "Return all ctypes that are allowed for the given fclass or ctype."
  [atl fclass-or-ctype]
  (let [ident (if (keyword? fclass-or-ctype)
                fclass-or-ctype
                (:db/ident fclass-or-ctype))]
    (:ctype/_parent (cu/find-matching #(and (or (fclass? %) (ctype? %))
                                            (= (:db/ident %) ident))
                                      atl))))

(defn allowed-material-types
  "Return all materials and products that are allowed for the given fclass or ctype"
  [atl fclass-or-ctype]
  (let [hierarchy (type-hierarchy atl fclass-or-ctype)
        node (last hierarchy)
        fgroup (-> hierarchy first :db/ident)]
    (if (leaf-ctype? node)
      (->> atl
           :materials
           ;; Is the fgroup in material's :material/fgroups?
           (filter (fn [material]
                     (->> material
                          :material/fgroups
                          (map :db/ident)
                          set
                          fgroup))))
      [])))

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
     (when (some? value)
       (let [v (cond
                 (vector? value) value
                 (string? value) (str/split value #"\s*,\s*")
                 :else (throw (ex-info "Unsupported tuple value"
                                       {:tuple-type tuple-type
                                        :value value})))]
         (mapv (coerce-fn tuple-type) v)))))
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
               #(str/join ", " %)
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
                :db.type/ref (if (keyword? v)
                               (some-> v id->def label)
                               (some-> v :db/ident id->def label))
                (str v))
              u]))
         (sort-by (comp label key) attr->val))))

(defn- matching-component-labels [language text acc {cs :ctype/_parent :as fclass}]
  (let [l (label language fclass)
        pos (string/match-pos l text)
        acc (if pos
              (conj acc {:pos pos :component l})
              acc)]
    (reduce (partial matching-component-labels  language text)
            acc cs)))

(defn search-fclass
  "Search for feature classes by search term.
  Returns sequence of [fgroup fclass] vectors of matching items
  sorted by relevance.

  Includes any fclass where the group name, class name or a component
  name contains the search term."
  [{fgroups :fgroups :as _atl} language search-term]
  (let [match
        (fn [fg fc]
          (let [fgl (label language fg)
                fcl (label language fc)
                cs (matching-component-labels language search-term [] fc)
                fgl-pos (string/match-pos fgl search-term)
                fcl-pos (string/match-pos fcl search-term)]
            (when (or fgl-pos fcl-pos (seq cs))
              ;; this matches, return result with relevance
              {:fgl-pos fgl-pos
               :fcl-pos fcl-pos
               :fgl fgl
               :fcl fcl
               :components cs})))]
    (->> (for [fg fgroups
               fc (:fclass/_fgroup fg)
               :let [m (match fg fc)]

               :when m]
           [m [fg fc (sort-by (juxt :pos :component)
                              (:components m))]])

         ;; Sort results based on match positions and actual labels
         (sort-by (fn [[{:keys [fcl fgl fcl-pos fgl-pos components]} _]]
                    [(or fgl-pos 99999)
                     (or fcl-pos 99999)
                     (or (reduce min (map :pos components)) 99999)
                     fgl fcl]))

         ;; Return only the [fg fc components] as results
         (mapv second))))
