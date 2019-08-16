(ns teet.thk.thk-import
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [teet.thk.thk-db :as thk-db]
            [clojure.string :as str]))

(defn parse-thk-export-csv [input]
  (let [[headers & rows]
        (-> input
            (io/reader :encoding "ISO-8859-1")
            (csv/read-csv :separator \;))]
    (map #(zipmap headers %) rows)))

(defn ->int [str]
  (when-not (str/blank? str)
    (Integer/parseInt str)))

(defn ->num [str]
  (when-not (str/blank? str)
    (BigDecimal. str)))

(defn import-thk-projects! [db input]
  (doseq [prj (parse-thk-export-csv input)]
    (thk-db/upsert-thk-project!
     db
     {:id (->int (prj "PlanObject.Id"))
      :plan_group_fk (prj "PlanObject.PlanGroupFk")
      :road_nr (->int (prj "PlanObject.RoadNr"))
      :bridge_nr (->int (prj "PlanObject.BridgeNr"))

      :km_start (->num (prj "PlanObject.KmStart"))
      :km_end (->num (prj "PlanObject.KmEnd"))
      :carriageway (->int (prj "PlanObject.Carriageway"))

      :name (prj "PlanObject.ObjectName")
      :oper_method (prj "PlanObject.OperMethod")
      :object_type_fk (prj "ObjectType.ObjectTypeFK")
      :region_fk (->int (prj "PlanObject.regionfk"))
      :county_fk (->int (prj "PlanObject.countyfk"))

      :customer_unit (prj "PlanObject.CustomerUnit")
      :updated (prj "PlanObject.UpdStamp")
      :procurement_no (prj "Procurement.ProcurementNo")
      :procurement_id (->int (prj "Procurement.ID"))

      :activity_id (->int (prj "Activity.Id"))
      :activity_type_fk (prj "Activity.ActivityTypeFK")

      :estimated_start (prj "Activity.EstStart")
      :estimated_end (prj "Activity.EstEnd")})))



(def db {:dbtype "postgres"
         :dbname "teet"
         :user "teet"})

(def i1 "/Volumes/GoogleDrive/Jaetut Drivet/Solita   Customers/Maanteeamet/Teiden elinkaaritiedon hallinta/Toteutusprojekti/Customer provided materials/THK_export.csv")

(def t1 (parse-thk-export-csv i1))
