(ns teet.project.project-view
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.map.map-features :as map-features]
            [teet.map.map-layers :as map-layers]
            [teet.map.map-view :as map-view]
            [teet.ui.material-ui :refer [ButtonBase Collapse Link Divider Paper
                                         Checkbox FormControlLabel]]
            [teet.ui.text-field :refer [TextField]]
            [teet.project.project-controller :as project-controller]
            [teet.project.project-style :as project-style]
            [teet.ui.breadcrumbs :as breadcrumbs]
            [teet.ui.skeleton :as skeleton]
            [teet.ui.format :as format]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.icons :as icons]
            [teet.ui.common :as common]
            [teet.ui.typography :refer [Heading1 Heading2 Heading3] :as typography]
            [teet.localization :refer [tr tr-tree]]
            [teet.ui.panels :as panels]
            [teet.ui.buttons :as buttons]
            teet.task.task-spec
            [teet.activity.activity-view :as activity-view]
            [teet.ui.util :as util]
            [clojure.string :as str]
            [teet.common.common-styles :as common-styles]
            [teet.ui.form :as form]
            [teet.task.task-controller :as task-controller]
            [teet.ui.select :as select]
            [teet.ui.timeline :as timeline]
            [teet.ui.progress :as progress]
            [teet.project.project-model :as project-model]
            [teet.log :as log]
            [teet.ui.url :as url]
            [teet.ui.tabs :as tabs]
            [teet.common.common-controller :as common-controller]))

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

(defn project-name-hover
  [project-name]
  (log/info "project-name-hover called")
  [:div project-name])


(defn- road-overlay [project]
  (log/info "road-overlay component called")
  [itemlist/ItemList {}
   [itemlist/Item {:label "Start meters"} (:thk-project/start-m project)]
   [itemlist/Item {:label "End meters"} (:thk-project/end-m project)]])


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
               :hover      [project-name-hover project-name]}]
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

   ;; TEET-288 "Add start and end meter pointers to map in project page" should be impl here.
   ;; OL 4.x docs: https://openlayers.org/en/v4.6.5/apidoc/
   ;; possible starting point for line end/start marker impl: https://stackoverflow.com/questions/39095736/openlayers-3-draw-start-pointcsp-of-a-line-string
   
   (log/info "project-map called - making a map-view comonent and passing in km-labeled-line-style as style:" (some? map-features/km-labeled-line-style))
   [map-view/map-view e!
    {:class  (<class map-style)
     :overlays [(when-let [road-nr (:thk.project/road-nr project)]
                  {; :coordinate (js->clj (aget road-address "point" "coordinates"))
                   :content [road-overlay project]})]
     :layers (merge {:thk-project
                     (map-layers/geojson-layer endpoint
                                               "geojson_entities"
                                               {"ids" (str "{" (:db/id project) "}")}
                                               map-features/km-labeled-line-style
                                               ;; map-features/road-line-style
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

(defn- collapsible-info [{:keys [on-toggle open?]
                          :or   {on-toggle identity}} heading info]
  [:div {:class (<class project-style/restriction-container)}
   [ButtonBase {:focus-ripple true
                :class        (<class project-style/restriction-button-style)
                :on-click     on-toggle}
    [Heading3 heading]
    (if open?
      [icons/hardware-keyboard-arrow-right {:color :primary}]
      [icons/hardware-keyboard-arrow-down {:color :primary}])]
   [Collapse {:in (boolean open?)}
    info]])

(defn restriction-component
  [e! {:keys [voond toiming muudetud seadus id open?] :as _restriction}]
  [collapsible-info {:open?     open?
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

(defn- restrictions-check-group [restrictions checked-restrictions toggle-restriction]
  [:div
   (doall
    (for [{:keys [voond id] :as restriction} (sort-by :voond restrictions)
          :let [checked? (boolean (checked-restrictions id))]]
      ^{:key id}
      [FormControlLabel
       {:control (r/as-element
                  [Checkbox {:color :primary
                             :checked checked?
                             :on-change (r/partial toggle-restriction id)}])
        :label voond}]))])

(defn restrictions-listing
  [e! {:keys [restrictions checked-restrictions toggle-restriction]}]
  (r/with-let [open-types (r/atom #{})
               restrictions-by-type (group-by :type restrictions)]
    [:<>
     (doall
      (for [[group restrictions] restrictions-by-type
            :let [group-checked (into #{}
                                      (comp
                                       (map :id)
                                       (filter checked-restrictions))
                                      restrictions)]]
        ^{:key group}
         [collapsible-info {:on-toggle (fn [_]
                                         (swap! open-types #(if (% group)
                                                             (disj % group)
                                                             (conj % group))))
                            :open? (@open-types group)}
          group
          [restrictions-check-group restrictions group-checked toggle-restriction]]))]))

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
  [collapsible-info {:open?     open?
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

(defn initialization-form-footer [{:keys [cancel validate disabled?]}]
  [:div {:class (<class project-style/wizard-footer)}
   ;; TODO this should be a text button and cancel
   [typography/Text "Skip setup"]
   [buttons/button-primary
    {:on-click validate
     :type     :submit
     :disabled disabled?}
    "Next"]])

(defn project-setup-basic-information-form
  [e! project]
  (e! (project-controller/->UpdateBasicInformationForm
       {:thk.project/project-name (:thk.project/name project)}))
  (fn [e! project]
    [:<>
     [:div {:class (<class project-style/initialization-form-wrapper)}
      [form/form {:e!              e!
                  :value           (:basic-information-form project)
                  :on-change-event project-controller/->UpdateBasicInformationForm
                  :save-event      project-controller/->SaveBasicInformation
                  :class (<class project-style/wizard-form)
                  :footer initialization-form-footer}

       ^{:attribute :thk.project/project-name}
       [TextField {:full-width true :variant :outlined}]

       ^{:attribute :thk.project/owner}
       [select/select-user {:e! e!}]]]]))

(defn project-setup-restrictions-form [e! _]
  (e! (project-controller/->FetchRestrictions))
  (fn [e! {:keys [restriction-candidates checked-restrictions] :as project}]
    (when restriction-candidates
      [restrictions-listing e! {:restrictions restriction-candidates
                                :checked-restrictions (or checked-restrictions #{})
                                :toggle-restriction (e! project-controller/->ToggleRestriction)}])))

(defn project-setup-cadastral-units-form [e! project]
  [:div "Tada"])

(defn project-setup-activities-form [e! project]
  [:div "Tada"])

(defn project-setup-wizard [e! project step]
  (let [[step label component]
        (case step
          "basic-information" [1 :basic-information [project-setup-basic-information-form e! project]]
          "restrictions" [2 :restrictions [project-setup-restrictions-form e! project]]
          "cadastral-units" [3 :cadastral-units [project-setup-cadastral-units-form e! project]]
          "activities" [4 :activities [project-setup-activities-form e! project]])]
    [:<>
    [:div {:class (<class project-style/wizard-header)}
     [:div {:class (<class project-style/wizard-header-step-info)}
      [typography/Text {:color :textSecondary}
       (tr [:project :wizard :project-setup])]
      [typography/Text {:color :textSecondary}
       (tr [:project :wizard :step-of] {:current step :total 4})]]
     [typography/Heading2 (tr [:project :wizard label])]]
     component]))

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
        [project-setup-wizard e! project (or step "basic-information")]
        (case (or tab "activities")
          "activities" [activities-tab e! query project]
          "people" [people-tab e! query project]
          "details" [details-tab e! query project]))]]))
