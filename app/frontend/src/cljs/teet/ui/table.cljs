(ns teet.ui.table
  "Sortable and filterable table view"
  (:require [teet.ui.material-ui :refer [TableCell TableSortLabel Button Link
                                         Table TableRow TableHead TableBody
                                         TableSortLabel]]
            [teet.ui.text-field :refer [TextField]]
            [clojure.string :as str]
            [herb.core :refer [<class]]
            [teet.localization :refer [tr]]
            [teet.theme.theme-colors :as theme-colors]
            [reagent.core :as r]
            [teet.ui.icons :as icons]
            [postgrest-ui.components.scroll-sensor :as scroll-sensor]
            [teet.ui.panels :as panels]))

(defn table-filter-style
  []
  {:background-color theme-colors/gray-lighter
   :border 0})

(defn row-style
  []
  ^{:pseudo {:hover {:background-color theme-colors/gray-lightest}
             :focus {:outline (str "2px solid " theme-colors/blue-light)}}}
  {:transition "background-color 0.2s ease-in-out"
   :cursor :pointer})

(defn- listing-header
  ([] (listing-header {:filters (r/wrap {} :_)}))
  ([{:keys [sort! sort-column sort-direction filters columns filter-type]
     :or {filter-type {}}}]
   [TableHead {}
    [TableRow {}
     (doall
      (for [column columns]
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
         (case (filter-type column)
           :string
           [TextField {:value (or (get @filters column) "")
                       :type "text"
                       :variant :filled
                       :start-icon icons/action-search
                       :input-class (<class table-filter-style)
                       :on-change #(swap! filters
                                          (fn [filters]
                                            (let [v (-> % .-target .-value)]
                                              (if (str/blank? v)
                                                (dissoc filters column)
                                                (assoc filters column v)))))}]

           :number
           [TextField {:value (or (get @filters column) "")
                       :type "number"
                       :variant :filled
                       :start-icon icons/action-search
                       :input-class (<class table-filter-style)
                       :on-change #(swap! filters assoc column
                                          (let [v (-> % .-target .-value)]
                                            (if (str/blank? v)
                                              nil
                                              (js/parseInt v))))}]
           [:span])]))]]))

(defn- listing-table [{:keys [default-sort-column]}]
  (let [sort-column (r/atom [default-sort-column :asc])
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
      (fn [{:keys [on-row-click filters data columns filter-type get-column format-column key]}]
        (let [[sort-col sort-dir] @sort-column]
          [:<>
           [Table {}
            [listing-header {:sort! sort!
                             :sort-column sort-col
                             :sort-direction sort-dir
                             :filters filters
                             :columns columns
                             :filter-type filter-type}]
            [TableBody {}
             (doall
              (for [row (take @show-count
                              ((if (= sort-dir :asc) identity reverse)
                               (sort-by #(get-column % sort-col) data)))]
                ^{:key (get row key)}
                [TableRow {:on-click #(on-row-click row)
                           :class (<class row-style)}
                 (doall
                  (for [column columns]
                    ^{:key (name column)}
                    [TableCell {}
                     (format-column column (get-column row column))]))]))]]
           [scroll-sensor/scroll-sensor show-more!]]))})))

(defn- filtered-data [get-column data filters]
  (filter (fn [row]
            (every? (fn [[filter-attribute filter-value]]
                      (let [v (get-column row filter-attribute)]
                        (cond
                          (string? filter-value)
                          (and v (str/includes? (str/lower-case v)
                                                (str/lower-case filter-value)))

                          (number? filter-value)
                          (= v filter-value)

                          :else true)))
                    filters))
          data))

(defn table [{:keys [get-column data label]
              :as opts}]
  (r/with-let [open? (r/atom true)
               filters (r/atom {})
               clear-filters! #(reset! filters {})]
    (let [data (filtered-data (or get-column get) data @filters)]
      ^{:key "table-listing-panel"}
      [panels/collapsible-panel
       {:title (str label
                    (when-let [total (and (seq data)
                                          (count data))]
                      (str " (" total ")")))
        :open-atom open?
        :action    [Button {:color    "secondary"
                            :on-click clear-filters!
                            :size     "small"
                            :disabled (empty? @filters)
                            :start-icon (r/as-element [icons/content-clear])}
                    (tr [:search :clear-filters])]}
       [listing-table (assoc opts
                             :filters filters
                             :data data)]])))
