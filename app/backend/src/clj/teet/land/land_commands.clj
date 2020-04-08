(ns teet.land.land-commands
  (:require [teet.db-api.core :as db-api :refer [defcommand]]
            [datomic.client.api :as d]
            [teet.meta.meta-model :refer [modification-meta creation-meta] :as meta-model]
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
              :land-acquisition/area-to-obtain (Long/parseLong area-to-obtain)
              :land-acquisition/pos-number (Long/parseLong pos-number)}
             (meta-model/creation-meta user)))]})


#_(defn land-acquisition-belongs-to-project?
  [db project land-acquisition-id]
  ;; TODO make land-db ns for this
  (boolean
    (ffirst
      (d/q '[:find ?f
             :where [?c :comment/files ?f]
             :in $ ?f ?c]
           db file-id comment-id))))

(defcommand :land/update-land-acquisition
  {:doc "Save a land purchase decision form."
   :context {conn :conn
             user :user
             db :db}
   :payload {:land-acquisition/keys [impact pos-number area-to-obtain]
             :keys [id                                      ;; TODO existing db id of land purchase
                    cadastral-unit
                    project-id]}
   :project-id [:thk.project/id project-id]
   ;:pre [(land-acquisition-belongs-to-project? db [:thk.project/id project-id] id)]
   :authorization {:land/create-land-acquisition {:eid [:thk.project/id project-id]
                                                  :link :thk.project/owner}}
   :transact
   [(cu/without-nils
      (merge {:db/id id
              :land-acquisition/impact impact
              ;:land-acquisition/project [:thk.project/id project-id]
              ;:land-acquisition/cadastral-unit cadastral-unit
              :land-acquisition/area-to-obtain area-to-obtain
              :land-acquisition/pos-number pos-number}
             (meta-model/modification-meta user)))]})

