(ns teet.workflow.workflow-commands
  "Commands for workflows, phases and tasks"
  (:require [teet.db-api.core :as db-api]
            [datomic.client.api :as d]))

(defmethod db-api/command! :workflow/create-workflow [conn {:keys [thk-project-id
                                                                   phases]}]
  (d/transact ))
