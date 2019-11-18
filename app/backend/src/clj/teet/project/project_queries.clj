(ns teet.project.project-queries
  (:require [teet.db-api.core :as db-api]
            [teet.project.project-model :as project-model]
            [datomic.client.api :as d]
            [teet.project.project-model :as project-model]))

(defmethod db-api/query :thk.project/db-id->thk-id [{db :db} {id :db/id}]
  (-> db
      (d/pull [:thk.project/id] id)
      :thk.project/id))

(defmethod db-api/query :thk.project/fetch-project [{db :db} {:thk.project/keys [id]}]
  (d/pull db (into project-model/project-info-columns
                   '[{:thk.project/lifecycles
                      [:thk.lifecycle/estimated-start-date :thk.lifecycle/estimated-end-date :thk.lifecycle/type
                       {:thk.lifecycle/activities [*]}]}])
          [:thk.project/id id]))

(defmethod db-api/query :thk.project/listing [{db :db} _]
  {:query '[:find (pull ?e columns)
            :in $ columns
            :where [?e :thk.project/id _]]
   :args [db project-model/project-listing-columns]
   :result-fn (partial mapv first)})
