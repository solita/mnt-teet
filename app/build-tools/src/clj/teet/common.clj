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
  [sheet role-count nth-row]
  (into {}
        (for [cell (take role-count
                         (drop 2                            ;; Discard first 2 columns
                               (sheet/cell-seq
                                 ;; take only the row where the roles are
                                 (nth (sheet/row-seq sheet) nth-row))))
              :let [role (-> cell sheet/read-cell keywordize)
                    cell-ref (sheet/cell-reference cell)
                    column (subs cell-ref 0 (dec (count cell-ref)))]]
          [role column])))
