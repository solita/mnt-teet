(ns teet.thk.thk-mapping
  "Mapping of information between THK CSV and TEET attribute keywords"
  (:require [clojure.string :as str]
            [clojure.java.io :as io])
  (:import (java.text SimpleDateFormat)))

(defn- blank? [s]
  (or (not s)
      (str/blank? s)))

(defn ->num [str]
  (when-not (blank? str)
    (BigDecimal. str)))

(defn ->int [str]
  (when-not (blank? str)
    (Long/parseLong str)))

(defn- ->date [date-str]
  (when-not (blank? date-str)
    (.parse (SimpleDateFormat. "yyyy-MM-dd")
            date-str)))

(defn date-str [date]
  (.format (SimpleDateFormat. "yyyy-MM-dd") date))

(defn datetime-str [date]
  (.format (SimpleDateFormat. "yyyy-MM-dd HH:mm:ss.SSS") date))

(defn- km->m [km]
  (when km
    (int (* 1000 km))))

(defn- reverse-mapping [m]
  (into {}
        (map (fn [[k v]]
               [v k]))
        m))

(defn- m->km-str [m]
  ;; Format meters as kilometers with 3 decimal point precision
  ;; using "." as the decimal separator.
  (String/format java.util.Locale/ENGLISH
                 "%.3f" (into-array Object [(/ m 1000.0)])))

(def phase-name->lifecycle-type
  ;; FIXME: how to map "Eelproj"?
  {"projetapp" :thk.lifecycle-type/design
   "ehitetapp" :thk.lifecycle-type/construction})

(def lifecycle-type->phase-name
  (reverse-mapping phase-name->lifecycle-type))

(def thk-activity-type->activity-name
  {;; THK has activities without mapping in TEET,
   ;; we skip those as they should be removed in
   ;; the future.
   ;;
   ;; 4000 Planeering
   ;; 4001 Uuring/Anal
   ;; 4002 KMH
   "4003" :activity.name/detailed-design ;; Pöhiprojekt
   "4004" :activity.name/land-acquisition ;; Maaost
   "4005" :activity.name/construction ;; Teostus
   ;; 4010 ekspertiis
   ;; 4011 LOA proj
   "4012" :activity.name/pre-design ;; Eskiisproj
   "4013" :activity.name/preliminary-design  ;; Eelproj
   "4014" :activity.name/design-requirements ;; Proj. tingimused
   })

(def activity-name->thk-activity-type
  (reverse-mapping thk-activity-type->activity-name))

(def thk-activity-status->status
  {;;  4100 Ettevalmistamisel
   ;;  4101 Hankemenetluses
   "4102" :activity.status/in-progress  ;; Töös
   ;;  4103 Garantiiaeg
   "4104" :activity.status/completed ;; Lõpetatud
   ;;  4106 Hankeplaanis
   ;; Unmapped status
   ;;:activity.status/other
   })

(def status->thk-activity-status
  (reverse-mapping thk-activity-status->status))


(def object-integration-info-fields
  #{:object/groupfk :object/groupname
    :object/regionfk :object/thkupdstamp
    :object/statusfk :object/statusname})

(def phase-integration-info-fields
  #{:phase/thkupdstamp :phase/cost :phase/typefk})

(def activity-integration-info-fields
  #{:activity/typefk :activity/shortname :activity/statusname
    :activity/contract
    :activity/guaranteeexpired :activity/thkupdstamp :activity/cost})

(def csv-column-names
  ["object_id"
   "object_groupfk"
   "object_groupshortname"
   "object_groupname"
   "object_roadnr"
   "object_carriageway"
   "object_kmstart"
   "object_kmend"
   "object_bridgenr"
   "object_name"
   "object_projectname"
   "object_owner"
   "object_regionfk"
   "object_regionname"
   "object_thkupdstamp"
   "object_teetupdstamp"
   "object_statusfk"
   "object_statusname"
   "phase_id"
   "phase_teetid"
   "phase_typefk"
   "phase_shortname"
   "phase_eststart"
   "phase_estend"
   "phase_thkupdstamp"
   "phase_teetupdstamp"
   "phase_cost"
   "activity_id"
   "activity_teetid"
   "activity_taskid"
   "activity_taskdescr"
   "activity_typefk"
   "activity_shortname"
   "activity_statusfk"
   "activity_statusname"
   "activity_contract"
   "activity_eststart"
   "activity_estend"
   "activity_actualstart"
   "activity_actualend"
   "activity_guaranteeexpired"
   "activity_thkupdstamp"
   "activity_teetupdstamp"
   "activity_teetdelstamp"
   "activity_cost"
   "activity_procurementno"
   "activity_procurementid"])

