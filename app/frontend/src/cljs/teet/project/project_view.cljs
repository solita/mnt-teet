(ns teet.project.project-view
  (:require [clojure.set :as set]
            [herb.core :refer [<class]]
            [reagent.core :as r]
            [teet.map.map-features :as map-features]
            [teet.map.map-layers :as map-layers]
            [teet.map.map-view :as map-view]
            [teet.login.login-paths :as login-paths]
            [postgrest-ui.components.item-view :as postgrest-item-view]
            [teet.ui.material-ui :refer [Grid Button TextField Divider]]
            [teet.project.project-controller :as project-controller]
            [teet.project.project-style :as project-style]
            [teet.task.task-controller :as task-controller]
            [teet.theme.theme-spacing :as theme-spacing]
            [teet.ui.select :as select]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.icons :as icons]
            [teet.localization :refer [tr tr-fixme]]
            [teet.ui.panels :as panels]
            [teet.ui.date-picker :as date-picker]
            [cljs-time.core :as t]
            [cljs-time.format :as tf]
            [taoensso.timbre :as log]
            [teet.phase.phase-view :as phase-view]
            [teet.task.task-view :as task-view]))

(defn project-data
  [{:strs [name estimated_duration road_nr km_range carriageway procurement_no]}]
  [itemlist/ItemList
   {:title name}
   [:div "Est. duration: " estimated_duration]
   [:div "Road number: " road_nr]
   [:div "Km range: " km_range]
   [:div "Carriageway: " carriageway]
   [:div "Procurement number:" procurement_no]])


(defn- project-info [endpoint token project]
  [postgrest-item-view/item-view
   {:endpoint endpoint
    :token token
    :table "thk_project"
    :select ["name" "estimated_duration"
             "road_nr" "km_range" "carriageway"
             "procurement_no"]
    :view project-data}
   project])

(defn project-phase-listing [e! project phases]
  [:<>
      [itemlist/ListHeading {:title (tr [:project :phases])
                             :action [Button {:on-click (e! project-controller/->OpenPhaseDialog)}
                                      (tr [:project :add-phase])
                                      [icons/content-add-circle]]}]
      (doall
       (for [{id :db/id
              :phase/keys [phase-name tasks
                           estimated-start-date estimated-end-date] :as p}
             phases]
         ^{:key id}
         [itemlist/ItemList {:class (<class project-style/phase-list-style)
                             :title (tr [:enum (:db/ident phase-name)])
                             :subtitle (str (.toLocaleDateString estimated-start-date) " - "
                                            (.toLocaleDateString estimated-end-date))
                             :variant :secondary}
          (if (seq tasks)
            (for [t tasks]
              ^{:key (:db/id t)}
              [:div
               [Button {:element "a"
                        :href (str "#/projects/" project "/" id "/" (:db/id t))}
                [icons/file-folder]
                (tr [:enum (:db/ident (:task/type t))])]])
            [:div [:em (tr [:project :phase :no-tasks])]])

          [:div
           [Button {:on-click (r/partial e! (project-controller/->OpenTaskDialog id))
                    :size "small"}
            [icons/content-add-circle]
            (tr [:project :add-task])]]]))])

(defn project-page [e! {{:keys [project]} :params
                        {:keys [add-phase add-task]} :query :as app}]
  [:<>
   (when add-phase
     [panels/modal {:title (tr [:project :add-phase])
                    :on-close #(e! (project-controller/->ClosePhaseDialog))}
      [phase-view/phase-form e! project-controller/->ClosePhaseDialog (get-in app [:project project :new-phase])]])
   (when add-task
     [panels/modal {:title (tr [:project :add-task])
                    :on-close #(e! (project-controller/->CloseTaskDialog))}
      [task-view/task-form e! project-controller/->CloseTaskDialog add-task (get-in app [:project project :new-task])]])
   [:div {:class (<class project-style/project-view-container) }
    [:div {:class (<class project-style/project-tasks-style)}
     [project-info (get-in app [:config :api-url]) (get-in app login-paths/api-token) project]
     [project-phase-listing e! project (get-in app [:project project :phases])]]

    [:div {:class (<class project-style/project-map-style)}
     [map-view/map-view e! {:class (<class theme-spacing/fill-content)
                            :layers {:thk-project
                                     (map-layers/geojson-layer (get-in app [:config :api-url])
                                                               "geojson_thk_project"
                                                               {"id" project}
                                                               map-features/project-line-style
                                                               {:fit-on-load? true})
                                     :related-restrictions
                                     (map-layers/geojson-layer (get-in app [:config :api-url])
                                                               "geojson_thk_project_related_restrictions"
                                                               {"project_id" project
                                                                "distance" 200}
                                                               map-features/project-related-restriction-style
                                                               {:opacity 0.5})}}
      {}]]]])

(defn project-page-and-title [e! app]
  (let [project (get-in app [:params :project])
        current-tab (get-in app [:query :tab] "documents")
        tab (fn [t title]
              {:page :project :params {:project project} :query {:tab t}
               :title title :key title
               :selected? (= current-tab t)})]
    {:title ""
     :tabs [(tab "documents" (tr [:project :documents-tab]))
            (tab "restrictions" (tr [:project :restrictions-tab]))]
     :page (case current-tab
             "documents" [project-page e! app]
             "restrictions" [:div "hep hep"])}))
