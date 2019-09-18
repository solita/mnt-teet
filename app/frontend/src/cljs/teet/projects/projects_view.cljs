(ns teet.projects.projects-view
  "Projects view"
  (:require [reagent.core :as r]
            [herb.core :refer [<class]]
            [postgrest-ui.components.listing :as postgrest-listing]
            [postgrest-ui.components.filters :as postgrest-filters]
            [teet.projects.projects-controller :as projects-controller]
            [teet.search.search-interface :as search-interface]
            [teet.ui.icons :as icons]
            [teet.login.login-paths :as login-paths]
            [postgrest-ui.components.item-view :as postgrest-item-view]
            [teet.map.map-view :as map-view]
            [teet.map.map-layers :as map-layers]
            [teet.map.map-features :as map-features]
            [teet.theme.theme-spacing :as theme-spacing]
            [teet.ui.material-ui :refer [TextField TableCell TableSortLabel]]
            [postgrest-ui.display :as display]
            postgrest-ui.elements
            [taoensso.timbre :as log]
            [teet.localization :refer [tr tr-or]]))

(defmethod search-interface/format-search-result "project" [{:keys [id label]}]
  {:icon [icons/file-folder-open]
   :text label
   :href (str "#/project/" id)})

(defn- project-filter [opts]
  [postgrest-filters/simple-search-form ["searchable_text"] opts])

(defn link-to-project  [{:strs [id name]}]
  [:a {:href (str "#/projects/" id)} name])

(defn- projects-header [e! {:keys [column style order on-click]}]
  [TableCell {:sortDirection (if order (name order) false)}
   [TableSortLabel
    {:active (or (= order :asc) (= order :desc))
     :direction (if (= :asc order) "asc" "desc")
     :hideSortIcon false
     :onClick on-click}
    (tr-or [:fields "project" column]
           [:fields :common column]
           column)]
   (case column
     "name" [:div "hakukenttä"]
     [:span])])

(defn projects-listing [e! app]
  [postgrest-listing/listing
   {;:filters-view project-filter
    :endpoint (get-in app [:config :api-url])
    :token (get-in app login-paths/api-token)
    :state (get-in app [:projects :listing])
    :set-state! #(e! (projects-controller/->SetListingState %))
    :table "thk_project_search"
    :select ["id" "name" "road_nr" "km_range" "estimated_duration"]
    :columns ["name" "road_nr" "km_range" "estimated_duration"]
    :accessor {"name" #(select-keys % ["name" "id"])}
    :format {"name" link-to-project}
    :where (projects-controller/project-filter-where (get-in app [:projects :filter]))
    :header-fn (r/partial projects-header e!)}])

(def ^:const project-pin-resolution-threshold 100)

(defn projects-map-page [e! app]
  [map-view/map-view e! {:class (<class theme-spacing/fill-content)
                         :layers {:thk-projects
                                  (map-layers/mvt-layer (get-in app [:config :api-url])
                                                        "mvt_thk_projects"
                                                        {"q" (get-in app [:projects :filter :text])}
                                                        map-features/project-line-style
                                                        {:max-resolution project-pin-resolution-threshold})
                                  :thk-project-pins
                                  (map-layers/geojson-layer (get-in app [:config :api-url])
                                                            "geojson_thk_project_pins"
                                                            {"q" (get-in app [:projects :filter :text])}
                                                            map-features/project-pin-style
                                                            {:min-resolution project-pin-resolution-threshold
                                                             :fit-on-load? true})}}
   app])

(defn projects-list-page [e! app]
  [:<>

   [TextField {:label (tr [:search :quick-search])
               :value (or (get-in app [:projects :new-filter :text]) "")
               :on-change #(e! (projects-controller/->UpdateProjectsFilter {:text (-> % .-target .-value)}))}]
   [projects-listing e! app]])
