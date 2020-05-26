(ns teet.land.land-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [datomic.client.api :as d]
            [teet.meta.meta-model :as meta-model]
            [teet.util.collection :as cu]
            [teet.util.spec :as su]
            teet.land.land-specs
            [teet.util.datomic :as du]
            [teet.land.land-db :as land-db]))


(defcommand :land/create-land-acquisition
  {:doc "Save a land purchase decision form."
   :context {conn :conn
             user :user}
   :payload {:land-acquisition/keys [impact pos-number area-to-obtain status]
             :keys [cadastral-unit
                    project-id]}
   :project-id [:thk.project/id project-id]
   :authorization {:land/create-land-acquisition {:eid [:thk.project/id project-id]
                                                  :link :thk.project/owner}} ;; TODO needs discussion
   :transact
   [(cu/without-nils
      (merge {:db/id "new land-purchase"
              :land-acquisition/impact impact
              :land-acquisition/status status
              :land-acquisition/project [:thk.project/id project-id]
              :land-acquisition/cadastral-unit cadastral-unit
              :land-acquisition/area-to-obtain area-to-obtain
              :land-acquisition/pos-number pos-number}
             (meta-model/creation-meta user)))]})


(defn land-acquisition-belongs-to-project?
  [db project land-aq-id]
  (boolean
    (ffirst
      (d/q '[:find ?l
             :where [?l :land-acquisition/project ?p]
             :in $ ?p ?l]
           db
           project
           land-aq-id))))

(defcommand :land/update-land-acquisition
  {:doc "Save a land purchase decision form."
   :context {conn :conn
             user :user
             db :db}
   :payload {:land-acquisition/keys [impact status pos-number area-to-obtain]
             :keys [cadastral-unit
                    project-id]
             id :db/id}
   :project-id [:thk.project/id project-id]
   :pre [(land-acquisition-belongs-to-project? db [:thk.project/id project-id] id)]
   :authorization {:land/create-land-acquisition {:eid [:thk.project/id project-id]
                                                  :link :thk.project/owner}}
   :transact
   [(cu/without-nils
      (merge {:db/id id
              :land-acquisition/impact impact
              :land-acquisition/status status
              :land-acquisition/area-to-obtain area-to-obtain
              :land-acquisition/pos-number pos-number}
             (meta-model/modification-meta user)))]})

(defn- maybe-add-db-id [{existing-id :db/id :as data} new-id]
  (if existing-id
    data
    (assoc data :db/id new-id)))

(defn new-compensations-tx
  [compensations]
  (into []
        (keep-indexed
         (fn [i compensation]
           (when (seq compensation)
             (-> compensation
                 (select-keys (su/keys-of :estate-procedure/compensation))
                 (maybe-add-db-id (str "new-third-party-comp-" i))
                 (cu/update-in-if-exists [:estate-compensation/amount] bigdec)))))
        compensations))




(defn new-priced-area-txs
  [priced-areas]
  (into []
        (keep-indexed
         (fn [i area]
           (when (seq area)
             (-> area
                 (select-keys (su/keys-of :estate-procedure/priced-area))
                 (maybe-add-db-id (str "new-area-" i))
                 (cu/update-in-if-exists [:priced-area/area] bigdec)
                 (cu/update-in-if-exists [:priced-area/price-per-sqm] bigdec)))))
        priced-areas))

(defn new-land-exchange-txs
  [land-exchanges]
  (into []
        (keep-indexed
         (fn [i exchange]
           (when (seq exchange)
             (-> exchange
                 (select-keys (su/keys-of :estate-procedure/land-exchange))
                 (maybe-add-db-id (str "new-exchange-" i))
                 (cu/update-in-if-exists [:land-exchange/exchange] bigdec)
                 (cu/update-in-if-exists [:land-exchange/price-per-sqm] bigdec)))))
        land-exchanges))

;; TEET-432p2 plan/todo:
;; 1. rename keys, fn names, etc land-exchange -> priced-area in land-commands ns [x]
;;   1.5 ensure that besides creation, update and deletion (if applicable) are covered [x]
;;      - update is same as create, delete doesnt exist
;;   1.75 delete commented out old versions of fns [x]
;; 2. add keys also to acq. negotiations and property-rights  cases in land-commands ns [x] 
;; 3. schema.edn - land-exchange -> priced-area [x]
;; 4. frontend land-controller and land-view - land-exchange -> priced-area and ensure prop rights/nagotiations case
;;    is accounted for [x]
;; 4b work out field title (change from land exchanges) [ ]
;;     fix translations for area/price fields (by renaming tr keys in localization spreadsheet)
;; 5 update land command param specs in land-specs ns [x]
;; 6. manually test [x]
;; 7. check unit tests [x]
;;
;; - land exchange case: area to be taken entered by the user in cadastral view, in estate view enter data the area given back in the return. cadastral view shows sum what owner gets paid. estate vew nr is deduced.
;;   modal (coming later) will show unit valuations vs compensation, needs per unit info associated in land-exchanges


