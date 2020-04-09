(ns teet.land.land-queries
  (:require [teet.db-api.core :refer [defquery]]
            [datomic.client.api :as d]))

(defquery :land/fetch-land-acquisitions
  {:doc "Fetch all land acquisitions related to proejct"
   :context {db :db}
   :args {project-id :project-id
          units :units}
   :project-id [:thk.project/id project-id]
   :authorization {}}
  (let [land-acquisitions (d/q '[:find (pull ?e [*])
                                 :in $
                                 :where [?e :land-acquisition/project ?project-id]]
                               db
                               project-id)]
    (mapv
      first
      land-acquisitions)))