(defn estonian-person-id->user [id]
  (when-not (str/blank? id)
    {:db/id (str "new-user-" id)
     :user/person-id (if (re-matches #"^\d+$" id)
                       ;; If id is a string of digits: it is a person id without EE prefix
                       (str "EE" id)
                       id)}))

(defonce estonian-translations
  (delay
    (-> "et.edn"
        io/resource io/reader slurp
        read-string)))

(defn tr-enum [value]
  (let [value (get-in @estonian-translations [:enum (if (keyword? value)
                                                      value
                                                      (:db/ident value))])]
    (if value
      value
      (throw (ex-info "Can't find estonian translation"
                      {:enum value})))))

(def thk->teet
  {;; Object/project fields
   "object_id" {:attribute :thk.project/id}
   "object_groupfk" {:attribute :object/groupfk}
   "object_groupshortname" {:attribute :thk.project/repair-method}
   "object_groupname" {:attribute :object/groupname}
   "object_roadnr" {:attribute :thk.project/road-nr
                    :parse ->int}
   "object_carriageway" {:attribute :thk.project/carriageway
                         :parse ->int}
   "object_kmstart" {:attribute :thk.project/start-m
                     :parse (comp km->m ->num)
                     :format m->km-str
                     :override-kw :thk.project/custom-start-m}
   "object_kmend" {:attribute :thk.project/end-m
                   :parse (comp km->m ->num)
                   :format m->km-str
                   :override-kw :thk.project/custom-end-m}
   "object_bridgenr" {:attribute :thk.project/bridge-nr
                      :parse ->int}
   "object_name" {:attribute :thk.project/name}
   "object_projectname" {:attribute :thk.project/project-name}
   "object_owner" {:attribute :thk.project/owner
                   :parse estonian-person-id->user
                   :format :user/person-id}
   "object_regionfk" {:attribute :object/regionfk}
   "object_regionname" {:attribute :thk.project/region-name}
   "object_thkupdstamp" {:attribute :object/thkupdstamp}
   ;;"object_teetupdstamp"
   "object_statusfk" {:attribute :object/statusfk}
   "object_statusname" {:attribute :object/statusname}

   ;; Phase/lifecycle fields
   "phase_id" {:attribute :thk.lifecycle/id}
   "phase_teetid" {:attribute :lifecycle-db-id
                   :parse ->int}
   "phase_typefk" {:attribute :phase/typefk}
   "phase_shortname" {:attribute :thk.lifecycle/type
                      :parse phase-name->lifecycle-type
                      :format (comp lifecycle-type->phase-name :db/ident)}
   "phase_eststart" {:attribute :thk.lifecycle/estimated-start-date
                     :parse ->date
                     :format date-str}
   "phase_estend" {:attribute :thk.lifecycle/estimated-end-date
                   :parse ->date
                   :format date-str}
   "phase_thkupdstamp" {:attribute :phase/thkupdstamp}
   ;"phase_teetupdstamp"
   "phase_cost" {:attribute :phase/cost}

   ;; Activity fields
   "activity_id" {:attribute :thk.activity/id
                  ;; Tasks sent to THK have activity id as well
                  :task {:attribute :thk.activity/id}}
   "activity_teetid" {:attribute :activity-db-id
                      :parse ->int}
   "activity_taskid" {:attribute :activity/task-id
                      :parse ->int
                      :task {:attribute :db/id}}
   "activity_taskdescr" {:attribute :activity/task-description
                         :task {:attribute :task/type
                                :format tr-enum}}
   "activity_typefk" {:attribute :activity/name
                      :parse thk-activity-type->activity-name
                      :format (comp activity-name->thk-activity-type :db/ident)
                      :task {:attribute :task/type
                             :format #(get-in % [:thk/task-type :thk/code])}}
   "activity_shortname" {:attribute :activity/shortname
                         :task {:attribute :task/type
                                :format #(some-> % :db/ident name)}}
   "activity_statusfk" {:attribute :activity/status
                        :parse thk-activity-status->status
                        :format (comp status->thk-activity-status :db/ident)
                        :task {:attribute :task/status
                               :format #(case (:db/ident %)
                                         :task.status/in-progress "4102"
                                         :task.status/completed "4104"
                                         "")}}
   "activity_statusname" {:attribute :activity/statusname
                          :task {:attribute :task/status
                                 :format #(case (:db/ident %)
                                            :task.status/in-progress "Töös"
                                            :task.status/completed "Lõpetatud"
                                            "")}}
   "activity_contract" {:attribute :activity/contract}
   "activity_eststart" {:attribute :activity/estimated-start-date
                        :parse ->date
                        :format date-str
                        :task {:attribute :task/estimated-start-date}}
   "activity_estend" {:attribute :activity/estimated-end-date
                      :parse ->date
                      :format date-str
                      :task {:attribute :task/estimated-end-date}}
   "activity_actualstart" {:attribute :activity/actual-start-date
                           :parse ->date
                           :format date-str
                           :task {:attribute :task/actual-start-date}}
   "activity_actualend" {:attribute :activity/actual-end-date
                         :parse ->date
                         :format date-str
                         :task {:attribute :task/actual-end-date}}
   "activity_guaranteeexpired" {:attribute :activity/guaranteeexpired}
   "activity_thkupdstamp" {:attribute :activity/thkupdstamp}
   ;;"activity_teetupdstamp" :activity/teetupdstamp
   "activity_teetdelstamp" {:attribute (juxt :meta/deleted? :meta/modified-at)
                            :parse (constantly nil)
                            :format (fn [[d? at]] (if d? (datetime-str at) ""))
                            :task {:attribute (juxt :meta/deleted? :meta/modified-at)}}
   "activity_cost" {:attribute :activity/cost}
   "activity_procurementno" {:attribute :activity/procurement-nr}
   "activity_procurementid" {:attribute :activity/procurement-id}})

(defn uuid->number [uuid]
  (let [bb (java.nio.ByteBuffer/wrap (byte-array 16))]
    (.putLong bb (.getMostSignificantBits uuid))
    (.putLong bb (.getLeastSignificantBits uuid))
    (BigInteger. 1 (.array bb))))

(defn number->uuid [n]
  (java.util.UUID. 0 n))
