(ns teet.asset-schema
  (:require [dk.ative.docjure.spreadsheet :as sheet]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]))

(defn- keywordize [pfx name]
  (let [clean #(-> %
                   (str/replace "(" "")
                   (str/replace ")" "")
                   (str/replace "/" "_"))]
    (keyword (clean pfx) (clean name))))

(defn- parse-prefix-and-name [s]
  (when-let [[_ pfx name] (re-matches #"\[([^:]+):([^]]+)\]" s)]
    [pfx name]))

(defn- parse-name
  "Turn string name like \"[property:size]\" into keyword :property/size"
  [s]
  (some-> s
          parse-prefix-and-name
          (apply keywordize)))

(defn- update-name-columns [name-keys rows]
  (mapv #(reduce
          (fn [row k]
            (update row k parse-name))
          % name-keys)
        rows))

(def column-keys
  (mapv (comp keyword str char)
        (range (int \A) (int \Z))))

(defn filter-required-columns [required-columns rows]
  (filter (fn [row]
            (every? #(let [v (row %)]
                       (and (some? v)
                            (or (not (string? v))
                                (not (str/blank? v))))) required-columns))
          rows))

(defn- read-sheet
  ([name columns name-keys workbook]
   (read-sheet name columns name-keys #{} workbook))
  ([name columns name-keys required-columns workbook]
   (->> workbook
        (sheet/select-sheet name)
        (sheet/select-columns
         (zipmap column-keys columns))
        ;; First row is the header
        (drop 1)
        (filter-required-columns required-columns)
        ;; Parse name columns
        (update-name-columns name-keys))))

(def read-feature-groups
  (partial read-sheet
           "fgroup"
           [:name :asset?
            :label-et :label-en
            :description-et :description-en
            :agreed? :comment]
           #{:name}))

(def read-feature-classes
  (partial read-sheet
           "fclass"
           [:name :fgroup :oid-prefix
            :label-et :label-en
            :description-et :description-en
            :agreed?]
           #{:name :fgroup}))

(def read-ctypes
  (partial read-sheet
           "ctype"
           [:name :part-of :inherits-location? :quantity-unit
            :label-et :label-en
            :description-et :description-en
            :agreed? :comment]
           #{:name :part-of}
           #{:name :part-of}))

(def read-pset
  (partial read-sheet
           "pset"
           [:name :ctype :datatype :unit :min-value :max-value
            :cost-grouping? :mandatory?
            :label-et :label-en
            :description-et :description-en
            :comment]
           #{:name :ctype}))

(def read-list-items
  (partial read-sheet
           "ListItems"
           [:name :property
            :label-et :label-en
            :agreed?
            :comment]
           #{:name :property}))

(defn- without-empty [m]
  (into {}
        (remove (comp #(or (nil? %)
                           (and (string? %) (str/blank? %)))
                      second))
        m))

(defn- ->long [x]
  (when x
    (if (string? x)
      (try
        (Long/parseLong x)
        (catch NumberFormatException e
          (println "Can't parse number: " x)
          nil))
      (long x))))

(defn common-attrs [type {:keys [name comment label-et label-en
                                 description-et description-en]}]
  (without-empty
   (merge
    {:db/id (str name)
     :db/ident name
     :db/doc comment
     :asset-schema/type type}
    (when (or label-et label-en)
      {:asset-schema/label [(or label-et "") (or label-en "")]})
    (when (or description-et description-en)
      {:asset-schema/description [(or description-et) (or description-en "")]}))))

(defn- map-by-name [things]
  (into {} (map (juxt :name identity)) things))

(defn- attribute-name
  "Builds the attribute name from its `:name` and `:ctype`.
   `(attribute-name {:name :barrier :ctype :barrierworkingwidth}) => :barrier/barrierworkingwidth`"
  ;; TODO: Why is datatype needed here?
  [{n :name :keys [ctype datatype] :as _attr}]
  (when (and n ctype datatype)
    (keyword (name ctype) (name n))))

(defn- min-and-max-values [p]
  (merge (when-let [min (->long (:min-value p))]
           {:attribute/min-value min}
           ;; TODO: Handle min-value-ref

           ;; TODO: `attribute-name` can be used with the attribute
           ;; itself since we know the ctype. We don't know that for
           ;; the reference. Can we assume it's a property of parent?
           ;; Or we could build a kv-store mapping raw attributes to
           ;; their namespaces. This assumes that the raw attributes
           ;; are globally unique, not only within a given namespace.n
           ;; Example value "[property:barrierworkingwidth]"
           )
         (when-let [max (->long (:max-value p))]
           {:attribute/max-value max})))

(defn generate-asset-schema [sheet-file]
  (with-open [in (io/input-stream sheet-file)]
    (let [workbook (sheet/load-workbook in)
          fgroup (read-feature-groups workbook)
          fclass (read-feature-classes workbook)

          fclass-by-name (map-by-name fclass)

          ctype (into [{:name :ctype/common
                        :comment "Attributes common to all assets and components"}]
                      (read-ctypes workbook))
          ctypes-by-name (map-by-name ctype)
          pset (read-pset workbook)
          list-items (read-list-items workbook)

          attrs-by-name
          (into {}
                (keep (fn [attr]
                        (when-let [attr-name (attribute-name attr)]
                          [(:name attr)
                           (assoc attr :name attr-name)])))
                pset)
          exists? (into #{}
                        (map :name)
                        (concat fgroup fclass ctype pset list-items))]
      (vec
       (concat
        ;; Output feature groups
        (for [fg fgroup
              :when (:name fg)]
          (common-attrs :asset-schema.type/fgroup fg))

        ;; Output feature classes
        (for [fc fclass
              :when (:name fc)]
          (merge
           (common-attrs :asset-schema.type/fclass fc)
           {:fclass/fgroup (str (:fgroup fc))}
           (when-let [op (:oid-prefix fc)]
             {:fclass/oid-prefix op})))

        ;; Output component types
        (for [ct ctype
              :when (or (= (:name ct) :ctype/common)
                        (and (:name ct)
                             (or (not (contains? ct :part-of))
                                 (exists? (:part-of ct)))))]

          (merge
           (common-attrs :asset-schema.type/ctype ct)
           (when-let [il (:inherits-location? ct)]
             {:component/inherits-location? il})
           (when-let [qu (:quantity-unit ct)]
             {:component/quantity-unit (str/trim qu)})
           (when (contains? ct :part-of)
             {:ctype/parent (str (:part-of ct))})))

        ;; Output attributes namespaced by ctype
        (for [p (vals attrs-by-name)
              :let [valueType (case (:datatype p)
                                ("text" "alphanumeric") :db.type/string
                                "listitem" :db.type/ref
                                "integer" :db.type/long
                                "number" :db.type/bigdec
                                "datetime" :db.type/instant
                                nil)]
              :when (and valueType
                         (exists? (:ctype p)))]
          (without-empty
           (merge
            (common-attrs :asset-schema.type/attribute p)
            {:db/cardinality :db.cardinality/one ; PENDING: can be many?
             :db/valueType valueType
             :asset-schema/unit (:unit p)
             :attribute/parent (str (:ctype p))
             :attribute/cost-grouping? (:cost-grouping? p)
             :attribute/mandatory? (:mandatory? p)}
            (min-and-max-values p))))

        ;; Output enum values
        (for [item list-items
              :let [attr (attrs-by-name (:property item))
                    attr-name (get-in attrs-by-name [(:property item) :name])
                    ctype-or-fclass (or (get ctypes-by-name (:ctype attr))
                                        (get fclass-by-name (:ctype attr)))
                    all-exist? (and attr ctype-or-fclass
                                    (:name item)
                                    (exists? (:property item)))]
              :when all-exist?]
          (merge
           (common-attrs :asset-schema.type/enum item)
           {:enum/attribute (str attr-name)})))))))

(defn -main [& [sheet-path]]
  (let [sheet-file (some-> sheet-path io/file)]
    (if (some-> sheet-file .canRead)
      (let [schema (generate-asset-schema sheet-file)]
        (spit "../backend/resources/asset-schema.edn"
              (with-out-str
                (pp/pprint schema))))
      (println "Specify location of TEET_ROTL.xlsx as argument."))))