;; -> revised plan:
;; - keep land-exchange in data model as special case, add priced-area just for prop rights / negotiations cases
;; 8. land-commands ns: restore land-exchange versions of functions and keys alongside priced-area  [x]
;; 9. check l-e vs property-rights & neg. cases in land-commands [x]
;;   - updated proc type options: :estate-procedure.type/property-trading -> land-exchanges [x]
;; 10. merge from master [×] 
;; 11. frontend land-controller: separate back out land-exchanges handling (if any) [x]
;; 12. frontend land-view: separate back out land-exchanges handling [ ]
;; 13. frontend land-view: change from per-estate to per-cadastral location in the the form [ ]
;;     - add fn in land-controller that can pick out area/lex info based on cadastral id 
;;     - q: what about interaction with area-to-obtain field? check figma
;;        - wireframe has fields POS n, area-to-obtain, price-per-m2, estate regnr, progress all on same innermost unit level
;;        - the status field is currently also at cadastral level
;;        - in the data model the impact/status/cadunit/pos are their own land-acuqisition entity, but cadastral-id ties them to cadastral units
;;     -> the picking fn needs to get both land-acquisition and priced-area/land-exchange entities based on cad id
;;  14. cadastral subform and estate forms use different versions of form vs form2 .. change inner cadastral form to form2


(defn new-process-fees-tx
  [process-fees]
  (vec
   (keep-indexed
    (fn [i pf]
      (when (seq pf)
        (-> pf
            (cu/update-in-if-exists [:estate-process-fee/fee] bigdec)
            (maybe-add-db-id (str "new-process-fee-" i)))))
    process-fees)))

(def common-procedure-updates
  [[:estate-procedure/pos #(Integer/parseInt %)]
   [:estate-procedure/motivation-bonus bigdec]
   [:estate-procedure/compensations new-compensations-tx]
   [:estate-procedure/third-party-compensations new-compensations-tx]
   [:estate-procedure/priced-areas new-priced-area-txs]
   [:estate-procedure/land-exchanges new-land-exchange-txs]])

(def common-procedure-keys
  [:estate-procedure/pos :estate-procedure/type :estate-procedure/estate-id])

(def procedure-type-options
  {:estate-procedure.type/urgent
   {:keys

    [:estate-procedure/urgent-bonus
     :estate-procedure/motivation-bonus :estate-procedure/third-party-compensations]}

   :estate-procedure.type/acquisition-negotiation
   {:keys [:estate-procedure/pos :estate-procedure/compensations
           :estate-procedure/motivation-bonus
           :estate-procedure/third-party-compensations
           :estate-procedure/process-fees
           :estate-procedure/priced-areas]
    :updates [[:estate-procedure/process-fees new-process-fees-tx]]}

   :estate-procedure.type/expropriation
   {:keys [:estate-procedure/pos :estate-procedure/compensations
           :estate-procedure/motivation-bonus
           :estate-procedure/third-party-compensations]}

   :estate-procedure.type/property-rights
   {:keys [:estate-procedure/pos :estate-procedure/compensations :estate-procedure/priced-areas
           :estate-procedure/motivation-bonus :estate-procedure/third-party-compensations]}

   :estate-procedure.type/property-trading
   {:keys [:estate-procedure/pos :estate-procedure/land-exchanges
           :estate-procedure/third-party-compensations]}})

(defn estate-procedure-tx
  [procedure-form-data]
  (let [payload (su/select-by-spec :land/create-estate-procedure
                                   procedure-form-data
                                   #{:db/id})
        type (:estate-procedure/type payload)
        {:keys [keys updates]} (type procedure-type-options)]
    (merge
      (reduce
       (fn [payload [path update-fn]]
         (cu/update-in-if-exists payload [path] update-fn))
        (select-keys payload (concat common-procedure-keys keys))
        (concat common-procedure-updates updates))
      {:db/id (or (:db/id procedure-form-data) "new-estate")
       :estate-procedure/project [:thk.project/id (:thk.project/id procedure-form-data)]})))


(defcommand :land/create-estate-procedure
  {:doc "Create a new compensation for estate"
   :context {:keys [db user]}
   :payload {:thk.project/keys [id] :as payload}
   :project-id [:thk.project/id id]
   :authorization {:land/edit-land-acquisition {:eid [:thk.project/id id]
                                                :link :thk.project/owner}}
   :pre [(empty? (du/db-ids payload))]
   :transact
   [(let [tx-data (estate-procedure-tx payload)]
      (def p* payload)
      (def tx* tx-data)
      tx-data)]})

(defcommand :land/update-estate-procedure
  {:doc "Update an existing estate procedure"
   :context {:keys [user db]}
   :payload {id :thk.project/id
             procedure-id :db/id
             :as payload}
   :project-id [:thk.project/id id]
   :authorization {:land/edit-land-acquisition {:eid [:thk.project/id id]
                                                :link :thk.project/owner}}
   :pre [(du/same-db-ids?
          (land-db/project-estate-procedure-by-id
           db [:thk.project/id id] procedure-id)
          payload)]
   :transact [(let [tx-data
                    (estate-procedure-tx payload)]
                (def p* payload)
                (def tx* tx-data)
                tx-data)]})
