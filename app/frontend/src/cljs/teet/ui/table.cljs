(ns teet.ui.table
  "Sortable and filterable table view"
  (:require [teet.ui.material-ui :refer [TableCell TableSortLabel Button
                                         Table TableRow TableHead TableBody
                                         TableSortLabel]]
            [teet.ui.text-field :refer [TextField]]
            [clojure.string :as str]
            [herb.core :refer [<class] :as herb]
            [teet.localization :refer [tr]]
            [teet.theme.theme-colors :as theme-colors]
            [reagent.core :as r]
            [teet.ui.util :refer [mapc]]
            [teet.ui.icons :as icons]
            [teet.ui.panels :as panels]
            [teet.ui.common :as common-ui]))

(defn- table-filter-style
  []
  {:background-color theme-colors/gray-lighter
   :border 0})

(defn- row-style
  [clickable?]
  (if clickable?
    ^{:pseudo {:hover {:background-color theme-colors/gray-lightest}
               :focus {:outline (str "2px solid " theme-colors/blue-light)}}}
    {:transition "background-color 0.2s ease-in-out"
     :cursor :pointer}
    {}))

(defprotocol ListingTableState
  (current-sort-column [this] "Return column and direction of sort")
  (current-show-count [this] "Returns current shown row count (or nil for unlimited)")
  (sort! [this column] "Sort by given column, if already sorted by that column, changes direction")
  (listing-items [this items] "Apply current sorting and show count to given items")
  (show-more! [this] "Increase show count")
  (reset-show-count! [this] "Reset show count back to initial value"))

(defn listing-header
  ([] (listing-header {:filters (r/wrap {} :_)}))
  ([{:keys [state filters columns filter-type
            column-align column-label-fn]
     :or {filter-type {}}}]
   (let [[sort-column sort-direction] (current-sort-column state)]
     [TableHead {}
      [TableRow {}
       (doall
        (for [column columns]
          ^{:key (name column)}
          [TableCell
           (merge {:style {:vertical-align :top}
                   :data-cy (str "table-header-column-" (name column))
                   :sortDirection (if (= sort-column column)
                                    (name sort-direction)
                                    false)}
                  (when-let [a (get column-align column)]
                    {:align a}))
           [TableSortLabel
            {:active (= column sort-column)
             :direction (if (= sort-direction :asc)
                          "asc" "desc")
             :on-click (r/partial sort! state column)}
            (if column-label-fn
              (column-label-fn column)
              (tr [:fields column]))]
           (case (filter-type column)
             :string
             [TextField {:value (or (get @filters column) "")
                         :type "text"
                         :id (str "filter input for " column)
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
                         :id (str "filter input for " column)
                         :variant :filled
                         :start-icon icons/action-search
                         :input-class (<class table-filter-style)
                         :on-change #(swap! filters assoc column
                                            (let [v (-> % .-target .-value)]
                                              (if (str/blank? v)
                                                nil
                                                (js/parseInt v))))}]
             [:span])]))]])))

(defn- default-format-column [_column-name value _row]
  (str value))

(defn- db-id-key [row]
  (str (:db/id row)))

(defn listing-body
  [{:keys [rows on-row-click columns column-align get-column format-column key on-row-hover]
    :or {get-column get
         key db-id-key
         format-column default-format-column}}]
  [TableBody {}
   (doall
    (for [row rows]
      (let [row-key (key row)]
        ^{:key row-key}
        [TableRow (merge
                   {:class (<class row-style (boolean on-row-click))
                    :data-cy (str "row-" row-key)}
                   (when on-row-click
                     {:on-click (r/partial on-row-click row)})
                   (when on-row-hover
                     {:on-mouse-over (r/partial on-row-hover row)}))
         (doall
          (for [column columns]
            ^{:key (name column)}
            [TableCell (merge {:data-cy (str "table-body-column-" (name column))}
                              (when-let [a (get column-align column)]
                                {:align a}))
             (format-column column (get-column row column) row)]))])))])



(defn listing-table-state
  "Create internal state used in listing table, contains how many items are shown
  and the sorting options and functions to access and manipulate."
  ([] (listing-table-state {}))
  ([{:keys [default-sort-column default-show-count get-column get-column-compare]
     :or {default-show-count 20
          get-column get
          get-column-compare (constantly compare)}}]
   (let [sort-column (r/atom [default-sort-column :asc])
         show-count (r/atom default-show-count)]
     (reify ListingTableState
       (current-sort-column [_] @sort-column)
       (current-show-count [_] @show-count)
       (reset-show-count! [_] (reset! show-count default-show-count))
       (sort! [_ col]
         (reset! show-count default-show-count)
         (swap! sort-column
                (fn [[sort-col sort-dir]]
                  (if (= sort-col col)
                    [sort-col (if (= sort-dir :asc)
                                :desc
                                :asc)]
                    [col :asc]))))
       (show-more! [_]
         (swap! show-count + default-show-count))

       (listing-items [_ items]
         (let [[sort-col sort-dir] @sort-column
               sorted-items ((if (= sort-dir :asc) identity reverse)
                             (sort-by #(get-column % sort-col)
                                      (or (get-column-compare sort-col) compare)
                                      items))]
           (if default-show-count
             (take @show-count sorted-items)
             sorted-items)))))))

