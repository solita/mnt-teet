(ns teet.asset.asset-boq
  "Asset Bill of Quantities Excel export."
  (:require [teet.asset.asset-model :as asset-model]
            [teet.asset.asset-db :as asset-db]
            [teet.asset.asset-type-library :as asset-type-library]
            [dk.ative.docjure.spreadsheet :as spreadsheet]
            [teet.localization :refer [tr]]
            [teet.localization :as localization]
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

(defn- cost-group-rows [atl language cost-groups include-unit-prices?]
  (let [ident->label #(asset-type-library/label language (asset-type-library/item-by-ident atl %))]
    (concat
     (map-indexed
      (fn [i {:keys [type quantity quantity-unit cost-per-quantity-unit]
              status :common/status :as row}]
        (let [r (+ 5 i)]
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
           {:formula (str "E" r "*G" r)}]))
      cost-groups)

     ;; Add summary row
     (list
      (conj (vec (repeat 7 nil))
            [{:formula (str "SUM(H5:H" (+ 4 (count cost-groups)) ")")}
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
  `atl`  the asset type library
  `cost-groups` the project cost groups totals
  `project-id` THK project id
  `language` the language to export in
  `include-unit-prices?` if false, unit prices are not included
  `version` the pulled BOQ version entity
  "
  [{:keys [atl cost-groups project-id language include-unit-prices?
           version] :as a}]
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
                  (when style
                    (spreadsheet/set-cell-style! cell (style! style))))))]
     (r! [(tr [:project :tabs :cost-items]) nil nil nil nil nil
          ;; PENDING: versioning&locking not implemented yet, this is dummy
          "Versioon:" "H0"])
     (r! [nil "Objekti nr:" project-id])
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
