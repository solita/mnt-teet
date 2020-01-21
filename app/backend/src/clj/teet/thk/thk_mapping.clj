(ns teet.thk.thk-mapping
  "Mapping of information between THK CSV and TEET attribute keywords"
  (:require [clojure.string :as str])
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

(defn- km->m [km]
  (when km
    (int (* 1000 km))))

(defn- phase-name->lifecycle-type [phase_shortname]
  (case phase_shortname
    "projetapp" :thk.lifecycle-type/design
    "ehitetapp" :thk.lifecycle-type/construction

    ;; FIXME: how to map "Eelproj"?
    nil))

(defn thk-activity-type->activity-name [act_typefk]
  (case act_typefk
    ;; THK has activities without mapping in TEET,
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
    "4013" :activity.name/preliminary-design ;; Eelproj

    ;; Default to other
    nil))

(defn thk-activity-status->status [act_statusfk]
  (case act_statusfk
    ;;  4100 Ettevalmistamisel
    ;;  4101 Hankemenetluses
    "4102" :activity.status/in-progress ;; Töös
    ;;  4103 Garantiiaeg
    "4104" :activity.status/completed ;; Lõpetatud
    ;;  4106 Hankeplaanis
    ;; Unmapped status
    :activity.status/other))


(def object-integration-info-fields
  #{:object/groupfk :object/groupshortname :object/groupname
    :object/owner :object/regionfk :object/regionname
    :object/thkupdstamp :object/statusfk :object/statusname})

(def phase-integration-info-fields
  #{:phase/thkupdstamp :phase/cost})

(def activity-integration-info-fields
  #{:activity/typefk :activity/shortname :activity/statusname
    :activity/contract :activity/actualstart :activity/actualend
    :activity/guaranteeexpired :activity/thkupdstamp :activity/cost
    :activity/procurementno :activity/procurementid})

(def thk->teet
  {;; Object/project fields
   "object_id" [:thk.project/id]
   "object_groupfk" [:object/groupfk]
   "object_groupshortname" [:object/groupshortname]
   "object_groupname" [:object/groupname]
   "object_roadnr" [:thk.project/road-nr ->int]
   "object_carriageway" [:thk.project/carriageway ->int]
   "object_kmstart" [:thk.project/start-m (comp km->m ->num)]
   "object_kmend" [:thk.project/end-m (comp km->m ->num)]
   "object_bridgenr" [:thk.project/bridge-nr ->int]
   "object_name" [:thk.project/name]
   "object_projectname" [:thk.project/project-name]
   "object_owner" [:object/owner]
   "object_regionfk" [:object/regionfk]
   "object_regionname" [:object/regionname]
   "object_thkupdstamp" [:object/thkupdstamp]
   ;;"object_teetupdstamp"
   "object_statusfk" [:object/statusfk]
   "object_statusname" [:object/statusname]

   ;; Phase/lifecycle fields
   "phase_id" [:thk.lifecycle/id]
   "phase_teetid" [:db/id ->int]
   "phase_typefk" [:phase/typefk]
   "phase_shortname" [:thk.lifecycle/type phase-name->lifecycle-type]
   "phase_eststart" [:thk.lifecycle/estimated-start-date ->date]
   "phase_estend" [:thk.lifecycle/estimated-end-date ->date]
   "phase_thkupdstamp" [:phase/thkupdstamp]
   ;"phase_teetupdstamp"
   "phase_cost" [:phase/cost]

   ;; Activity fields
   "activity_id" [:thk.activity/id]
   "activity_teetid" [:db/id ->int]
   "activity_typefk" [:activity/name thk-activity-type->activity-name]
   "activity_shortname" [:activity/shortname]
   "activity_statusfk" [:activity/status thk-activity-status->status]
   "activity_statusname" [:activity/statusname]
   "activity_contract" [:activity/contract]
   "activity_eststart" [:activity/estimated-start-date ->date]
   "activity_estend" [:activity/estimated-end-date ->date]
   "activity_actualstart" [:activity/actualstart]
   "activity_actualend" [:activity/actualend]
   "activity_guaranteeexpired" [:activity/guaranteeexpired]
   "activity_thkupdstamp" [:activity/thkupdstamp]
   ;;"activity_teetupdstamp" :activity/teetupdstamp
   ;;"activity_teetdelstamp" :activity/teetdelstamp
   "activity_cost" [:activity/cost]
   "activity_procurementno" [:activity/procurementno]
   "activity_procurementid" [:activity/procurementid]})
