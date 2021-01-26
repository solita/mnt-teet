(ns teet.asset-schema
  (:require [dk.ative.docjure.spreadsheet :as sheet]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]))


(defn- parse-name [s]
  (when s
    (when-let [[_ pfx name] (re-matches #"\[([^:]+):([^]]+)\]" s)]
      (keyword pfx name))))

(defn- update-name-columns [name-keys rows]
  (mapv #(reduce
          (fn [row k]
            (update row k parse-name))
          % name-keys)
        rows))

(def column-keys
  (mapv (comp keyword str char)
        (range (int \A) (int \Z))))

(defn- read-sheet [name columns name-keys workbook]
  (->> workbook
       (sheet/select-sheet name)
       (sheet/select-columns
        (zipmap column-keys columns))
       ;; First row is the header
       (drop 1)
       ;; Parse name columns
       (update-name-columns name-keys)))

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
           [:name :fgroup
            :label-et :label-en
            :description-et :description-en
            :agreed?]
           #{:name :fgroup}))

(def read-ctypes
  (partial read-sheet
           "ctype"
           [:name :part-of
            :label-et :label-en
            :description-et :description-en
            :comment]
           #{:name :part-of}))

(def read-pset
  (partial read-sheet
           "pset"
           [:name :ctype :datatype :unit :cost-grouping? :mandatory?
            :label-et :label-en
            :description-et :description-en
            :comment]
           #{:name :ctype :unit}))

(def read-list-items
  (partial read-sheet
           "ListItems"
           [:name :property
            :label-et :label-en
            :comment]
           #{:name :property}))

(defn- without-empty [m]
  (into {}
        (remove (comp #(or (nil? %)
                           (and (string? %) (str/blank? %)))
                      second))
        m))

(defn common-attrs [{:keys [name comment label-et label-en]}]
  (without-empty
   {:db/id (str name)
    :db/ident name
    :db/doc comment
    :asset-schema/label [(or label-et "") (or label-en "")]}))



(defn generate-asset-schema [sheet-file]
  (with-open [in (io/input-stream sheet-file)]
    (let [workbook (sheet/load-workbook in)
          fgroup (read-feature-groups workbook)
          fclass (read-feature-classes workbook)
          ctype (read-ctypes workbook)
          pset (read-pset workbook)
          list-items (read-list-items workbook)
          attrs-by-name
          (into {}
                (keep (fn [{n :name :keys [ctype datatype] :as attr}]
                        (when (and n ctype datatype)
                          [n
                           (assoc attr :name (keyword (name ctype) (name n)))])))
                pset)
          exists? (into #{}
                        (map :name)
                        (concat fgroup fclass ctype pset list-items))]

      (vec
       (concat
        ;; Output feature groups
        (for [fg fgroup
              :when (:name fg)]
          (common-attrs fg))

        ;; Output feature classes
        (for [fc fclass
              :when (:name fc)]
          (merge
           (common-attrs fc)
           {:fclass/fgroup (str (:fgroup fc))}))

        ;; Output component types
        (for [ct ctype
              :when (and (:name ct)
                         (exists? (:part-of ct)))]
          (merge
           (common-attrs ct)
           {:ctype/parent (str (:part-of ct))}))

        ;; Output attributes namespaced by ctype
        (for [p (vals attrs-by-name)]
          (merge
           (common-attrs p)
           {:db/cardinality :db.cardinality/one ; PENDING: can be many?
            :db/valueType (case (:datatype p)
                            ("text" "alphanumeric") :db.type/string
                            "listitem" :db.type/ref
                            "integer" :db.type/long
                            "number" :db.type/bigdec
                            "datetime" :db.type/instant)}))

        ;; Output enum values
        (for [item list-items
              :when (and (:name item)
                         (exists? (:property item)))]
          (merge
           (common-attrs item)
           {:enum/attribute (str (get-in attrs-by-name [(:property item) :name]))})))))))

(defn -main [& [sheet-path]]
  (let [sheet-file (some-> sheet-path io/file)]
    (if (some-> sheet-file .canRead)
      (spit "../backend/resources/asset-schema.edn"
            (with-out-str
              (pp/pprint (generate-asset-schema sheet-file))))
      (println "Specify location of TEET_ROTL.xlsx as argument."))))
