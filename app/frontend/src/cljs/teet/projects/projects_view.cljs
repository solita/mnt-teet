(ns teet.projects.projects-view
  "Projects view"
  (:require [reagent.core :as r]
            [herb.core :refer [<class]]
            [teet.search.search-interface :as search-interface]
            [teet.ui.icons :as icons]
            [teet.map.map-view :as map-view]
            [teet.map.map-layers :as map-layers]
            [teet.map.map-features :as map-features]
            [teet.theme.theme-spacing :as theme-spacing]
            [teet.ui.material-ui :refer [FormControlLabel Checkbox]]
            postgrest-ui.elements
            [teet.localization :as localization :refer [tr]]
            [teet.project.project-model :as project-model]
            [teet.project.project-controller :as project-controller]
            [teet.common.common-styles :as common-styles]
            [teet.ui.format :as format]
            [teet.ui.table :as table]
            [teet.common.common-controller :as common-controller]
            [clojure.string :as str]
            [teet.ui.typography :as typography]
            [teet.projects.projects-style :as projects-style]))

(defmethod search-interface/format-search-result :project
  [{:thk.project/keys [id] :as project}]
  {:icon [icons/file-folder-open]
   :text (project-model/get-column project :thk.project/project-name)
   :href (str "#/projects/" id)})

(defn name-and-status-view
  [status name thk-id]
  [:div {:class (<class projects-style/name-and-status-row-style)}
   [:div {:class (<class projects-style/status-circle-style status)
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

    ;; Default: stringify
    (str value)))

(defn projects-listing [e! app all-projects]
  (let [unassigned-only? (common-controller/query-param-boolean-atom app :unassigned)]
    [:<>
     [table/table {:after-title [:div {:style {:margin-left "2rem" :display :inline-block}}
                                 [FormControlLabel
                                  {:value "unassigned-only"
                                   :label-placement :end
                                   :label (tr [:projects :unassigned-only])
                                   :control (r/as-element [Checkbox {:checked @unassigned-only?
                                                                     :on-change #(swap! unassigned-only? not)}])}]]
                   :on-row-click (comp (e! project-controller/->NavigateToProject) :thk.project/id)
                   :label (tr [:projects :title])
                   :data (if @unassigned-only?
                           (filter (complement :thk.project/owner) all-projects)
                           all-projects)
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
  [:div {:style {:padding "1.5rem"}}
   [projects-listing e! app projects]])
