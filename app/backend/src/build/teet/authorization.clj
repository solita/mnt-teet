(ns teet.authorization
  (:require [dk.ative.docjure.spreadsheet :as sheet]
            [clojure.string :as str]))

(defn- keywordize [s]
  (-> s
      str/lower-case
      (str/replace #" " "-")
      keyword))

(defn- role->column-mapping [sheet]
  (into {}
        (for [cell (drop 2 ; discard A and B columns
                         (sheet/cell-seq
                          ;; Take the 4th row
                          (nth (sheet/row-seq sheet) 3)))
              :let [role (-> cell sheet/read-cell keywordize)
                    cell-ref (sheet/cell-reference cell)
                    column (subs cell-ref 0 (dec (count cell-ref)))]]
          [role column])))

(def permission-type
  {;; Asterisk means access to any project
   "*" :full

   ;; Plus means access to projects that you have the linked role in
   "+" :link})

(defn get-authorizations-from-sheet [sheet-path]
  (let [sheet (->> (sheet/load-workbook sheet-path)
                   (sheet/select-sheet "Authorization"))

        ;; Roles are in the 4th row of the sheet
        role->column (role->column-mapping sheet)]

    ;; Read all authorization rows (skip 4 header rows)
    (for [row (drop 4 (sheet/row-seq sheet))
          :when (not (str/blank? (sheet/read-cell (first (sheet/cell-seq row)))))
          :let [[section functionality & _] (map sheet/read-cell
                                                 (sheet/cell-seq row))
                row-num (inc (.getRowNum row))]]
      {:name (keyword (str/lower-case section)
                      (name (keywordize functionality)))
       :role-permissions (reduce-kv
                          (fn [perms role column]
                            (if-let [permission (->> sheet
                                                     (sheet/select-cell (str column row-num))
                                                     sheet/read-cell
                                                     permission-type)]
                              (assoc perms
                                     role
                                       permission)
                              perms))
                          {}
                          role->column)})))
