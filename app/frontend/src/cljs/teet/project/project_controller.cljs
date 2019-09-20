(ns teet.project.project-controller
  "Controller for project page"
  (:require [teet.routes :as routes]
            [taoensso.timbre :as log]
            [tuck.core :as t]))

(defrecord FetchProjectPhases [project-id])
(defrecord FetchProjectDocuments [project-id])
(defrecord OpenPhaseDialog []) ; open add phase modal dialog
(defrecord ClosePhaseDialog [])
(defrecord OpenTaskDialog [phase-id])
(defrecord CloseTaskDialog [])

(defmethod routes/on-navigate-event :project [{{project :project} :params}]
  (log/info "Navigated to project, fetch workflows for THK project: " project)
  [(->FetchProjectPhases project)
   (->FetchProjectDocuments project)])

(extend-protocol t/Event
  FetchProjectPhases
  (process-event [{project-id :project-id} app]
    (log/info "Fetching phases for THK project: " project-id)
    (t/fx app
          {:tuck.effect/type :query
           :query :workflow/list-project-phases
           :args {:thk-project-id project-id}
           :result-path [:project project-id :phases]}))

  FetchProjectDocuments
  (process-event [{project-id :project-id} app]
    (log/info "Fetching documents for THK project: " project-id)
    (t/fx app
          {:tuck.effect/type :query
           :query :document/list-project-documents
           :args {:thk-project-id project-id}
           :result-path [:project project-id :documents]}))

  OpenPhaseDialog
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :navigate
           :page :project
           :params {:project (get-in app [:params :project])}
           :query {:add-phase 1}}))

  ClosePhaseDialog
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :navigate
           :page :project
           :params {:project (get-in app [:params :project])}
           :query {}}))

  OpenTaskDialog
  (process-event [{phase-id :phase-id} app]
    (t/fx app
          {:tuck.effect/type :navigate
           :page :project
           :params {:project (get-in app [:params :project])}
           :query {:add-task phase-id}}))

  CloseTaskDialog
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :navigate
           :page :project
           :params {:project (get-in app [:params :project])}
           :query {}})))
