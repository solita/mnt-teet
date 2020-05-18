(ns teet.projects.projects-view
  "Projects view"
  (:require [clojure.string :as str]
            [herb.core :refer [<class]]
            postgrest-ui.elements
            [teet.app-state :as app-state]
            [teet.common.common-styles :as common-styles]
            [teet.localization :as localization :refer [tr tr-enum]]
            [teet.map.map-features :as map-features]
            [teet.map.map-layers :as map-layers]
            [teet.map.map-view :as map-view]
            [teet.project.project-controller :as project-controller]
            [teet.project.project-model :as project-model]
            [teet.projects.projects-style :as projects-style]
            [teet.routes :as routes]
            [teet.search.search-interface :as search-interface]
            [teet.theme.theme-spacing :as theme-spacing]
            [teet.ui.format :as format]
            [teet.ui.icons :as icons]
            [teet.ui.material-ui :refer [Link]]
            [teet.ui.table :as table]
            [teet.ui.typography :as typography]
            [teet.ui.util :as util :refer [mapc]]
            [teet.user.user-model :as user-model]))

(defmethod search-interface/format-search-result :project
  [{:thk.project/keys [id] :as project}]
  {:icon [icons/file-folder-open]
   :text (project-model/get-column project :thk.project/project-name)
   :href (str "#/projects/" id)})

(defn name-and-status-view
  [status name thk-id]
  [:div {:class (<class common-styles/flex-align-center)}
   [:div {:class (<class projects-style/project-status-circle-style status)
          :title status}]
   [:p
    [:strong {:class (<class projects-style/project-name-style)} name]
    [typography/GreyText (str "THK" thk-id)]]])

(defn format-column-value [column value row]
  (case column
    :thk.project/project-name
    (let [status (:thk.project/status row)
          thk-id (:thk.project/id row)]
      [name-and-status-view status value thk-id])

    :thk.project/effective-km-range
    (let [[start end] value]
      [:span {:style {:white-space :nowrap}} (str (.toFixed start 3) " \u2013 " (.toFixed end 3))])

    :thk.project/estimated-date-range
    (let [[start end] value]
      (str (format/date start) " \u2013 " (format/date end)))

    :thk.project/owner-info
    (if value
       value
       [:span {:class (<class common-styles/gray-text)}
        (tr [:common :unassigned])])

    :thk.project/activity-status
    (if (empty? value)
      [:ul {:class [(<class projects-style/actitivies-ul-style) (<class common-styles/gray-text)]}
       [:li (tr [:projects :no-activities])]]
      [:ul {:class (<class projects-style/actitivies-ul-style)}
       (mapc
         (fn [activity]
           [:li [:span (tr-enum (:activity/name activity)) " - " (tr-enum (:activity/status activity))]])
         value)])

    ;; Default: stringify
    (str value)))

(defn- user-participates-in-project?
  "The user is considered participating in a project if they're the
  manager or owner, or if they have a valid permission of any kind for
  the project."
  [user project]
  (or (= (-> project :thk.project/owner :user/id)
         (:user/id user))
      (= (-> project :thk.project/manager :user/id)
         (:user/id user))
      ((->> (user-model/projects-with-valid-permission-at user (js/Date.))
            (map :db/id)
            set)
       (:db/id project))))

(defn- filtered-by [value current-user all-projects]

  (cond (= value "unassigned-only")
        (filter (complement :thk.project/owner) all-projects)

        (= value "my-projects")
        (filter (partial user-participates-in-project? current-user)
                all-projects)

        :else
        all-projects))

(defn filter-link [route my-filter current-filter]
  (let [filter-text (tr [:projects :filters (keyword my-filter)])]
    [typography/Text {:display "inline"}
     (if (= my-filter current-filter)
       filter-text
       [Link {:href (routes/url-for (assoc-in route [:query :row-filter] my-filter))}
        filter-text])]))

(defn projects-listing [e! app all-projects]
  (let [row-filter (or (-> app :query :row-filter)
                       "my-projects")
        current-route (select-keys app [:page :params :query])]
    [:<>
     [table/table {:title-class (<class projects-style/title)
                   :after-title [:div {:class (<class projects-style/after-title)}
                                 [filter-link current-route "my-projects" row-filter]
                                 [filter-link current-route "all" row-filter]
                                 [filter-link current-route "unassigned-only" row-filter]]
                   :on-row-click (comp (e! project-controller/->NavigateToProject) :thk.project/id)
                   :label (tr [:projects :title])
                   :data (filtered-by row-filter @app-state/user all-projects)
                   :columns project-model/project-listing-display-columns
                   :get-column project-model/get-column
                   :format-column format-column-value
                   :filter-type {:thk.project/project-name :string
                                 :thk.project/owner-info :string
                                 :thk.project/road-nr :number
                                 :thk.project/region-name :string}
                   :key :thk.project/id
                   :default-sort-column :thk.project/project-name}]]))

(defn projects-map-page [e! app]
  (let [api-url (get-in app [:config :api-url])
        search-term (get-in app [:quick-search :term])
        search-results (into #{}
                             (map :db/id)
                             (get-in app [:quick-search :results]))]
    [map-view/map-view e!
     {:config (:config app)
      :class (<class theme-spacing/fill-content)
      :layer-controls? true
      :layers (merge
               {}
               (when-not (str/blank? search-term)
                 ;; Showing search results, only fetch those
                 {:search-results
                  (map-layers/geojson-layer api-url
                                            "geojson_entities"
                                            {"ids" (str "{" (str/join "," search-results) "}")}
                                            map-features/project-line-style
                                            {:max-resolution map-layers/project-pin-resolution-threshold})
                  :search-result-pins
                  (map-layers/geojson-layer api-url
                                            "geojson_entity_pins"
                                            {"ids" (str "{" (str/join "," search-results) "}")}
                                            map-features/project-pin-style
                                            {:min-resolution map-layers/project-pin-resolution-threshold
                                             :fit-on-load? true})}))}
     (:map app)]))

(defn projects-list-page [e! app projects _breadcrumbs]
  [:div {:class (<class projects-style/projects-table-container)}
   [projects-listing e! app projects]])
