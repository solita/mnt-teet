(ns teet.project.project-view
  (:require [clojure.string :as str]
            [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.activity.activity-view :as activity-view]
            [teet.common.common-styles :as common-styles]
            [teet.localization :refer [tr tr-tree]]
            [teet.map.map-features :as map-features]
            [teet.map.map-layers :as map-layers]
            [teet.map.map-view :as map-view]
            [teet.project.project-controller :as project-controller]
            [teet.project.project-model :as project-model]
            [teet.project.project-style :as project-style]
            [teet.project.project-setup-view :as project-setup-view]
            [teet.project.project-layers :as project-layers]
            [teet.road.road-model :as road-model :refer [km->m]]
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
            [teet.ui.material-ui :refer [Divider Paper]]
            [teet.ui.panels :as panels]
            [teet.ui.progress :as progress]
            [teet.ui.select :as select]
            [teet.ui.skeleton :as skeleton]
            [teet.ui.tabs :as tabs]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.timeline :as timeline]
            [teet.ui.typography :refer [Heading1 Heading2 Heading3] :as typography]
            [teet.ui.url :as url]
            [teet.ui.util :as util]
            [teet.util.collection :as cu]
            [teet.activity.activity-controller :as activity-controller]
            [teet.routes :as routes]
            [teet.map.map-controller :as map-controller]
            [teet.authorization.authorization-check :refer [when-authorized]]
            [teet.log :as log]))

(defn task-form [_e! {:keys [initialization-fn]}]
  ;;Task definition (under project activity)
  ;; Task type (a predefined list of tasks: topogeodeesia, geoloogia, liiklusuuring, KMH eelhinnang, loomastikuuuring, arheoloogiline uuring, muu)
  ;; Description (short description of the task for clarification, 255char, in case more detailed description is needed, it will be uploaded as a file under the task)
  ;; Responsible person (email)
  (when initialization-fn
    (initialization-fn))
  (fn [e! {:keys [close task save on-change delete]}]
    [form/form {:e!              e!
                :value           task
                :on-change-event on-change
                :cancel-event    close
                :save-event      save
                :delete          delete
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
       (doall (for [{id :db/id type :thk.lifecycle/type} lifecycles]
                ^{:key (str id)}
                [:li [:a {:href "foo"}
                      (tr [:enum (:db/ident type)])]]))]]]))


(defn project-header-style
  []
  {:padding "1.5rem 1.875rem"})

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
    [buttons/button-secondary {:on-click (e! project-controller/->OpenEditActivityDialog)} (tr [:buttons :edit])]]
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

(defn project-map [e!
                   app
                   project]
  (r/with-let [overlays (r/atom [])]
    [:div {:style {:flex           1
                   :display        :flex
                   :flex-direction :column}}
     [map-view/map-view e!
      {:class    (<class map-style)
       :layers (reduce (fn [layers layer-fn]
                         (merge layers (layer-fn app project overlays)))
                       {}
                       [project-layers/surveys-layer
                        project-layers/project-road-geometry-layer
                        project-layers/setup-restriction-candidates
                        project-layers/setup-cadastral-unit-candidates
                        project-layers/road-buffer
                        project-layers/related-restrictions
                        project-layers/related-cadastral-units])
       :overlays @overlays}
      map]]))

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

