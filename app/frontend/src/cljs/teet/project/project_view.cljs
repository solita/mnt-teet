(ns teet.project.project-view
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.map.map-features :as map-features]
            [teet.map.map-layers :as map-layers]
            [teet.map.map-view :as map-view]
            [teet.login.login-paths :as login-paths]
            [postgrest-ui.components.item-view :as postgrest-item-view]
            [teet.ui.material-ui :refer [Grid ButtonBase Collapse Link Divider]]
            [teet.ui.text-field :refer [TextField]]
            [teet.project.project-controller :as project-controller]
            [teet.project.project-style :as project-style]
            [teet.theme.theme-spacing :as theme-spacing]
            [teet.ui.breadcrumbs :as breadcrumbs]
            [teet.ui.skeleton :as skeleton]
            [teet.ui.format :as format]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.icons :as icons]
            [teet.ui.common :as common]
            [teet.ui.typography :refer [Heading1 Heading2 Heading3]]
            [teet.ui.layout :as layout]
            [teet.localization :refer [tr tr-tree]]
            [teet.ui.panels :as panels]
            [teet.ui.buttons :as buttons]
            teet.task.task-spec
            [teet.activity.activity-view :as activity-view]
            [teet.ui.util :as util]
            [clojure.string :as str]
            [teet.ui.query :as query]
            [teet.ui.tabs :as tabs]
            [teet.common.common-styles :as common-styles]
            [teet.ui.form :as form]
            [teet.task.task-controller :as task-controller]
            [teet.ui.select :as select]
            [teet.ui.timeline :as timeline]
            [teet.ui.progress :as progress]
            [teet.util.collection :refer [count-by]]
            teet.project.project-info))

(defn task-form [e! close _activity-id task]
  ;;Task definition (under project activity)
  ;; Task type (a predefined list of tasks: topogeodeesia, geoloogia, liiklusuuring, KMH eelhinnang, loomastikuuuring, arheoloogiline uuring, muu)
  ;; Description (short description of the task for clarification, 255char, in case more detailed description is needed, it will be uploaded as a file under the task)
  ;; Responsible person (email)
  [form/form {:e! e!
              :value task
              :on-change-event task-controller/->UpdateTaskForm
              :cancel-event close
              :save-event task-controller/->CreateTask
              :spec :task/new-task-form}
   ^{:xs 12 :attribute :task/type}
   [select/select-enum {:e! e! :attribute :task/type}]

   ^{:attribute :task/description}
   [TextField {:full-width true :multiline true :rows 4 :maxrows 4
               :variant :outlined}]

   ^{:attribute :task/assignee}
   [select/select-user {:e! e!}]])


(defn- activity-info-popup [label start-date end-date num-tasks complete-count incomplete-count]
  [:div
   [:div [:b label]]
   [Divider]
   [:div (format/date start-date) " - " (format/date end-date)]
   (when (pos? num-tasks)
     [:div {:style {:display "flex" :align-items "center"}}
      [progress/circle {:radius 20 :stroke 5}
       {:total num-tasks
        :success complete-count
        :fail incomplete-count}]
      (str complete-count " / " num-tasks " tasks complete")])])

(defn project-data
  [activities {:thk.project/keys [name estimated-start-date estimated-end-date road-nr start-m end-m
                                  carriageway procurement-nr]}]
  [:div
   [Heading1 name]
   [itemlist/ItemList
    {}
    [:div (tr [:project :information :estimated-duration])
     ": "
     (format/date estimated-start-date) " \u2013 " (format/date estimated-end-date)]
    [:div (tr [:project :information :road-number]) ": " road-nr]
    (when (and start-m end-m)
      [:div (tr [:project :information :km-range]) ": "
       (.toFixed (/ start-m 1000) 3) " \u2013 "
       (.toFixed (/ start-m 1000) 3)])
    [:div (tr [:project :information :procurement-number]) ": " procurement-nr]
    [:div (tr [:project :information :carriageway]) ": " carriageway]]

   (when (and estimated-start-date estimated-end-date
              (seq activities))
     (let [tr* (tr-tree [:enum])]
       [:<>
        [:br]
        [timeline/timeline {:start-date estimated-start-date
                            :end-date  estimated-start-date}
         (for [{name :activity/name
                start-date :activity/estimated-start-date
                end-date :activity/estimated-end-date
                tasks :activity/tasks} (sort-by :activity/estimated-start-date activities)
               :let [tasks-by-completion (count-by task-controller/completed? tasks)
                     num-tasks (count tasks)
                     complete-pct (when (seq tasks)
                                    (/ (tasks-by-completion true 0) num-tasks))
                     incomplete-pct (if complete-pct
                                      (/ (tasks-by-completion false 0) num-tasks)
                                      1)]]
           {:label (-> name :db/ident tr*)
            :start-date start-date
            :end-date end-date
            :fill (if complete-pct
                    [[complete-pct "green"]
                     [incomplete-pct "url(#incomplete)"]]
                    "url(#incomplete)")
            :hover [activity-info-popup
                    (-> name :db/ident tr*)
                    start-date end-date
                    num-tasks
                    (tasks-by-completion true 0)
                    (tasks-by-completion false 0)]})]]))])


