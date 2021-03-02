(ns teet.integration.vektorio.vektorio-queries
  (:require [teet.db-api.core :refer [defquery audit]]
            [teet.integration.vektorio.vektorio-core :as vektorio-core]
            [teet.environment :as environment]))

(defquery :vektorio/instant-login
  {:doc "Get instant login hash for the default user id to access BIM viewer"
   :context {db :db}
   :args {project-id :project-id}
   :project-id [:thk.project/id project-id]
   :authorization {}}
  (let [response (vektorio-core/instant-login (environment/config-value :vektorio))]
    response))
