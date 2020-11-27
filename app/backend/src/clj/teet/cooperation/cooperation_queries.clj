(ns teet.cooperation.cooperation-queries
  (:require [teet.db-api.core :refer [defquery] :as db-api]
            [teet.cooperation.cooperation-db :as cooperation-db]
            [teet.project.project-db :as project-db]
            [teet.util.datomic :as du]))

(defquery :cooperation/overview
  {:doc "Fetch project overview of cooperation: 3rd parties and their latest applications"
   :context {:keys [db user]}
   :args {project-id :thk.project/id}
   :project-id [:thk.project/id project-id]
   :authorization {:cooperation/view-cooperation-page {}}}
  (let [p [:thk.project/id project-id]]
    {:project (project-db/project-by-id db p)
     :overview (cooperation-db/overview db p)}))

(defquery :cooperation/third-party
  {:doc "Fetches overview plus a given 3rd party and all its applications"
   :context {:keys [db user]}
   :args {project-id :thk.project/id
          name :cooperation.3rd-party/name :as args}
   :project-id [:thk.project/id project-id]
   :authorization {:cooperation/view-cooperation-page {}}}
  (let [p [:thk.project/id project-id]
        tp-id (cooperation-db/third-party-id-by-name db p name)]
    {:project (project-db/project-by-id db p)
     :overview (cooperation-db/overview db p
                                        #(= tp-id %))}))

(defquery :cooperation/application
  {:doc "Fetch overview plus a single 3rd party appliation with all information"
   :context {:keys [db user]}
   :args {project-id :thk.project/id
          name :cooperation.3rd-party/name
          id :db/id}
   :project-id [:thk.project/id project-id]
   :authorization {:cooperation/view-cooperation-page {}}}
  (let [p [:thk.project/id project-id]
        tp-id (cooperation-db/third-party-id-by-name db p name)]
    {:project (project-db/project-by-id db p)
     :overview (cooperation-db/overview db p)
     :third-party (cooperation-db/third-party-with-application db tp-id id)}))
