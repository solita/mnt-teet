(ns teet.cooperation.cooperation-queries
  (:require [teet.db-api.core :refer [defquery] :as db-api]
            [datomic.client.api :as d]
            [teet.cooperation.cooperation-db :as cooperation-db]))

(defquery :cooperation/overview
  {:doc "Fetch project overview of cooperation: 3rd parties and their latest applications"
   :context {:keys [db user]}
   :args {project-id :thk.project/id}
   :project-id [:thk.project/id project-id]
   :authorization {:cooperation/view-cooperation-page {}}}
  (cooperation-db/overview db [:thk.project/id project-id]))
