(ns teet.land.land-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [datomic.client.api :as d]
            [teet.meta.meta-model :as meta-model]
            [teet.util.collection :as cu]
            [teet.util.spec :as su]
            teet.land.land-specs))


(defcommand :land/create-land-acquisition
  {:doc "Save a land purchase decision form."
   :context {conn :conn
             user :user}
   :payload {:land-acquisition/keys [impact pos-number area-to-obtain]
             :keys [cadastral-unit
                    project-id]}
   :project-id [:thk.project/id project-id]
   :authorization {:land/create-land-acquisition {:eid [:thk.project/id project-id]
                                                  :link :thk.project/owner}} ;; TODO needs discussion
   :transact
   [(cu/without-nils
      (merge {:db/id "new land-purchase"
              :land-acquisition/impact impact
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
   :payload {:land-acquisition/keys [impact pos-number area-to-obtain]
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
              :land-acquisition/area-to-obtain area-to-obtain
              :land-acquisition/pos-number pos-number}
             (meta-model/modification-meta user)))]})

(defn new-compensations-tx
  [compensations]
  (into []
        (map-indexed
          (fn [i compensation]
            (-> (select-keys compensation (su/keys-of :estate-procedure/compensation))
                (merge {:db/id (str "new-third-party-comp-" i)})
                (cu/update-in-if-exists [:estate-compensation/amount] bigdec))))
        compensations))

(defn new-land-exchange-txs
  [land-exchanges]
  (into []
        (map-indexed
          (fn [i exchange]
            (-> (select-keys exchange (su/keys-of :estate-procedure/land-exchange))
                (merge {:db/id (str "new-exchange-" i)})
                (cu/update-in-if-exists [:land-exchange/area] bigdec)
                (cu/update-in-if-exists [:land-exchange/price-per-sqm] bigdec))))
        land-exchanges))

(def common-procedure-updates
  [[:estate-procedure/pos #(Integer/parseInt %)]
   [:estate-procedure/compensations new-compensations-tx]
   [:estate-procedure/third-party-compensations new-compensations-tx]
   [:estate-procedure/land-exchanges new-land-exchange-txs]])

(def common-procedure-keys
  [:estate-procedure/pos :estate-procedure/type :estate-procedure/estate-id])

(def procedure-type-options
  {:estate-procedure.type/urgent {:keys
                                  [:estate-procedure/urgent-bonus
                                   :estate-procedure/motivation-bonus :estate-procedure/third-party-compensations]}
   :estate-procedure.type/acquisition-negotiation {:keys [:estate-procedure/pos :estate-procedure/compensations
                                                          :estate-procedure/motivation-bonus
                                                          :estate-procedure/third-party-compensations]}
   :estate-procedure.type/expropriation {:keys [:estate-procedure/pos :estate-procedure/compensations
                                                :estate-procedure/motivation-bonus
                                                :estate-procedure/third-party-compensations]}
   :estate-procedure.type/property-rights {:keys [:estate-procedure/pos :estate-procedure/compensations
                                                  :estate-procedure/motivation-bonus :estate-procedure/third-party-compensations]}
   :estate-procedure.type/property-trading {:keys [:estate-procedure/pos :estate-procedure/land-exchanges
                                                   :estate-procedure/third-party-compensations]}})

(defn estate-procedure-tx
  [procedure-form-data]
  (let [payload (su/select-by-spec :land/create-estate-procedure procedure-form-data)
        type (:estate-procedure/type payload)
        {:keys [keys updates]} (type procedure-type-options)]
    (merge
      (reduce
        (fn [payload [path update-fn]]
          (cu/update-in-if-exists payload [path] update-fn))
        (select-keys payload (concat common-procedure-keys keys))
        (concat common-procedure-updates updates))
      {:db/id "new-estate"
       :estate-procedure/project [:thk.project/id (:thk.project/id procedure-form-data)]})))


(defcommand :land/create-estate-procedure
  {:doc "Create a new compensation for estate"
   :context {conn :conn
             user :user
             db :db}
   :payload {:thk.project/keys [id] :as payload}
   :project-id [:thk.project/id id]
   :authorization {}
   :pre [(nil? (:db/id payload))]
   :transact
   [(estate-procedure-tx payload)]})

