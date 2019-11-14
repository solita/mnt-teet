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
            [teet.ui.material-ui :refer [TableCell TableSortLabel Button Link
                                         Table TableRow TableHead TableBody
                                         TableSortLabel]]
            [teet.ui.text-field :refer [TextField]]
            postgrest-ui.elements
            [teet.localization :as localization :refer [tr]]
            [teet.ui.panels :as panels]
            [clojure.string :as string]
            [teet.theme.theme-colors :as theme-colors]
            [teet.common.common-controller :as common-controller]
            [teet.projects.projects-style :as projects-style]
            [teet.ui.query :as query]
            [teet.project.project-model :as project-model]
            [postgrest-ui.components.scroll-sensor :as scroll-sensor]
            [teet.ui.skeleton :as skeleton]
            [teet.log :as log]))

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

(defn- projects-listing-header
  ([] (projects-listing-header {:filters (r/wrap {} :_)}))
  ([{:keys [sort! sort-column sort-direction filters]}]
   [TableHead {}
    [TableRow {}
     (doall
      (for [column project-model/project-listing-display-columns]
        ^{:key (name column)}
        [TableCell {:style {:vertical-align :top}
                    :sortDirection (if (= sort-column column)
                                     (name sort-direction)
                                     false)}
         [TableSortLabel
          {:active (= column sort-column)
           :direction (if (= sort-direction :asc)
                        "asc" "desc")
           :on-click (r/partial sort! column)}
          (tr [:fields column])]
         (case column
           :thk.project/name
           [TextField {:value (or (get @filters column) "")
                       :type "text"
                       :variant :filled
                       :start-icon icons/action-search
                       :input-class (<class table-filter-style)
                       :on-change #(swap! filters assoc column (-> % .-target .-value))}]

           :thk.project/road-nr
           [TextField {:value (or (get @filters column) "")
                       :type "number"
                       :variant :filled
                       :start-icon icons/action-search
                       :input-class (<class table-filter-style)
                       :on-change #(swap! filters assoc column
                                          (let [v (-> % .-target .-value)]
                                            (if (string/blank? v)
                                              nil
                                              (js/parseInt v))))}]
           [:span])]))]]))

(defn- projects-listing-table [_ _]
  (let [sort-column (r/atom [:thk.project/name :asc])
        show-count (r/atom 20)
        sort! (fn [col]
                (reset! show-count 20)
                (swap! sort-column
                       (fn [[sort-col sort-dir]]
                         (if (= sort-col col)
                           [sort-col (if (= sort-dir :asc)
                                       :desc
                                       :asc)]
                           [col :asc]))))

        show-more! #(swap! show-count + 20)]
    (r/create-class
     {:component-will-receive-props (fn [_this _new-props]
                                      ;; When props change, reset show count
                                      ;; filters/results have changed
                                      (reset! show-count 20))
      :reagent-render
      (fn [filters projects]
        (let [[sort-col sort-dir] @sort-column]
          [:<>
           [Table {}
            [projects-listing-header {:sort! sort!
                                      :sort-column sort-col
                                      :sort-direction sort-dir
                                      :filters filters}]
            [TableBody {}
             (doall
              (for [project (take @show-count
                                  ((if (= sort-dir :asc) identity reverse)
                                   (sort-by sort-col projects)))]
                ^{:key (:thk.project/id project)}
                [TableRow {}
                 (doall
                  (for [column project-model/project-listing-display-columns]
                    ^{:key (name column)}
                    [TableCell {}
                     (project-model/format-column-value column (project-model/get-column project column))]))]))]]
           [scroll-sensor/scroll-sensor show-more!]]))})))

(defn projects-listing [e! all-projects]
  (r/with-let [open? (r/atom true)
               filters (r/atom {})
               clear-filters! #(reset! filters {})]
    (let [projects (project-model/filtered-projects all-projects @filters)]
      ^{:key "projects-listing-panel"}
      [panels/collapsible-panel
       {:title (str (tr [:projects :title])
                    (when-let [total (and (seq projects)
                                          (count projects))]
                      (str " (" total ")")))
        :open-atom open?
        :action    [Button {:color    "secondary"
                            :on-click clear-filters!
                            :size     "small"
                            :disabled (empty? @filters)
                            :start-icon (r/as-element [icons/content-clear])}
                    (tr [:search :clear-filters])]}
       [projects-listing-table filters projects]])))


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
