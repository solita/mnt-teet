(ns teet.projects.projects-view
  "Projects view"
  (:require [reagent.core :as r]
            [herb.core :refer [<class]]
            [postgrest-ui.components.listing :as postgrest-listing]
            [teet.projects.projects-controller :as projects-controller]
            [teet.search.search-interface :as search-interface]
            [teet.ui.icons :as icons]
            [teet.login.login-paths :as login-paths]
            [teet.map.map-view :as map-view]
            [teet.map.map-layers :as map-layers]
            [teet.map.map-features :as map-features]
            [teet.theme.theme-spacing :as theme-spacing]
            [teet.ui.material-ui :refer [TableCell TableSortLabel Button Link]]
            [teet.ui.text-field :refer [TextField]]
            postgrest-ui.elements
            [teet.localization :as localization :refer [tr]]
            [teet.ui.panels :as panels]
            [clojure.string :as string]
            [teet.theme.theme-colors :as theme-colors]
            [teet.common.common-controller :as common-controller]
            [teet.projects.projects-style :as projects-style]))

(defmethod search-interface/format-search-result "project" [{:keys [id label]}]
  {:icon [icons/file-folder-open]
   :text label
   :href (str "#/project/" id)})

(defn link-to-project [{:strs [id name]}]
  name)

(defn table-filter-style
  []
  {:background-color theme-colors/gray-lighter
   :border 0})

(defn- column-filter [e! filters column type]
  [TextField {:value (or (get filters column) "")
              :type type
              :variant :filled
              :start-icon icons/action-search
              :input-class (<class table-filter-style)
              :on-change #(e! (projects-controller/->UpdateProjectsFilter
                                column
                                (let [v (-> % .-target .-value)]
                                  (if (string/blank? v)
                                    nil
                                    (if (= "number" type)
                                      (js/parseInt v)
                                      v)))))}])

(defn- projects-header [e! filters {:keys [column order on-click]}]
  [TableCell {:style {:vertical-align :top}
              :sortDirection (if order (name order) false)}
   [TableSortLabel
    {:active       (or (= order :asc) (= order :desc))
     :direction    (if (= :asc order) "asc" "desc")
     :onClick      on-click}
    (localization/label-for-field "thk_project_search" column)]
   (case column
     "name" [column-filter e! filters "name" "text"]
     "road_nr" [column-filter e! filters "road_nr" "number"]
     [:span])])

(defn projects-listing [e! app]
  (r/with-let [open? (r/atom true)]
              (let [where (projects-controller/project-filter-where (get-in app [:projects :filter]))]
                [panels/collapsible-panel
                 {:title     (str (tr [:projects :title])
                                  (when-let [total (get-in app [:projects :total-count])]
                                    (str " (" total ")")))
                  :open-atom open?
                  :action    [Button {:color    "secondary"
                                      :on-click #(e! (projects-controller/->ClearProjectsFilter))
                                      :size     "small"
                                      :disabled (empty? where)
                                      :start-icon (r/as-element [icons/content-clear])}
                              (tr [:search :clear-filters])]}
                 [postgrest-listing/listing
                  {:endpoint          (get-in app [:config :api-url])
                   :token             (get-in app login-paths/api-token)
                   :state             (get-in app [:projects :listing])
                   :set-state!        #(e! (projects-controller/->SetListingState %))
                   :table             "thk_project_search"
                   :row-class         (<class projects-style/row-style)
                   :on-row-click      (fn [{:strs [id]}]
                                        (e! (common-controller/->Navigate :project {:project id} {})))
                   :select            ["id" "name" "road_nr" "km_range" "estimated_duration"]
                   :columns           ["name" "road_nr" "km_range" "estimated_duration"]
                   :accessor          {"name" #(select-keys % ["name" "id"])}
                   :format            {"name" link-to-project}
                   :where             where
                   :header-fn         (r/partial projects-header e! (get-in app [:projects :new-filter]))

                   ;; Extract total count from PostgREST range header
                   :on-fetch-response (fn [^js/Response resp]
                                        (when-let [r (.get (.-headers resp) "Content-Range")]
                                          (let [[_ total] (string/split r "/")]
                                            (e! (projects-controller/->SetTotalCount total)))))}]])))

(def ^:const project-pin-resolution-threshold 100)
(def ^:const project-restriction-resolution 20)
(def ^:const cadastral-unit-resolution 5)

(defn generate-mvt-layers                                   ;;This should probably be moved somewhere else so we can use it in project view also if needed
  [restrictions api-url]
  (into {}
        (for [[type restrictions] restrictions
              :let [selected-restrictions (->> restrictions
                                               (filter second)
                                               (map first))]
              :when (and (not= type "Katastri")
                         (seq selected-restrictions))]
          [(keyword type)
           (map-layers/mvt-layer api-url
                                 "mvt_restrictions"
                                 {"type" type
                                  "layers" (string/join ", " selected-restrictions)}
                                 map-features/project-restriction-style
                                 {:max-resolution project-restriction-resolution})])))


(defn projects-map-page [e! app]
  (let [api-url (get-in app [:config :api-url])]
    [map-view/map-view e! {:class (<class theme-spacing/fill-content)
                           :layer-controls? true
                           :layers (merge {:thk-projects
                                           (map-layers/mvt-layer api-url
                                                                 "mvt_thk_projects"
                                                                 {"q" (get-in app [:projects :filter :text])}
                                                                 map-features/project-line-style
                                                                 {:max-resolution project-pin-resolution-threshold})
                                           :thk-project-pins
                                           (map-layers/geojson-layer api-url
                                                                     "geojson_thk_project_pins"
                                                                     {"q" (get-in app [:projects :filter :text])}
                                                                     map-features/project-pin-style
                                                                     {:min-resolution project-pin-resolution-threshold
                                                                      :fit-on-load? true})}

                                          (when (get-in app [:map :map-restrictions "Katastri" "katastriyksus"])
                                            {:cadastral-units
                                             (map-layers/mvt-layer api-url
                                                                   "mvt_cadastral_units"
                                                                   {}
                                                                   map-features/cadastral-unit-style
                                                                   {:max-resolution cadastral-unit-resolution})})
                                          (generate-mvt-layers (get-in app [:map :map-restrictions]) api-url))}
     (:map app)]))

(defn projects-list-page [e! app]
  [:div {:style {:padding "1.5rem"}}
   [projects-listing e! app]])
