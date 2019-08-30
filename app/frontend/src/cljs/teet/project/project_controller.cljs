(ns teet.project.project-controller
  "Controller for project page"
  (:require [teet.routes :as routes]
            [taoensso.timbre :as log]
            [tuck.core :as t]))

(defrecord FetchProjectWorkflows [project-id])
(defrecord StartNewWorkflow [project-id workflow])
(defrecord OpenWorkflow [project-id workflow-id]) ; navigate to workflow page

(defmethod routes/on-navigate-event :project [{{project :project} :params}]
  (log/info "Navigated to project, fetch workflows for THK project: " project)
  (->FetchProjectWorkflows project))

(extend-protocol t/Event
  FetchProjectWorkflows
  (process-event [{project-id :project-id} app]
    (log/info "Fetching workflows for THK project: " project-id)
    (t/fx app
          {:tuck.effect/type :query
           :query :workflow/list-project-workflows
           :args {:thk-project-id project-id}
           :result-path [:project project-id :workflows]}))

  StartNewWorkflow
  (process-event [{:keys [project-id workflow]} app]
    (log/info "Start new workflow: " workflow ", for project id: " project-id)
    (t/fx app
          {:tuck.effect/type :command!
           :command :workflow/create-project-workflow
           :payload {:db/id "workflow"
                     :workflow/name (:name workflow)
                     :workflow/phases [{:db/id "phase"
                                        :phase/name "Design Technical Requirements"
                                        :phase/ordinality 1
                                        :phase/tasks [{:db/id "task1"
                                                       :task/name "Requirements document"
                                                       :task/description "Upload finished DTR document"
                                                       :task/status [:db/ident :task.status/not-started]}]}]
                     :thk/id project-id}
           :result-event (fn [{tempids :tempids :as result}]
                           (log/info "RESULT: " result)
                           (def dbg1 result)
                           (->OpenWorkflow project-id (tempids "workflow")))}))

  OpenWorkflow
  (process-event [{:keys [project-id workflow-id]} app]
    (t/fx app
          {:tuck.effect/type :navigate
           :page :project-workflow
           :params {:project project-id
                    :workflow workflow-id}})))