(defn- project-info [project breadcrumbs activities]
  [:div
   [breadcrumbs/breadcrumbs breadcrumbs]
   [project-data activities project]])

(defn activity-action-heading
  [{:keys [heading button]}]
  [:div {:class (<class project-style/activity-action-heading)}
   [Heading2 heading]
   button])

;; TODO: Added for pilot demo. Maybe later store in database, make customizable?
(def activity-sort-priority-vec
  [:activity.name/pre-design
   :activity.name/preliminary-design
   :activity.name/land-acquisition
   :activity.name/detailed-design
   :activity.name/construction
   :activity.name/other])

(defn- activity-sort-priority [activity]
  (.indexOf activity-sort-priority-vec
            (-> activity :activity/name :db/ident)))

(defn heading-state
  [title select]
  [:div {:class (<class project-style/heading-state-style)}
   [Heading3 title]
   select])

(defn project-activity
  [e! {:keys [project]} {id :db/id
                         :activity/keys [activity-name tasks status] :as activity}]
  [:div {:class (<class project-style/project-activity-style)}
   [heading-state
    (tr [:enum (:db/ident activity-name)])
    [select/select-enum {:e! e!
                         :tiny-select? true
                         :show-label? false
                         :on-change #(e! (project-controller/->UpdateActivityState id %))
                         :value (:db/ident status)
                         :attribute :activity/status}]]
   (if (seq tasks)
     (doall
       (for [{:task/keys [status type] :as t} tasks]
         ^{:key (:db/id t)}
         [common/list-button-link (merge {:link (str "#/projects/" project "/" id "/" (:db/id t))
                                          :label (tr [:enum (:db/ident type)])
                                          :icon icons/file-folder-open}
                                    (when status
                                      {:end-text (tr [:enum (:db/ident status)])}))]))
     [:div {:class (<class project-style/top-margin)}
      [:em
       (tr [:project :activity :no-tasks])]])
   [Link {:class (<class project-style/link-button-style)
          :on-click (r/partial e! (project-controller/->OpenTaskDialog id))
          :component :button}
    "+ "
    (tr [:project :add-task])]])

(defn project-activity-listing [e! project activities]
  [:<>
   [activity-action-heading {:heading (tr [:project :activities])
                          :button [buttons/button-primary
                                   {:on-click (e! project-controller/->OpenActivityDialog)
                                    :start-icon (r/as-element [icons/content-add])}
                                   (tr [:project :add-activity])]}]
   (doall
     (for [activity
           (sort-by activity-sort-priority
             activities)]
       ^{:key (:db/id activity)}
       [project-activity e! {:project project} activity]))])

(defn project-map [e! endpoint project tab]
  [:div {:class (<class project-style/project-map-style)}
   [map-view/map-view e!
    {:class (<class theme-spacing/fill-content)
     :layers (merge {:thk-project
                     (map-layers/geojson-layer endpoint
                       "geojson_entities"
                       {"ids" (str "{" (:db/id project) "}")}
                       map-features/project-line-style
                       {:fit-on-load? true})}
               (when (= tab "restrictions")
                 {:related-restrictions
                  (map-layers/geojson-layer endpoint
                    "geojson_thk_project_related_restrictions"
                    {"entity_id" (:db/id project)
                     "distance" 200}
                    map-features/project-related-restriction-style
                    {:opacity 0.5})})
               (when (= tab "cadastral-units")
                 {:related-cadastral-units
                  (map-layers/geojson-layer endpoint
                    "geojson_thk_project_related_cadastral_units"
                    {"entity_id" (:db/id project)
                     "distance" 200}
                    map-features/cadastral-unit-style
                    {:opacity 0.5})}))}
    {}]])

(defn- collapsible-info [{:keys [on-toggle open?]
                          :or {on-toggle identity}} heading info]
  [:div {:class (<class project-style/restriction-container)}
   [ButtonBase {:focus-ripple true
                :class (<class project-style/restriction-button-style)
                :on-click on-toggle}
    [Heading3 heading]
    (if open?
      [icons/hardware-keyboard-arrow-right {:color :primary}]
      [icons/hardware-keyboard-arrow-down {:color :primary}])]
   [Collapse {:in open?}
    info]])

