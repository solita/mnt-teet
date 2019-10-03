(ns teet.project.project-view
  (:require [clojure.set :as set]
            [herb.core :refer [<class]]
            [reagent.core :as r]
            [teet.map.map-features :as map-features]
            [teet.map.map-layers :as map-layers]
            [teet.map.map-view :as map-view]
            [teet.login.login-paths :as login-paths]
            [postgrest-ui.components.item-view :as postgrest-item-view]
            [teet.ui.material-ui :refer [Grid Button TextField Divider Tabs Tab]]
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
            [teet.task.task-view :as task-view]
            [teet.common.common-controller :as common-controller]
            [postgrest-ui.components.listing :as postgrest-listing]
            [clojure.string :as str]))

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

(defn project-map [e! endpoint project]
  [:div {:class (<class project-style/project-map-style)}
   [map-view/map-view e! {:class (<class theme-spacing/fill-content)
                          :layers {:thk-project
                                   (map-layers/geojson-layer endpoint
                                                             "geojson_thk_project"
                                                             {"id" project}
                                                             map-features/project-line-style
                                                             {:fit-on-load? true})
                                   :related-restrictions
                                   (map-layers/geojson-layer endpoint
                                                             "geojson_thk_project_related_restrictions"
                                                             {"project_id" project
                                                              "distance" 200}
                                                             map-features/project-related-restriction-style
                                                             {:opacity 0.5})}}
    {}]])

(defn project-related-restrictions [{:keys [endpoint token e! project]}]
  [postgrest-listing/listing
   {:endpoint endpoint
    :token token
    :batch-size 100 ; increased batch size as query is a little slow
    :table "related_restrictions_by_project"
    :select ["id" "type" "voond" "voondi_nimi" "toiming" "muudetud" "seadus"]
    :columns ["id" "voond" "voondi_nimi"]
    :drawer (fn [{:strs [voond voondi_nimi] :as item}]
              [itemlist/ItemList {:title (str voond (when voondi_nimi (str ": " voondi_nimi)))}
               (for [[key val] item
                     :when val]
                 ^{:key key}
                 [itemlist/Item {:label key} val])])
    :where {"project_id" [:= project]}}])

(defn project-page [e! {{:keys [project]} :params
                        {:keys [tab]} :query
                        {:keys [add-phase add-task]} :query :as app}]
  (let [tab (or tab "documents")]
    [:<>
     (when add-phase
       [panels/modal {:title (tr [:project :add-phase])
                      :on-close #(e! (project-controller/->ClosePhaseDialog))}
        [phase-view/phase-form e! project-controller/->ClosePhaseDialog (get-in app [:project project :new-phase])]])
     (when add-task
       [panels/modal {:title (tr [:project :add-task])
                      :on-close #(e! (project-controller/->CloseTaskDialog))}
        [task-view/task-form e! project-controller/->CloseTaskDialog add-task (get-in app [:project project :new-task])]])
     [:div {:class (<class project-style/project-view-container)}
      [Grid {:container true}
       [Grid {:item true :xs 6}
        [:div {:class (<class project-style/project-tasks-style)}
         [project-info (get-in app [:config :api-url]) (get-in app login-paths/api-token) project]

         [Tabs {:value (case tab
                         "documents" 0
                         "restrictions" 1)
                :indicatorColor "primary"
                :textColor "primary"
                :on-change (fn [_ v]
                             (log/info "GO: " v)
                             (e! (common-controller/map->Navigate {:page :project
                                                                   :params {:project project}
                                                                   :query {:tab (case v
                                                                                  0 "documents"
                                                                                  1 "restrictions")}})))}
          (Tab {:key "documents"
                :label (tr [:project :documents-tab])})
          (Tab {:key "restrictions"
                :label (tr [:project :restrictions-tab])})]

         (case tab
           "documents"
           [project-phase-listing e! project (get-in app [:project project :phases])]

           "restrictions"
           [project-related-restrictions {:endpoint (get-in app [:config :api-url])
                                          :token (get-in app login-paths/api-token)
                                          :e! e!
                                          :project project}])]]
       [Grid {:item true :xs 6}
        [project-map e! (get-in app [:config :api-url]) project]]]]]))

(defn project-page-and-title [e! app]
  {:title "TEET"
   :page [project-page e! app]})
