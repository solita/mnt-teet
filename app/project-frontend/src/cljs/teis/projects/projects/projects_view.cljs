(ns teis.projects.projects.projects-view
  "Projects view"
  (:require [postgrest-ui.components.listing :as postgrest-listing]
            [teis.projects.projects.projects-controller :as projects-controller]))

(defn projects-listing [e! app]
  [postgrest-listing/listing
   {:endpoint (get-in app [:config :project-registry-url])
    :state (get-in app [:projects :listing])
    :set-state! #(e! (projects-controller/->SetListingState %))
    :table "project"
    :select ["id" "name" "duration" "description"]}])
