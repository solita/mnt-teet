(ns teet.asset.asset-boq
  "Asset Bill of Quantities Excel export."
  (:require [teet.asset.asset-model :as asset-model]
            [teet.asset.asset-db :as asset-db]
            [teet.asset.asset-type-library :as asset-type-library]
            [dk.ative.docjure.spreadsheet :as spreadsheet]))

(defn boq-spreadsheet
  "Create Bill of Quantities spreadsheet from cost group summary info."
  [cost-groups]
  (spreadsheet/create-workbook
   "BillOfQuantities"
   [["boq"]
    nil
    ["type" "foo"]

    ]
   )

  )

(defn export-boq
  "Create BOQ Excel from `cost-groups` and write it into the `out` stream."
  [out cost-groups]
  (spreadsheet/save-workbook-into-stream!
   out
   (boq-spreadsheet cost-groups)))
