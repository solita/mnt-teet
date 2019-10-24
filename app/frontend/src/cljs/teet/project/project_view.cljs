(ns teet.project.project-view
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.map.map-features :as map-features]
            [teet.map.map-layers :as map-layers]
            [teet.map.map-view :as map-view]
            [teet.login.login-paths :as login-paths]
            [postgrest-ui.components.item-view :as postgrest-item-view]
            [teet.ui.material-ui :refer [Grid Button ButtonBase Collapse]]
            [teet.project.project-controller :as project-controller]
            [teet.project.project-style :as project-style]
            [teet.theme.theme-spacing :as theme-spacing]
            [teet.ui.breadcrumbs :as breadcrumbs]
            [teet.ui.skeleton :as skeleton]
            [teet.ui.format :as format]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.icons :as icons]
            [teet.ui.typography :refer [Heading1 Heading2 Heading3]]
            [teet.ui.layout :as layout]
            [teet.localization :refer [tr]]
            [teet.ui.panels :as panels]
            [teet.ui.buttons :as buttons]
            [teet.phase.phase-view :as phase-view]
            [teet.task.task-view :as task-view]
            [teet.ui.util :as util]
            [clojure.string :as str]
            [teet.ui.query :as query]
            [teet.ui.tabs :as tabs]
            [teet.common.common-styles :as common-styles]))

(defn project-data
  [{:strs [name estimated_duration road_nr km_range carriageway procurement_no]}]
  [:div {:class (<class project-style/project-info)}
   [Heading1 name]
   [itemlist/ItemList
    {}
    [:div (tr [:project :information :estimated-duration])
     ": "
     (format/date-range estimated_duration)]
    [:div (tr [:project :information :road-number]) ": " road_nr]
    [:div (tr [:project :information :km-range]) ": " (format/km-range km_range)]
    [:div (tr [:project :information :procurement-number]) ": " procurement_no]
    [:div (tr [:project :information :carriageway]) ": " carriageway]]])


(defn- project-info [endpoint token project breadcrumbs]
  [:div
   [breadcrumbs/breadcrumbs breadcrumbs]
   [postgrest-item-view/item-view
    {:endpoint endpoint
     :token token
     :table "thk_project"
     :select ["name" "estimated_duration"
              "road_nr" "km_range" "carriageway"
              "procurement_no"]
     :view project-data}
    project]])

(defn phase-action-heading
  [{:keys [heading button]}]
  [:div {:class (<class project-style/phase-action-heading)}
   [Heading2 heading]
   button])

;; TODO: Added for pilot demo. Maybe later store in database, make customizable?
(def phase-sort-priority-vec
  [:phase.name/pre-design
   :phase.name/preliminary-design
   :phase.name/land-acquisition
   :phase.name/detailed-design
   :phase.name/construction
   :phase.name/other])

(defn- phase-sort-priority [phase]
  (.indexOf phase-sort-priority-vec
            (-> phase :phase/phase-name :db/ident)))

(defn project-phase-listing [e! project phases]
  [:<>
   [phase-action-heading {:heading (tr [:project :phases])
                          :button [buttons/button-primary {:on-click (e! project-controller/->OpenPhaseDialog)
                                                           :start-icon (r/as-element [icons/content-add-circle])}
                                   (tr [:project :add-phase])]}]
   (doall
     (for [{id :db/id
            :phase/keys [phase-name tasks
                         _estimated-start-date _estimated-end-date]}
           (sort-by phase-sort-priority
                    phases)]
       ^{:key id}
       [itemlist/ItemList {:title (tr [:enum (:db/ident phase-name)])
                           ;; :subtitle (str (.toLocaleDateString estimated-start-date) " - "
                           ;;                (.toLocaleDateString estimated-end-date))
                           :variant :secondary}
        (if (seq tasks)
          (doall
           (for [t tasks]
             ^{:key (:db/id t)}
             [:div
              [Button {:element "a"
                       :href (str "#/projects/" project "/" id "/" (:db/id t))}
               [icons/file-folder]
               (tr [:enum (:db/ident (:task/type t))])]]))
          [:div [:em (tr [:project :phase :no-tasks])]])

        [:div
         [Button {:on-click (r/partial e! (project-controller/->OpenTaskDialog id))
                  :size "small"
                  :start-icon (r/as-element [icons/content-add-circle])}
          (tr [:project :add-task])]]]))])

(defn project-map [e! endpoint project tab]
  [:div {:class (<class project-style/project-map-style)}
   [map-view/map-view e!
    {:class (<class theme-spacing/fill-content)
     :layers (merge {:thk-project
                     (map-layers/geojson-layer endpoint
                       "geojson_thk_project"
                       {"id" project}
                       map-features/project-line-style
                       {:fit-on-load? true})}
               (when (= tab "restrictions")
                 {:related-restrictions
                  (map-layers/geojson-layer endpoint
                    "geojson_thk_project_related_restrictions"
                    {"project_id" project
                     "distance" 200}
                    map-features/project-related-restriction-style
                    {:opacity 0.5})})
               (when (= tab "cadastral-units")
                 {:related-cadastral-units
                  (map-layers/geojson-layer endpoint
                    "geojson_thk_project_related_cadastral_units"
                    {"project_id" project
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
                        {:keys [add-phase add-task]} :query :as app}
                    phases
                    breadcrumbs]
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
        [:div {:class (<class common-styles/gray-bg-content)}
         [project-info (get-in app [:config :api-url]) (get-in app login-paths/api-token) project breadcrumbs]

         [tabs/tabs {:e! e!
                     :selected-tab tab}
          {:value "documents"
           :label (tr [:project :documents-tab])}
          {:value "restrictions"
           :label (tr [:project :restrictions-tab])}
          {:value "cadastral-units"
           :label (tr [:project :cadastral-units-tab])}]]
        [layout/section
         (case tab
           "documents"
           [project-phase-listing e! project phases]

           "restrictions"
           ^{:key "restrictions"}
           [query/rpc (merge (project-controller/restrictions-rpc project)
                        {:e! e!
                         :app app
                         :state-path [:project project :restrictions]
                         :skeleton [collapse-skeleton true 5]
                         :view project-related-restrictions})]

           "cadastral-units"
           ^{:key "cadastral-units"}
           [query/rpc (merge (project-controller/cadastral-units-rpc project)
                        {:e! e!
                         :app app
                         :state-path [:project project :cadastral-units]
                         :view project-related-cadastral-units
                         :skeleton [collapse-skeleton false 5]})])]]
       [Grid {:item true :xs 6}
        [project-map e! (get-in app [:config :api-url]) project (get-in app [:query :tab])]]]]]))
