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
   [Grid {:container true
          :className (<class project-style/project-grid-container)
          :spacing 10}
    [Grid {:item true
           :className (<class project-style/project-data-column)
           :xs 6}
     [postgrest-item-view/item-view
      {:endpoint (get-in app [:config :api-url])
       :token (get-in app login-paths/api-token)
       :table "thk_project"
       :select ["name" "estimated_duration"
                "road_nr" "km_range" "carriageway"
                "procurement_no"]
       :view project-data}
      project]
     [:<>
      [itemlist/ListHeading {:title (tr [:project :phases])
                             :action [Button {:on-click (r/partial e! (project-controller/->OpenPhaseDialog))}
                                      (tr [:project :add-phase])
                                      [icons/content-add-circle]]}]
      (doall
       (for [{id :db/id
              :phase/keys [phase-name tasks
                           estimated-start-date estimated-end-date] :as p}
             (get-in app [:project project :phases])]
         ^{:key id}
         [itemlist/ItemList {:title (tr [:enum (:db/ident phase-name)])
                             :subtitle (str (.toLocaleDateString estimated-start-date) " - "
                                            (.toLocaleDateString estimated-end-date))
                             :variant :secondary}
          ;;[:div (pr-str p)]
          (if (seq tasks)
            (for [t tasks]
              [Button {:element "a"
                       :href (str "#/projects/" project "/" id "/" (:db/id t))}
               [icons/file-folder]
               (tr [:enum (:db/ident (:task/type t))])])
            [:div [:em (tr [:project :phase :no-tasks])]])

          [:div
           [Button {:on-click (r/partial e! (project-controller/->OpenTaskDialog id))
                    :size "small"}
            [icons/content-add-circle]
            (tr [:project :add-task])]]]))]]

    [Grid {:item true :xs 6
           :className (<class project-style/project-map-column)}
     [map-view/map-view e! {:class (<class theme-spacing/fill-content)
                            :layers {:thk-project
                                     (map-layers/geojson-layer (get-in app [:config :api-url])
                                                               "geojson_thk_project"
                                                               {"id" project}
                                                               map-features/project-line-style
                                                               {:fit-on-load? true})}}
      {}]]]])
