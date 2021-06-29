(ns teet.authorization
  (:require [dk.ative.docjure.spreadsheet :as sheet]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [teet.common :as common]))



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
        role->column (common/role->column-mapping sheet 6 3)]

    ;; Read all authorization rows (skip 4 header rows)
    (into
     {}
     (for [row (drop 4 (sheet/row-seq sheet))
           :when (not (str/blank? (sheet/read-cell (first (sheet/cell-seq row)))))
           :let [[section functionality & _] (map sheet/read-cell
                                                  (sheet/cell-seq row))
                 row-num (inc (.getRowNum row))]]
       [(keyword (name (common/keywordize section))
                 (name (common/keywordize functionality)))
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
       (spit "../backend/resources/authorization.edn")))

(defn -main [& [sheet-path]]
  (if sheet-path
    (do (write-authorization-edn-from-sheet! sheet-path)
        (println "Authorization edn file successfully written!"))
    (println "Please provide authorization sheet path as argument.")))
