(ns teet.file.file-view
  (:require [teet.project.project-navigator-view :as project-navigator-view]
            [teet.project.project-model :as project-model]))

(defn file-page [e! {{file-id :file} :params :as app} project breadcrumbs]
  (let [file (project-model/file-by-id project file-id)]
    [project-navigator-view/project-navigator-with-content
     {:e! e!
      :app app
      :project project
      :breadcrumbs breadcrumbs}
     [:div "filen sivu " (pr-str file)]]))
