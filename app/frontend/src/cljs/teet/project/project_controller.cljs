(ns teet.project.project-controller
  "Controller for project page"
  (:require [teet.routes :as routes]
            [taoensso.timbre :as log]
            [tuck.core :as t]))

(defrecord FetchProjectWorkflows [project-id])

(defmethod routes/on-navigate-event :project [{{project :project} :params}]
  (log/info "Navigated to project, fetch workflows for THK project: " project)
  (->FetchProjectWorkflows project))

(extend-protocol t/Event
  FetchProjectWorkflows
  (process-event [{project-id :project-id} app]
    (log/info "Fetching workflows for THK project: " project-id)
    app))
