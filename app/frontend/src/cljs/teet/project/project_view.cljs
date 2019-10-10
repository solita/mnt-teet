(ns teet.project.project-view
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.map.map-features :as map-features]
            [teet.map.map-layers :as map-layers]
            [teet.map.map-view :as map-view]
            [teet.login.login-paths :as login-paths]
            [postgrest-ui.components.item-view :as postgrest-item-view]
            [teet.ui.material-ui :refer [Grid Button Tabs Tab ButtonBase Collapse]]
            [teet.project.project-controller :as project-controller]
            [teet.project.project-style :as project-style]
            [teet.theme.theme-spacing :as theme-spacing]
            [teet.ui.format :as format]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.icons :as icons]
            [teet.ui.typography :refer [Heading1 Heading2 Heading3]]
            [teet.ui.layout :as layout]
            [teet.theme.theme-colors :as theme-colors]
            [teet.localization :refer [tr]]
            [teet.ui.panels :as panels]
            [taoensso.timbre :as log]
            [teet.phase.phase-view :as phase-view]
            [teet.task.task-view :as task-view]
            [teet.common.common-controller :as common-controller]
            [postgrest-ui.components.query :as postgrest-query]
            [teet.project.project-info :as project-info]))

(defn project-data
  [{:strs [name estimated_duration road_nr km_range carriageway procurement_no]}]
  [:<>
   [Heading1 name]
   [itemlist/ItemList
    {:class (<class project-style/project-data-style)}
    [:div (tr [:project :information :estimated-duration])
     ": "
     (format/date-range estimated_duration)]
    [:div (tr [:project :information :road-number]) ": " road_nr]
    [:div (tr [:project :information :km-range]) ": " (format/km-range km_range)]
    [:div (tr [:project :information :procurement-number]) ": " procurement_no]
    [:div (tr [:project :information :carriageway]) ": " carriageway]]])


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
                        _estimated-start-date _estimated-end-date]}
          phases]
      ^{:key id}
      [itemlist/ItemList {:class (<class project-style/phase-list-style)
                          :title (tr [:enum (:db/ident phase-name)])
                          ;; :subtitle (str (.toLocaleDateString estimated-start-date) " - "
                          ;;                (.toLocaleDateString estimated-end-date))
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

(defn restriction-component
  [restriction]
  (r/with-let [open? (r/atom false)]
              [:div {:class (<class project-style/restriction-container)}
               [ButtonBase {:focus-ripple true
                            :class (<class project-style/restriction-button-style)
                            :on-click #(swap! open? not)}
                (if @open?
                  [icons/navigation-arrow-right]
                  [icons/navigation-arrow-drop-down])
                [Heading3 (get restriction "voond")]]
               [Collapse {:in @open?}
                [itemlist/ItemList {:class (<class project-style/restriction-list-style)}
                 [itemlist/Item {:label "Toiming"} (get restriction "toiming")]
                 [itemlist/Item {:label "Muudetud"} (get restriction "muudetud")]
                 [itemlist/Item {:label "Seadus"} (get restriction "seadus")]]]]))

(defn restrictions-listing
  [data]
  (let [formatted-data (group-by
                         (fn [restriction]
                           (get restriction "type"))
                         data)]
    [:div
     (doall
       (for [group formatted-data]
         ^{:key (first group)}
         [:div
          [Heading2 {:class (<class project-style/restriction-category-style)} (first group)]
          (doall
            (for [restriction (second group)]
              ^{:key (get restriction "id")}
              [restriction-component restriction]))]))]))

(defn project-related-restrictions [{:keys [endpoint token state project e!]}]
  [postgrest-query/query
   {:endpoint endpoint
    :token token
    :table "related_restrictions_by_project"
    :select ["id" "type" "voond" "voondi_nimi" "toiming" "muudetud" "seadus"]
    :columns ["id" "voond" "voondi_nimi"]
    :where {"project_id" [:= project]}}
   restrictions-listing])

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
       [Grid {:item true
              :xs 6}
        [:div {:class (herb/join (<class project-style/project-info-style)
                                 (<class project-style/project-tasks-style))}
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
                :label (tr [:project :restrictions-tab])})]]
        [layout/section
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
   :page [project-page e! app]
   :breadcrumbs [{:page :projects-list
                  :title (tr [:projects :title])}
                 {:title [project-info/project-name app (get-in app [:params :project])]}]})
