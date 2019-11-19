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
  (d/pull db (into project-model/project-info-attributes
                   '[{:thk.project/lifecycles
                      [:db/id :thk.lifecycle/estimated-start-date :thk.lifecycle/estimated-end-date :thk.lifecycle/type
                       {:thk.lifecycle/activities [*]}]}])
          [:thk.project/id id]))

(defmethod db-api/query :thk.project/fetch-project-lifecycle
  [{db :db} {:keys [project lifecycle]}]
  (let [lifecycle
        (ffirst
         (d/q '[:find (pull ?e [*
                                {:thk.lifecycle/activities [:db/id
                                                            :activity/name
                                                            :activity/estimated-start-date
                                                            :activity/estimated-end-date]}
                                {:thk.project/_lifecycles [:db/id]}])
                :where [?project :thk.project/lifecycles ?e]
                :in $ ?project ?e]
              db
              [:thk.project/id project]
              lifecycle))]
    (update-in lifecycle [:thk.project/_lifecycles 0]
               (fn [{id :db/id}]
                 (d/pull db project-model/project-info-attributes id)))))

(defmethod db-api/query :thk.project/listing [{db :db} _]
  {:query '[:find (pull ?e columns)
            :in $ columns
            :where [?e :thk.project/id _]]
   :args [db project-model/project-listing-attributes]
   :result-fn (partial mapv first)})
