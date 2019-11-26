(ns teet.projects.projects-view
  "Projects view"
  (:require [reagent.core :as r]
            [herb.core :refer [<class]]
            [teet.projects.projects-controller :as projects-controller]
            [teet.search.search-interface :as search-interface]
            [teet.ui.icons :as icons]
            [teet.map.map-view :as map-view]
            [teet.map.map-layers :as map-layers]
            [teet.map.map-features :as map-features]
            [teet.theme.theme-spacing :as theme-spacing]
            [teet.ui.material-ui :refer [FormControlLabel Checkbox]]
            postgrest-ui.elements
            [teet.localization :as localization :refer [tr]]
            [clojure.string :as string]
            [teet.project.project-model :as project-model]
            [teet.project.project-controller :as project-controller]
            [teet.common.common-styles :as common-styles]
            [teet.ui.format :as format]
            [teet.ui.table :as table]))

(defmethod search-interface/format-search-result "project" [{:keys [id label]}]
  {:icon [icons/file-folder-open]
   :text label
   :href (str "#/project/" id)})

(defn link-to-project [{:strs [id name]}]
  name)


(defn format-column-value [column value]
  (case column
    :thk.project/km-range
    (let [[start end] value]
      (str (.toFixed start 3) " \u2013 " (.toFixed end 3)))

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

(defn projects-listing [e! all-projects]
  (r/with-let [unassigned-only? (r/atom false)]
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
                                 :thk.project/road-nr :number}
                   :key :thk.project/id
                   :default-sort-column :thk.project/project-name}]]))


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
                                                                 "mvt_entities"
                                                                 {"type" "project"}
                                                                 map-features/project-line-style
                                                                 {:max-resolution project-pin-resolution-threshold})
                                           :thk-project-pins
                                           (map-layers/geojson-layer api-url
                                                                     "geojson_entity_pins"
                                                                     {"type" "project"}
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

(defn projects-list-page [e! app projects _breadcrumbs]
  [:div {:style {:padding "1.5rem"}}
   [projects-listing e! projects]])
