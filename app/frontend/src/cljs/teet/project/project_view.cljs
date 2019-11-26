(ns teet.project.project-view
  (:require [clojure.string :as str]
            [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.activity.activity-view :as activity-view]
            [teet.common.common-controller :as common-controller]
            [teet.common.common-styles :as common-styles]
            [teet.localization :refer [tr tr-tree]]
            [teet.log :as log]
            [teet.map.map-features :as map-features]
            [teet.map.map-layers :as map-layers]
            [teet.map.map-view :as map-view]
            [teet.project.project-controller :as project-controller]
            [teet.project.project-model :as project-model]
            [teet.project.project-style :as project-style]
            [teet.project.project-setup-view :as project-setup-view]
            [teet.task.task-controller :as task-controller]
            teet.task.task-spec
            [teet.ui.breadcrumbs :as breadcrumbs]
            [teet.ui.buttons :as buttons]
            [teet.ui.common :as common]
            [teet.ui.container :as container]
            [teet.ui.form :as form]
            [teet.ui.format :as format]
            [teet.ui.icons :as icons]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.material-ui :refer [ButtonBase Collapse Link Divider Paper
                                         Checkbox FormControlLabel]]
            [teet.ui.panels :as panels]
            [teet.ui.progress :as progress]
            [teet.ui.select :as select]
            [teet.ui.skeleton :as skeleton]
            [teet.ui.tabs :as tabs]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.timeline :as timeline]
            [teet.ui.typography :refer [Heading1 Heading2 Heading3] :as typography]
            [teet.ui.url :as url]
            [teet.ui.util :as util]))

(defn task-form [e! {:keys [close task save on-change initialization-fn]}]
  ;;Task definition (under project activity)
  ;; Task type (a predefined list of tasks: topogeodeesia, geoloogia, liiklusuuring, KMH eelhinnang, loomastikuuuring, arheoloogiline uuring, muu)
  ;; Description (short description of the task for clarification, 255char, in case more detailed description is needed, it will be uploaded as a file under the task)
  ;; Responsible person (email)
  (when initialization-fn
    (initialization-fn))
  (fn [e! {:keys [close task save on-change]}]
    [form/form {:e!              e!
                  :value           task
                  :on-change-event on-change
                  :cancel-event    close
                  :save-event      save
                  :spec            :task/new-task-form}
       ^{:xs 12 :attribute :task/type}
       [select/select-enum {:e! e! :attribute :task/type}]

       ^{:attribute :task/description}
       [TextField {:full-width true :multiline true :rows 4 :maxrows 4
                   :variant    :outlined}]

       ^{:attribute :task/assignee}
       [select/select-user {:e! e!}]]))


(defn- activity-info-popup [label start-date end-date num-tasks complete-count incomplete-count]
  [:div
   [:div [:b label]]
   [Divider]
   [:div (format/date start-date) " - " (format/date end-date)]
   (when (pos? num-tasks)
     [:div {:style {:display "flex" :align-items "center"}}
      [progress/circle {:radius 20 :stroke 5}
       {:total   num-tasks
        :success complete-count
        :fail    incomplete-count}]
      (str complete-count " / " num-tasks " tasks complete")])])

(defn project-data
  [{:thk.project/keys [estimated-start-date estimated-end-date road-nr start-m end-m
                       carriageway procurement-nr lifecycles] :as project}]
  (let [project-name (project-model/get-column project :thk.project/project-name)]
    [:div
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

     (when (and estimated-start-date estimated-end-date)
       (let [tr* (tr-tree [:enum])]
         [:<>
          [:br]
          [timeline/timeline {:start-date estimated-start-date
                              :end-date   estimated-end-date}
           (concat
             [{:label      project-name
               :start-date estimated-start-date
               :end-date   estimated-end-date
               :fill       "cyan"
               :hover      [:div project-name]}]
             (for [{:thk.lifecycle/keys [type estimated-start-date estimated-end-date]}
                   (sort-by :thk.lifecycle/estimated-start-date lifecycles)]
               {:label      (-> type :db/ident tr*)
                :start-date estimated-start-date
                :end-date   estimated-end-date
                :fill       "magenta"
                :hover      [:div (tr* (:db/ident type))]}))]]))

     [:div
      "FIXME: lifecycle navigation"
      [:ul
       (for [{id :db/id type :thk.lifecycle/type} lifecycles]
         ^{:key (str id)}
         [:li [:a {:href "foo"}
               (tr [:enum (:db/ident type)])]])]]]))


