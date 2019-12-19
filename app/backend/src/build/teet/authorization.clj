(ns teet.authorization
  (:require [dk.ative.docjure.spreadsheet :as sheet]
            [clojure.string :as str]
            [clojure.pprint :as pp]))

(defn- keywordize [s]
  (-> s
      str/trim
      str/lower-case
      (str/replace #" +" "-")
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
   "+" :link

   ;; Slash means read-only access
   "/" :read})

(defn get-authorizations-from-sheet [sheet-path]
  (let [sheet (->> (sheet/load-workbook sheet-path)
                   (sheet/select-sheet "Authorization"))

        ;; Roles are in the 4th row of the sheet
        role->column (role->column-mapping sheet)]

    ;; Read all authorization rows (skip 4 header rows)
    (into
     {}
     (for [row (drop 4 (sheet/row-seq sheet))
           :when (not (str/blank? (sheet/read-cell (first (sheet/cell-seq row)))))
           :let [[section functionality & _] (map sheet/read-cell
                                                  (sheet/cell-seq row))
                 row-num (inc (.getRowNum row))]]
       [(keyword (-> section str/trim str/lower-case)
                 (name (keywordize functionality)))
        (reduce-kv
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
         role->column)]))))

(defn pretty-print [x]
  (with-out-str (pp/pprint x)))

(defn- write-authorization-edn-from-sheet! [sheet-path]
  (->> sheet-path
       get-authorizations-from-sheet
       pretty-print
       (spit "resources/authorization.edn")))

(defn -main [& [sheet-path]]
  (if sheet-path
    (do (write-authorization-edn-from-sheet! sheet-path)
        (println "Authorization edn file successfully written!"))
    (println "Please provide authorization sheet path as argument.")))
