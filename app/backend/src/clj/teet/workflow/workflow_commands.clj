(ns teet.workflow.workflow-commands
  "Commands for workflows, phases and tasks"
  (:require [teet.db-api.core :as db-api]
            [datomic.client.api :as d]
            [clojure.spec.alpha :as s]
            [teet.workflow.workflow-specs]))


(defmethod db-api/command! :workflow/create-project-workflow [{conn :conn} project-workflow]
  ;; FIXME: automatically validate spec based on command
  (when-not (s/valid? :workflow/project-workflow project-workflow)
    (throw (ex-info "Project workflow is not valid"
                    {:explain (s/explain-data :workflow/project-workflow project-workflow)})))
  (d/transact conn {:tx-data [project-workflow]}))
