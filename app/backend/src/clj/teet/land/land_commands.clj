(ns teet.land.land-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [datomic.client.api :as d]
            [teet.meta.meta-model :as meta-model]
            [teet.util.collection :as cu]))


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
              :land-acquisition/area-to-obtain (when area-to-obtain
                                                 (Long/parseLong area-to-obtain))
              :land-acquisition/pos-number (when pos-number
                                             (Long/parseLong pos-number))}
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
              :land-acquisition/area-to-obtain (when area-to-obtain
                                                 (Long/parseLong area-to-obtain))
              :land-acquisition/pos-number (when pos-number
                                             pos-number)}
             (meta-model/modification-meta user)))]})