(defn project-header-style
  []
  {:padding         "1.5rem 1.875rem"})

(defn- project-header [{:thk.project/keys [name] :as project} breadcrumbs activities]
  [:div {:class (<class project-header-style)}
   [:div
    [breadcrumbs/breadcrumbs breadcrumbs]
    [Heading1 (project-model/get-column project :thk.project/project-name)]]]
  #_[project-data activities project])

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
  [e!
   {thk-id :thk.project/id}
   {id             :db/id
    :activity/keys [name tasks estimated-end-date estimated-start-date] :as activity}]
  [:div {:class (<class project-style/project-activity-style)}
   [:div {:style {:display         :flex
                  :justify-content :space-between
                  :align-items     :center}}
    [:h2 (tr [:enum (:db/ident name)])]
    [buttons/button-secondary {:on-click #(println "foobar")} (tr [:buttons :edit])]]
   [:span (format/date estimated-start-date) " – " (format/date estimated-end-date)]

   (if (seq tasks)
     (doall
       (for [{:task/keys [status type] :as t} tasks]
         ^{:key (:db/id t)}
         [common/list-button-link (merge {:link  (str "#/projects/" thk-id "/" (:db/id t))
                                          :label (tr [:enum (:db/ident type)])
                                          :icon  icons/file-folder-open}
                                         (when status
                                           {:end-text (tr [:enum (:db/ident status)])}))]))
     [:div {:class (<class project-style/top-margin)}
      [:em
       (tr [:project :activity :no-tasks])]])
   [buttons/button-primary
    {:on-click (e! project-controller/->OpenTaskDialog)}
    (tr [:project :add-task])]])


(defn map-style
  []
  {:flex 1})

(defn project-map [e! endpoint project tab]
  [:div {:style {:flex           1
                 :display        :flex
                 :flex-direction :column}}
   [map-view/map-view e!
    {:class  (<class map-style)
     :layers (merge {:thk-project
                     (map-layers/geojson-layer endpoint
                                               "geojson_entities"
                                               {"ids" (str "{" (:db/id project) "}")}
                                               map-features/project-line-style
                                               {:fit-on-load? true})}
                    {:related-restrictions
                     (map-layers/geojson-layer endpoint
                                               "geojson_thk_project_related_restrictions"
                                               {"entity_id" (:db/id project)
                                                "distance"  200}
                                               map-features/project-related-restriction-style
                                               {:opacity 0.5})}
                    {:related-cadastral-units
                     (map-layers/geojson-layer endpoint
                                               "geojson_thk_project_related_cadastral_units"
                                               {"entity_id" (:db/id project)
                                                "distance"  200}
                                               map-features/cadastral-unit-style
                                               {:opacity 0.5})})}
    {}]])

(defn restriction-component
  [e! {:keys [voond toiming muudetud seadus id open?] :as _restriction}]
  [container/collapsible-container {:open?     open?
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

(defn collapse-skeleton
  [title? n]
  [:<>
   (when title?
     [skeleton/skeleton {:parent-style {:padding        "1.5rem 0"
                                        :text-transform "capitalize"}
                         :style        {:width "40%"}}])
   (doall
     (for [y (range n)]
       ^{:key y}
       [skeleton/skeleton {:style        {:width "70%"}
                           :parent-style (skeleton/restriction-skeleton-style)}]))])

(defn- cadastral-unit-component [e! {:keys [id open? lahiaadress tunnus omandivorm pindala
                                            maakonna_nimi omavalitsuse_nimi asustusyksuse_nimi sihtotstarve_1 kinnistu_nr]
                                     :as   _unit}]
  [container/collapsible-container {:open?     open?
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

(defn project-page-structure
  [e!
   {{:keys [tab]} :query :as app}
   project
   breadcrumbs
   tabs
   current-tab-view]
  [:div {:style {:display        :flex
                 :flex-direction :column
                 :flex           1}}
   [project-header project breadcrumbs]
   [:div {:style {:position "relative"
                  :display  "flex" :flex-direction "column" :flex 1}}
    [project-map e! (get-in app [:config :api-url]) project (get-in app [:query :tab])]
    [Paper {:class (<class project-style/project-content-overlay)}
     (when (seq tabs)
       [tabs/tabs {:e!           e!
                   :selected-tab (or tab (:value (first tabs)))}
        tabs])
     [:div {:class (<class project-style/content-overlay-inner)}
      current-tab-view]]]])

(defn project-lifecycle-content
  [e! {{id :db/ident} :thk.lifecycle/type
       activities     :thk.lifecycle/activities}]
  [:section
   [typography/Heading2 (tr [:enum id])]
   [typography/Heading3 (tr [:project :activities])]


   (for [{:activity/keys [name estimated-end-date estimated-start-date] :as activity} activities]
     ^{:key (:db/id activity)}
     [:div {:style {:margin-bottom "1rem"}}
      [:a {:href  (url/set-params :activity (:db/id activity))
           :class (<class common-styles/list-item-link)}
       (tr [:enum (:db/ident name)])
       " "
       (format/date estimated-start-date) " — " (format/date estimated-end-date)]])

   [buttons/button-primary
    {:on-click   (e! project-controller/->OpenActivityDialog)
     :start-icon (r/as-element [icons/content-add])}
    (tr [:project :add-activity])]])


(defn activities-tab [e! {:keys [lifecycle activity] :as query} project]
  (cond
    activity
    [project-activity e! project (project-model/activity-by-id project activity)]
    lifecycle
    [project-lifecycle-content e! (project-model/lifecycle-by-id project lifecycle)]
    :else
    [:ul
     (for [lc (:thk.project/lifecycles project)]
       [:li {:on-click #(e! (common-controller/->SetQueryParam :lifecycle (str (:db/id lc))))}
        (tr [:enum (-> lc :thk.lifecycle/type :db/ident)])])]))

(defn people-tab [e! query project]
  [:div "people"])

(defn details-tab [e! query project]
  [project-data project])

(defn project-page [e! {{:keys [tab add step] :as query} :query
                        :as                              app}
                    project
                    breadcrumbs]
  (let [wizard? (or (not (project-model/initialized? project))
                    (some? step))]
    [:<>
     [panels/modal {:open-atom (r/wrap (boolean add) :_)
                    :title     (if-not add
                                 ""
                                 (tr [:project (case add
                                                 "task" :add-task
                                                 "activity" :add-activity)]))
                    :on-close  (e! project-controller/->CloseAddDialog)}
      (case add
        "task"
        [task-form e!
         {:close project-controller/->CloseAddDialog
          :task (:new-task project)
          :save task-controller/->CreateTask
          :on-change task-controller/->UpdateTaskForm}]

        "activity"
        [activity-view/activity-form e! project-controller/->CloseAddDialog
         (get-in app [:project (:thk.project/id project) :new-activity])]

        [:span])]
     [project-page-structure e! app project breadcrumbs
      (when (not wizard?)
        ;; FIXME: localize
        [{:label "Activities" :value "activities"}
         {:label "People" :value "people"}
         {:label "Details" :value "details"}])
      (if wizard?
        [project-setup-view/project-setup-wizard e! project (or step "basic-information")]
        (case (or tab "activities")
          "activities" [activities-tab e! query project]
          "people" [people-tab e! query project]
          "details" [details-tab e! query project]))]]))
