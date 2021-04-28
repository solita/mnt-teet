(ns teet.asset.asset-boq
  "Asset Bill of Quantities Excel export."
  (:require [teet.asset.asset-model :as asset-model]
            [teet.asset.asset-type-library :as asset-type-library]
            [dk.ative.docjure.spreadsheet :as spreadsheet]
            [teet.localization :refer [tr tr-enum] :as localization]
            [clojure.string :as str]))

(defn- header-row [atl language]
  (into [nil]
        (mapcat
         (fn [c]
           (cond
             (= c :common/status)
             [(asset-type-library/label language (asset-type-library/item-by-ident atl c))]

             ;; quantity and unit shown separately so quantity can be used in formula
             (= c :quantity)
             [(tr [:asset :totals-table :quantity])
              (tr [:asset :type-library :unit])]

             :else
             [(tr [:asset :totals-table c])]))
                asset-model/cost-totals-table-columns)))

(defn- groups-with-fgroup-and-fclass
  "Add localized `:fgroup` and `:fclass` to each cost group and sort by both."
  [atl language cost-groups]
  (sort-by
   (juxt :fgroup :fclass)
   (map
    #(let [[fg fc] (map
                    (partial asset-type-library/label language)
                    (take 2 (asset-type-library/type-hierarchy atl (:type %))))]
       (merge % {:fgroup fg
                 :fclass fc}))
    cost-groups)))

(defn- cost-group-rows [atl language cost-groups include-unit-prices?]
  (let [ident->label #(asset-type-library/label language (asset-type-library/item-by-ident atl %))

        cost-groups (groups-with-fgroup-and-fclass atl language cost-groups)
        cg-row (fn [r {:keys [type quantity quantity-unit cost-per-quantity-unit]
                       status :common/status :as row}]
                 [nil
                  (ident->label type)
                  [(str/join "\n"
                             (map (fn [[k v u]]
                                    (str k ": " v
                                         (when u
                                           (str "\u00a0" u))))
                                  (asset-type-library/format-properties language atl row)))
                   {:wrap true}]
                  (ident->label (:db/ident status))
                  quantity
                  quantity-unit
                  (when include-unit-prices? cost-per-quantity-unit)
                  {:formula (str "E" r "*G" r)}])
        cost-groups-output
        (reduce
         (fn [{:keys [r previous-cg rows]} cg]
           (cond
             ;; Fgroup changed, output fgroup and class before
             ;; the cost group row
             (not= (:fgroup previous-cg) (:fgroup cg))
             {:r (+ r 4)
              :previous-cg cg
              :rows (-> rows
                        (conj [])
                        (conj [nil [(:fgroup cg) {:col-span 6
                                                  :halign :center
                                                  :font {:bold true
                                                         :size 14}}]])
                        (conj [nil [(:fclass cg) {:col-span 6
                                                  :halign :center
                                                  :font {:bold true
                                                         :size 12}}]])
                        (conj (cg-row (+ r 3) cg)))}

             ;; Only fclass changed, output fclass before
             ;; the cost group row
             (not= (:fclass previous-cg) (:fclass cg))
             {:r (+ r 2)
              :previous-cg cg
              :rows (-> rows
                        (conj [nil [(:fclass cg) {:col-span 6
                                                  :halign :center
                                                  :font {:bold true
                                                         :size 12}}]])
                        (conj (cg-row (inc r) cg)))}

             ;; Regular row, now change in fgroup/fclass
             :else
             {:r (inc r)
              :previous-cg cg
              :rows
              (conj rows (cg-row r cg))}))
         {:r 5 :previous-cg nil :rows []}
         cost-groups)]

    (concat
     (:rows cost-groups-output)

     ;; Add summary row
     (list
      (conj (vec (repeat 7 nil))
            [{:formula (str "SUM(H5:H" (dec (:r cost-groups-output)) ")")}
             {:font {:bold true}}])))))

(def ^:private
  column-widths-in-chars
  "widths in chars for different cost table columns"
  {1 20 ; B=type
   2 50 ; C=properties
   3 25 ; D=status
   4 10 ; E=amount
   5 5  ; F=unit
   6 10 ; G=unit price
   7 12 ; H=total price
   })

(defn- set-widths! [sheet widths]
  (doseq [[c w] widths]
    ;; excel units are 1/256th of a character
    (.setColumnWidth sheet c (* 256 w))))

(defn boq-spreadsheet
  "Create Bill of Quantities spreadsheet from cost group summary info.

  Takes the following keys:
  `:atl`  the asset type library
  `:cost-groups` the project cost groups totals
  `:project-id` THK project id
  `:language` the language to export in
  `:include-unit-prices?` if false, unit prices are not included
  `:version` the pulled BOQ version entity
  `:project-name` the name of the THK project
  "
  [{:keys [atl cost-groups project-id language include-unit-prices?
           version project-name] :as a}]
  (def *a a)
   (let [sheet-name (str "THK-" project-id)
         wb (spreadsheet/create-workbook sheet-name [])
         sheet (doto (.getSheet wb sheet-name)
                 (set-widths! column-widths-in-chars))
         style! (memoize
                 (partial spreadsheet/create-cell-style! wb))
         r! (fn [row-values]
              (let [row (spreadsheet/add-row! sheet [])]
                (doseq [i (range (count row-values))
                        :let [v (nth row-values i)
                              [v style] (if (vector? v)
                                          v
                                          [v nil])
                              cell (.createCell row i)]]
                  (if (map? v)
                    (let [{:keys [formula]} v]
                      (.setCellType cell org.apache.poi.ss.usermodel.Cell/CELL_TYPE_FORMULA)
                      (.setCellFormula cell formula))
                    (spreadsheet/set-cell! cell v))
                  (when-let [s (:col-span style)]
                    (let [a (.getAddress cell)
                          r (.getRow a)
                          c (.getColumn a)
                          region (org.apache.poi.ss.util.CellRangeAddress. r r c (+ c s))]
                      (.addMergedRegion sheet region)))
                  (when style
                    (spreadsheet/set-cell-style! cell (style! style))))))]
     (r! [(tr [:project :tabs :cost-items])
          project-name project-id nil nil nil

          (tr [:fields :boq-export/version])
          (if version
            (str (tr-enum (:boq-version/type version)) " v"
                 (:boq-version/number version))
            (tr [:asset :unofficial-version]))])
     (r! [])
     (r! [])
     (r! (header-row atl language))
     (doseq [row (cost-group-rows atl language cost-groups include-unit-prices?)]
       (r! row))
     wb))

(defn export-boq
  "Create BOQ Excel and write it into the `out` stream.
  Takes `boq-data` map of information to export, see [[boq-spreadsheet]]."
  [out boq-data]
  (spreadsheet/save-workbook-into-stream!
   out
   (boq-spreadsheet boq-data)))
