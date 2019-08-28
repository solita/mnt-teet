(ns teet.project.project-view
  (:require [reagent.core :as r]
            [teet.projects.project-form :as project-form]
            [teet.map.map-features :as map-features]
            [teet.map.map-layers :as map-layers]
            [teet.map.map-view :as map-view]
            [teet.login.login-paths :as login-paths]
            [postgrest-ui.components.item-view :as postgrest-item-view]
            [teet.ui.material-ui :refer [Grid Typography]]))

(defn project-data
  [{:strs [name estimated_duration road_nr km_range carriageway procurement_no]}]
  [:<>
   [Typography
    "Project information"]])

(defn workflow-information
  []
  [Typography "workflow"])

(defn project-page [e! {{:keys [project]} :params :as app}]
  [:<>
   [map-view/map-view e! {:height "400px"
                          ;:zoom-to-geometries #{:thk-project}
                          :layers {:thk-project
                                   (map-layers/geojson-layer (get-in app [:config :api-url])
                                     "geojson_thk_project"
                                     {"id" project}
                                     map-features/project-line-style
                                     {:fit-on-load? true})}}
    app]
   [Grid {:container true
          :style {:margin-top "2rem"}}
    [Grid {:item true :xs 8}
     [workflow-information]]
    [Grid {:item true :xs 4}
     [postgrest-item-view/item-view
      {:endpoint (get-in app [:config :api-url])
       :token (get-in app login-paths/api-token)
       :table "thk_project"
       :select ["name" "estimated_duration"
                "road_nr" "km_range" "carriageway"
                "procurement_no"]
       :view project-data}
      project]]]])
