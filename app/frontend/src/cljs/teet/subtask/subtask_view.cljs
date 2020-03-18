(ns teet.subtask.subtask-view
  (:require [teet.project.project-navigator-view :as project-navigator-view]))

(defn subtask-page [e! app project breadcrumbs]
  [project-navigator-view/project-navigator-with-content
   {:e! e!
    :project project
    :app app
    :breadcrumbs breadcrumbs}
   [:div "subu subu"]])
