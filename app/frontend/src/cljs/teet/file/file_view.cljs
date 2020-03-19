(ns teet.file.file-view
  (:require [teet.project.project-navigator-view :as project-navigator-view]))

(defn file-page [e! app {file :file :as project} breadcrumbs]
  [project-navigator-view/project-navigator-with-content
   {:e! e!
    :app app
    :project project
    :breadcrumbs breadcrumbs}
   [:div "filen sivu " (pr-str file)]])