(defn restriction-component
  [e! {:keys [voond toiming muudetud seadus id open?] :as _restriction}]
  [collapsible-info {:open? open?
                     :on-toggle (e! project-controller/->ToggleRestrictionData id)}
   voond
   [itemlist/ItemList {:class (<class project-style/restriction-list-style)}
    [itemlist/Item {:label "Toiming"} toiming]
    [itemlist/Item {:label "Muudetud"} muudetud]
    (when-not (str/blank? seadus)
      [itemlist/Item {:label "Seadus"}
       [:ul
        (util/with-keys
          (for [r (str/split seadus #";")]
            [:li r]))]])]])

(defn restrictions-listing
  [e! data]
  (let [formatted-data (group-by
                         (fn [restriction]
                           (get restriction :type))
                         data)                              ;;TODO: this is ran everytime a restriction is opened should be fixed
        ]
    [:<>
     (doall
       (for [group formatted-data]
         ^{:key (first group)}
         [:div
          [Heading2 {:class (<class project-style/restriction-category-style)} (first group)]
          (doall
            (for [restriction (->> group second (sort-by :voond))]
              ^{:key (get restriction :id)}
              [restriction-component e! restriction]))]))]))

(defn collapse-skeleton
  [title? n]
  [:<>
   (when title?
     [skeleton/skeleton {:parent-style {:padding "1.5rem 0"
                                        :text-transform "capitalize"}
                         :style {:width "40%"}}])
   (doall
     (for [y (range n)]
       ^{:key y}
       [skeleton/skeleton {:style {:width "70%"}
                           :parent-style (skeleton/restriction-skeleton-style)}]))])

(defn project-related-restrictions
  [e! restrictions]
  [restrictions-listing e! restrictions])

(defn- cadastral-unit-component [e! {:keys [id open? lahiaadress tunnus omandivorm pindala
                                            maakonna_nimi omavalitsuse_nimi asustusyksuse_nimi sihtotstarve_1 kinnistu_nr]
                                     :as _unit}]
  [collapsible-info {:open? open?
                     :on-toggle (e! project-controller/->ToggleCadastralHightlight id)}
   (str lahiaadress " " tunnus " " omandivorm " " pindala)
   [itemlist/ItemList {:class (<class project-style/restriction-list-style)}
    [itemlist/Item {:label "Maakonna nimi"} maakonna_nimi]
    [itemlist/Item {:label "Omavalitsuse nimi"} omavalitsuse_nimi]
    [itemlist/Item {:label "Asustusyksuse nimi"} asustusyksuse_nimi]
    [itemlist/Item {:label "Sihtotstarve"} sihtotstarve_1]
    [itemlist/Item {:label "Kinnistu nr"} kinnistu_nr]]])

(defn project-related-cadastral-units
  [e! cadastral-units]
  [:div
   (doall
     ;; TODO: Sorted by address etc for Pilot demo
     (for [{id :id :as unit} (sort-by (juxt :lahiaadress :tunnus :omandivorm :pindala)
                               cadastral-units)]
       ^{:key id}
       [cadastral-unit-component e! unit]))])

(defn project-page [e! {{:keys [project]} :params
                        {:keys [tab]} :query
                        {:keys [add-activity add-task]} :query :as app}
                    {:keys [activities] :as project}
                    breadcrumbs]
  (let [tab (or tab "activities")]
    [:<>
     (when add-activity
       [panels/modal {:title (tr [:project :add-activity])
                      :on-close #(e! (project-controller/->CloseActivityDialog))}
        [activity-view/activity-form e! project-controller/->CloseActivityDialog (get-in app [:project project :new-activity])]])
     (when add-task
       [panels/modal {:title (tr [:project :add-task])
                      :on-close #(e! (project-controller/->CloseTaskDialog))}
        [task-form e! project-controller/->CloseTaskDialog add-task (get-in app [:project project :new-task])]])
     [:div {:class (<class project-style/project-view-container)}
      [Grid {:container true}
       [Grid {:item  true
              :xs    6
              :class (<class common-styles/grid-left-item)}
        [:div {:class (<class common-styles/top-info-spacing)}
         [project-info project breadcrumbs activities]]

        [tabs/tabs {:e!           e!
                    :selected-tab tab}
         {:value "activities"
          :label (tr [:project :activities-tab])}
         {:value "restrictions"
          :label (tr [:project :restrictions-tab])}
         {:value "cadastral-units"
          :label (tr [:project :cadastral-units-tab])}]
        [layout/section
         (case tab
           "activities"
           [project-activity-listing e! project activities]

           "restrictions"
           ^{:key "restrictions"}
           [query/rpc (merge (project-controller/restrictions-rpc project)
                             {:e!         e!
                              :app        app
                              :state-path [:project project :restrictions]
                              :skeleton   [collapse-skeleton true 5]
                              :view       project-related-restrictions})]

           "cadastral-units"
           ^{:key "cadastral-units"}
           [query/rpc (merge (project-controller/cadastral-units-rpc project)
                             {:e!         e!
                              :app        app
                              :state-path [:project project :cadastral-units]
                              :view       project-related-cadastral-units
                              :skeleton   [collapse-skeleton false 5]})])]]
       [Grid {:item true :xs 6}
        [project-map e! (get-in app [:config :api-url]) project (get-in app [:query :tab])]]]]]))
