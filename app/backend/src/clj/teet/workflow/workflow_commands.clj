(ns teet.workflow.workflow-commands
  "Commands for workflows, phases and tasks"
  (:require [teet.db-api.core :as db-api]
            [datomic.client.api :as d]
            [clojure.spec.alpha :as s]
            [teet.workflow.workflow-specs]))


(defmethod db-api/command! :workflow/create-project-workflow [{conn :conn} project-workflow]
  (select-keys (d/transact conn {:tx-data [project-workflow]}) [:tempids]))