(defn road-geometry-range-input
  [e! {road-buffer-meters :road-buffer-meters}]
  [Paper {:class (<class project-style/road-geometry-range-selector)}
   [:div {:class (<class project-style/wizard-header)}
    [typography/Heading3 "Road geometry inclusion"]]
   [:div {:class (<class project-style/road-geometry-range-body)}
    [TextField {:label       "Inclusion distance"
                :type        :number
                :placeholder "Give value to show related areas"
                :value road-buffer-meters
                :on-change #(e! (project-controller/->ChangeRoadObjectAoe (-> % .-target .-value)))}]]])

(defn project-page-structure
  [e!
   app
   project
   breadcrumbs
   {:keys [header body footer map-settings]}]
  [:div {:class (<class project-style/project-page-structure)}
   [project-header project breadcrumbs]
   [:div {:style {:position "relative"
                  :display  "flex" :flex-direction "column" :flex 1}}
    [project-map e! app project]
    [Paper {:class (<class project-style/project-content-overlay)}
     header
     [:div {:class (<class project-style/content-overlay-inner)}
      body]
     (when footer
       footer)]
    (when (:geometry-range? map-settings)
      [road-geometry-range-input e! (:map app)])]])

(defn project-lifecycle-content
  [e! {{lifecycle-type :db/ident} :thk.lifecycle/type
       activities                 :thk.lifecycle/activities}]
  [:section
   [typography/Heading2 (tr [:enum lifecycle-type])]
   [typography/Heading3
    (if (= lifecycle-type :thk.lifecycle-type/design)
      (tr [:common :design-stage-activities])
      ;; else
      (tr [:common :construction-phase-activities]))]


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

    (if (= lifecycle-type :thk.lifecycle-type/design)
      (tr [:common :design-stage])
      ;; else
      (tr [:common :construction-phase]))]])

(defn activities-tab [e! {:keys [query params page] :as app} project]
  (let [{:keys [activity lifecycle]} query]
    (cond
      activity
      [project-activity e! project (project-model/activity-by-id project activity)]
      lifecycle
      [project-lifecycle-content e! (project-model/lifecycle-by-id project lifecycle)]
      :else
      [:div
       [typography/Heading2 "Lifecycles"]
       (for [lc (:thk.project/lifecycles project)]
         [common/list-button-link (merge {:link  (routes/url-for {:page   page
                                                                  :query  (assoc query :lifecycle (str (:db/id lc)))
                                                                  :params params})
                                          :label (tr [:enum (get-in lc [:thk.lifecycle/type :db/ident])])
                                          :icon  icons/file-folder-open})])])))

(defn people-tab [_e! _app _project]
  [:div "people"])

(defn details-tab [_e! _app project]
  [project-data project])

(defn edit-activity-form
  [_ _ initialization-fn]
  (initialization-fn)
  (fn [e! app _]
    (when-let [activity-data (:edit-activity-data app)]                   ;;Otherwise the form renderer can't format dates properly
      [:div
       [activity-view/activity-form e! (merge {:on-change activity-controller/->UpdateEditActivityForm
                                               :save      activity-controller/->SaveEditActivityForm
                                               :close     project-controller/->CloseAddDialog
                                               :activity  (:edit-activity-data app)}
                                              (when-authorized :activity/delete-activity
                                                {:delete (project-controller/->DeleteActivity (str (:db/id activity-data)))}))]])))

(def project-tabs-layout
  ;; FIXME: Labels with TR paths instead of text
  [{:label     "Activities"
    :value     "activities"
    :component activities-tab
    :layers    #{:thk-project :related-cadastral-units :related-restrictions}}
   {:label     "People"
    :value     "people"
    :component people-tab
    :layers    #{:thk-project}}
   {:label     "Details"
    :value     "details"
    :component details-tab
    :layers    #{:thk-project}}])

(defn selected-project-tab [{{:keys [tab]} :query :as _app}]
  (if tab
    (cu/find-first #(= tab (:value %)) project-tabs-layout)
    (first project-tabs-layout)))

(defn- project-tabs [e! app]
  [tabs/tabs {:e!           e!
              :selected-tab (:value (selected-project-tab app))}
   project-tabs-layout])

(defn- project-tab [e! app project]
  [(:component (selected-project-tab app)) e! app project])

(defn project-page-modals
  [e! {{:keys [add edit]} :query :as app} project]
  (let [[modal modal-label]
        (cond
          add
          (case add
            "task" [[task-form e!
                     {:close     project-controller/->CloseAddDialog
                      :task      (:new-task project)
                      :save      task-controller/->CreateTask
                      :on-change task-controller/->UpdateTaskForm}]
                    (tr [:project :add-task])]
            "activity" [[activity-view/activity-form e! {:close     project-controller/->CloseAddDialog
                                                         :activity  (get-in app [:project (:thk.project/id project) :new-activity])
                                                         :on-change activity-controller/->UpdateActivityForm
                                                         :save      activity-controller/->CreateActivity}]
                        (tr [:project :add-activity])])

          edit
          (case edit
            "activity"
            [[edit-activity-form e! app (e! project-controller/->InitializeActivityEditForm)]
             (tr [:project :edit-activity])])
          :else nil)]
    [panels/modal {:open-atom (r/wrap (boolean modal) :_)
                   :title     (or modal-label "")
                   :on-close  (e! project-controller/->CloseAddDialog)}

     modal]))

(defn- initialized-project-view
  "The project view shown for initialized projects."
  [e! app project breadcrumbs]
  [project-page-structure e! app project breadcrumbs
   {:header       [project-tabs e! app]
    :body         [project-tab e! app project]
    :map-settings {:layers #{:thk-project :surveys}}}])

(defn- project-setup-view
  "The project setup wizard that is shown for uninitialized projects."
  [e! app project breadcrumbs]
  [project-page-structure e! app project breadcrumbs
   (project-setup-view/view-settings e! app project)])

(defn project-page
  "Shows the normal project view for initialized projects, setup wizard otherwise."
  [e! app project breadcrumbs]
  [:<>
   [project-page-modals e! app project]
   (if (project-model/initialized? project)
     [initialized-project-view e! app project breadcrumbs]
     [project-setup-view e! app project breadcrumbs])])
