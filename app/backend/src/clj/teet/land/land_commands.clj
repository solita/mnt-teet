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


;; todo: rename land-exchanges?
(defn new-land-exchange-txs
  [land-exchanges]
  (into []
        (keep-indexed
         (fn [i exchange]
           (when (seq exchange)
             (-> exchange
                 (select-keys (su/keys-of :estate-procedure/land-exchange))
                 (maybe-add-db-id (str "new-exchange-" i))
                 (cu/update-in-if-exists [:land-exchange/area] bigdec)
                 (cu/update-in-if-exists [:land-exchange/price-per-sqm] bigdec)))))
        land-exchanges))

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
           :estate-procedure/process-fees]
    :updates [[:estate-procedure/process-fees new-process-fees-tx]]}

   :estate-procedure.type/expropriation
   {:keys [:estate-procedure/pos :estate-procedure/compensations
           :estate-procedure/motivation-bonus
           :estate-procedure/third-party-compensations]}

   :estate-procedure.type/property-rights
   {:keys [:estate-procedure/pos :estate-procedure/compensations
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
