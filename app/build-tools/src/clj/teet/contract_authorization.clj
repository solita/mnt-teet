(ns teet.contract-authorization
  (:require [dk.ative.docjure.spreadsheet :as sheet]
            [teet.common :as common]
            [clojure.string :as str]
            [clojure.pprint :as pp]))

(defn access?
  [val]
  (= "+" val))

(def excel-role->teet-role
  {
   ;;These might require an enum value mapping to role enums at some point.
   :admin :admin
   :ta-responsible-person :ta-responsible-person
   :ta-project-manager :ta-project-manager
   :ta-consultant :ta-consultant ; old:  :internal-consultant

   :company-representative :company-contract-employee.role/company-representative
   :company-project-manager :company-contract-employee.role/company-project-manager
   :supervisor :company-contract-employee.role/supervisor
   :team-member :company-contract-employee.role/team-member

   :subcontractor :company-contract-employee.role/subcontractor})


(defn get-authorizations-from-sheet [sheet-path]
  (let [sheet (->> (sheet/load-workbook sheet-path)
                   (sheet/select-sheet "Contract authorization"))
        role->column (common/role->column-mapping sheet 7 2)]
    (into
      {}
      (for [row (drop 4 (sheet/row-seq sheet))
            :when (-> row
                      (sheet/cell-seq)
                      (first)
                      sheet/read-cell
                      str/blank?
                      not)
            :let [[section functionality & _] (map sheet/read-cell
                                                   (sheet/cell-seq row))
                  row-num (inc (.getRowNum row))]]
        [(keyword (name (common/keywordize section))
                  (name (common/keywordize functionality)))
         (reduce-kv
           (fn [perms role column]
             (if (->> sheet
                      (sheet/select-cell (str column row-num))
                      sheet/read-cell
                      access?)
               (conj perms
                     (excel-role->teet-role role))
               perms))
           #{}
           role->column)]))))

(defn pretty-print [x]
  (with-out-str (pp/pprint x)))

(defn- write-authorization-edn-from-sheet!
  [sheet-path]
  (->> sheet-path
       get-authorizations-from-sheet
       pretty-print
       (spit "../backend/resources/contract-authorization.edn")))

(defn -main [& [sheet-path]]
  (if sheet-path
    (do (write-authorization-edn-from-sheet! sheet-path)
        (println "Contract-authorization edn file successfully written!"))
    (println "Please provide contract-authorization sheet path as argument.")))
