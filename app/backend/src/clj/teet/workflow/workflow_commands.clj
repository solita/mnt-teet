(ns teet.workflow.workflow-commands
  "Commands for workflows, phases and tasks"
  (:require [teet.db-api.core :as db-api]
            [datomic.client.api :as d]
            [clojure.spec.alpha :as s]
            [teet.workflow.workflow-specs]
            [taoensso.timbre :as log]))


(defmethod db-api/command! :workflow/create-project-workflow [{conn :conn} project-workflow]
  (select-keys (d/transact conn {:tx-data [project-workflow]}) [:tempids]))

(defmethod db-api/command! :workflow/update-task [{conn :conn} task]
  (select-keys (d/transact conn {:tx-data [task]}) [:tempids]))

(defmethod db-api/command! :workflow/add-task-to-phase [{conn :conn} {phase-id :phase-id
                                                                      task :task}]
  (select-keys (d/transact conn {:tx-data [{:db/id phase-id
                                            :phase/tasks [task]}]}) [:tempids])
  )
