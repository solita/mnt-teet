(ns teet.project.project-view
  (:require [clojure.set :as set]
            [herb.core :refer [<class]]
            [reagent.core :as r]
            [teet.map.map-features :as map-features]
            [teet.map.map-layers :as map-layers]
            [teet.map.map-view :as map-view]
            [teet.login.login-paths :as login-paths]
            [postgrest-ui.components.item-view :as postgrest-item-view]
            [teet.ui.material-ui :refer [Grid]]
            [teet.project.project-controller :as project-controller]
            [teet.task.task-controller :as task-controller]
            [teet.theme.theme-spacing :as theme-spacing]
            [teet.ui.select :as select]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.icons :as icons]))

(defn project-data
  [{:strs [name estimated_duration road_nr km_range carriageway procurement_no]}]
  [itemlist/ItemList
   {:title name}
   [:div "Est. duration: " estimated_duration]
   [:div "Road number: " road_nr]
   [:div "Km range: " km_range]
   [:div "Carriageway: " carriageway]
   [:div "Procurement number:" procurement_no]])

(defn project-page [e! {{:keys [project]} :params :as app}]
  [:<>
   [Grid {:container true
          :style {:margin-top "2rem"}
          :spacing 10}
    [Grid {:item true :xs 6}
     [postgrest-item-view/item-view
      {:endpoint (get-in app [:config :api-url])
       :token (get-in app login-paths/api-token)
       :table "thk_project"
       :select ["name" "estimated_duration"
                "road_nr" "km_range" "carriageway"
                "procurement_no"]
       :view project-data}
      project]
     [itemlist/ProgressList
      {:title "Workflows"}
      (for [wf (get-in app [:project project :workflows])]
        {:name (:workflow/name wf)
         :id (str (:db/id wf))
         :link (str "#/projects/" project "/workflows/" (:db/id wf))})]
     [itemlist/ProgressList
      {:title "Workflows"}
      (for [wf (get-in app [:project project :workflows])]
        {:name (:workflow/name wf)
         :id (str (:db/id wf))
         :link (str "#/projects/" project "/workflows/" (:db/id wf))})]
     [itemlist/ProgressList
      {:title "Workflows"}
      (for [wf (get-in app [:project project :workflows])]
        {:name (:workflow/name wf)
         :id (str (:db/id wf))
         :link (str "#/projects/" project "/workflows/" (:db/id wf))})]
     [itemlist/ProgressList
      {:title "Workflows"}
      (for [wf (get-in app [:project project :workflows])]
        {:name (:workflow/name wf)
         :id (str (:db/id wf))
         :link (str "#/projects/" project "/workflows/" (:db/id wf))})]
     [itemlist/ProgressList
      {:title "Workflows"}
      (for [wf (get-in app [:project project :workflows])]
        {:name (:workflow/name wf)
         :id (str (:db/id wf))
         :link (str "#/projects/" project "/workflows/" (:db/id wf))})]
     [select/select-with-action {:placeholder "New workflow"
                                 :item-label :name
                                 :items [{:name "Pre-design"}
                                         {:name "Foo bar"}]
                                 :on-select #(e! (project-controller/->StartNewWorkflow project %))}]]
    [Grid {:item true :xs 6}
     [map-view/map-view e! {:class (<class theme-spacing/fill-content)
                            :layers {:thk-project
                                     (map-layers/geojson-layer (get-in app [:config :api-url])
                                                               "geojson_thk_project"
                                                               {"id" project}
                                                               map-features/project-line-style
                                                               {:fit-on-load? true})}}
      app]]]])