(defn listing-table-container [& children]
  (into [Table {}] children))

(defn listing-table-body-component
  "Add a special body component. Adds a table body with
  one row containing one cell that spans all the columns.

  The children are added to the one cell."
  [{:keys [columns]} child]
  [:tbody {}
   [:tr {}
    [:td {:colSpan (count columns)}
     child]]])


(defn listing-table
  "Raw table without panel and title.

  default-show-count determines how many items are initially rendered
  and what is the increment when rendering more. If `nil` then all
  items are rendered."
  [{:keys [default-show-count get-column]
    :or {default-show-count 20
         get-column get} :as opts}]
  (let [state (listing-table-state opts)]
    (r/create-class
      {:component-will-receive-props (fn [_this _new-props]
                                       ;; When props change, reset show count
                                       ;; filters/results have changed
                                       (reset-show-count! state))
       :reagent-render
       (fn [{:keys [filters data columns column-align
                    filter-type column-label-fn]
             :or {filter-type {}}
             :as opts}]
         [:<>
          [Table {}
           [listing-header {:state state
                            :filters filters
                            :columns columns
                            :filter-type filter-type
                            :column-align column-align
                            :column-label-fn column-label-fn}]
           [listing-body
            (merge opts
                   {:rows (listing-items state data)})]]
          (when default-show-count
            [common-ui/scroll-sensor
             (r/partial show-more! state)])])})))

(defn- filtered-data [get-column data filters]
  (filter (fn [row]
            (every? (fn [[filter-attribute filter-value]]
                      (let [v (get-column row filter-attribute)]
                        (cond
                          (string? filter-value)
                          ;; v can be a vector in case of :thk.project/owner-info
                          ;; and this component is being replaced shortly
                          ;; hence the use of str
                          (and v (str/includes? (str/lower-case (str v))
                                                (str/lower-case filter-value)))

                          (number? filter-value)
                          (= v filter-value)

                          :else true)))
                    filters))
          data))

(defn table [{:keys [get-column data label after-title title-class]
              :as opts}]
  (r/with-let [filters (r/atom {})
               clear-filters! #(reset! filters {})]
    (let [data (filtered-data (or get-column get) data @filters)]
      ^{:key "table-listing-panel"}
      [panels/panel-with-action
       {:title [:div (when title-class
                       {:class title-class})
                (str label
                     (when-let [total (and (seq data)
                                           (count data))]
                       (str " (" total ")")))
                after-title]
        :action    [Button {:color    "secondary"
                            :on-click clear-filters!
                            :size     "small"
                            :disabled (empty? @filters)
                            :start-icon (r/as-element [icons/content-clear])}
                    (tr [:search :clear-filters])]}
       [listing-table (assoc opts
                             :filters filters
                             :data data)]])))

(defn- simple-table-row-style
  []
  ^{:pseudo {:first-of-type {:border-top :none}}}
  {:border-width "1px 0"
   :border-style :solid
   :border-color theme-colors/gray-lighter})

(defn- table-heading-cell-style
  []
  ^{:pseudo {:first-of-type {:padding-right 0}}}
  {:white-space :nowrap
   :font-weight 500
   :font-size "0.875rem"
   :color theme-colors/gray
   :padding-right "0.5rem"})

(defn- simple-table-cell-style
  []
  ^{:pseudo {:last-of-type {:padding-right 0}}}
  {:padding "0.5rem 0.5rem 0.5rem 0"})

(defn simple-table
  [table-headings rows]
  [:table {:style {:border-collapse :collapse
                   :width "100%"}}
   [:thead
    [:tr {:class (<class simple-table-row-style)}
     (mapc
       (fn [[heading opts]]
         [:td (merge
                {:class (<class table-heading-cell-style)}
                opts)
          heading])
       table-headings)]]
   [:tbody
    (mapc
      (fn [row]
        [:tr {:class (<class simple-table-row-style)}
         (mapc
           (fn [[column {:keys [style class] :as opts}]]
             [:td (merge
                    {:style (merge {} style)
                     :class (herb/join
                              (<class simple-table-cell-style)
                              (when class
                                class))}
                    (dissoc opts :style :class))
              column])
           row)])
      rows)]])
