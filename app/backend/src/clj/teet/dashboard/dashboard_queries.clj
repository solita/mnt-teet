(ns teet.dashboard.dashboard-queries
  (:require [teet.db-api.core :refer [defquery]]
            [datomic.client.api :as d]
            [teet.meta.meta-query :as meta-query]))

(defquery :dashboard/user-dashboard
  {:doc "Get a dashboard for the current user"
   :context {:keys [user db]}
   :args _
   :project-id nil
   :authorization {}}
  {:tasks (meta-query/without-deleted
           db
           (mapv first
                 (d/q '[:find (pull ?e [:db/id
                                        :task/name :task/description
                                        :task/type
                                        {:task/status [*]}
                                        {:activity/_tasks
                                         [:activity/name
                                          :activity/estimated-start-date
                                          :activity/estimated-end-date
                                          :activity/actual-start-date
                                          :activity/actual-end-date
                                          {:thk.lifecycle/_activities
                                           [:thk.lifecycle/type
                                            :thk.lifecycle/estimated-start-date
                                            :thk.lifecycle/estimated-end-date
                                            {:thk.project/_lifecycles
                                             [:thk.project/id :thk.project/name]}]}]}])
                        :in $ ?user
                        :where [?e :task/assignee ?user]]
                      db [:user/id (:user/id user)])))})
