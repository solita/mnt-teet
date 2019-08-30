(ns teet.workflow.workflow-controller
  (:require [tuck.core :as t]
            [teet.routes :as routes]
            [goog.math.Long]))

(defrecord FetchWorkflow [project-id workflow-id])

(defmethod routes/on-navigate-event :project-workflow [{{:keys [project workflow]} :params}]
  (->FetchWorkflow project workflow))

(extend-protocol t/Event
  FetchWorkflow
  (process-event [{:keys [project-id workflow-id]} app]
    (t/fx app
          {:tuck.effect/type :query
           :query :workflow/fetch-workflow
           :args {:project-id project-id
                  :workflow-id (goog.math.Long/fromString workflow-id)}
           :result-path [:workflow workflow-id]})))
