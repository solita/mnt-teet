(ns teet.common
  (:require [clojure.string :as str]
            [dk.ative.docjure.spreadsheet :as sheet]))

(defn keywordize [s]
  (-> s
      str/lower-case
      (str/replace #"\([^)]+\)" "")
      str/trim
      (str/replace #" +" "-")
      keyword))

(defn role->column-mapping
  [sheet role-count rows-to-drop]
  (into {}
        (for [cell (take role-count
                         (drop 2                            ;; Discard first 2 columns
                               (sheet/cell-seq
                                 ;; rows to drop until we get to the row where the roles are in excel
                                 (nth (sheet/row-seq sheet) rows-to-drop))))
              :let [role (-> cell sheet/read-cell keywordize)
                    cell-ref (sheet/cell-reference cell)
                    column (subs cell-ref 0 (dec (count cell-ref)))]]
          [role column])))
