(ns teet.file.file-view
  (:require [clojure.string :as str]
            [herb.core :refer [<class]]
            [re-svg-icons.feather-icons :as fi]
            [re-svg-icons.tabler-icons :as ti]
            [reagent.core :as r]
            [teet.common.common-controller :as common-controller]
            [teet.common.common-styles :as common-styles]
            [teet.file.file-controller :as file-controller]
            [teet.file.file-style :as file-style]
            [teet.localization :refer [tr tr-enum]]
            [teet.project.project-model :as project-model]
            [teet.project.project-navigator-view :as project-navigator-view]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.buttons :as buttons]
            [teet.ui.file-upload :as file-upload]
            [teet.ui.format :as format]
            [teet.ui.icons :as icons]
            [teet.ui.material-ui :refer [Grid Link LinearProgress IconButton]]
            [teet.ui.panels :as panels]
            [teet.ui.select :as select]
            [teet.ui.tabs :as tabs]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.typography :as typography]
            [teet.ui.url :as url]
            [teet.ui.util :refer [mapc]]
            [teet.user.user-model :as user-model]
            [teet.util.datomic :as du]))



(defn- file-row-icon-style
  []
  {:margin "0 0.25rem"})

(defn- base-name-and-suffix [name]
  (let [[_ base-name suffix] (re-matches #"^(.*)\.([^\.]+)$" name)]
    (if base-name
      [base-name (str/upper-case suffix)]
      [name ""])))

(defn- file-row
  [{:keys [link-download? actions? comment-action]
    :or {link-download? false
         actions? true}}
   {id :db/id :file/keys [number version status name] :as file}]
  (let [[base-name suffix] (base-name-and-suffix name)]
    [:div.file-row {:class [(<class common-styles/flex-row) (<class common-styles/margin-bottom 0.5)]}
     [:div.file-row-name {:class (<class common-styles/flex-table-column-style 44)}
      (if link-download?
        [Link {:target :_blank
               :href (common-controller/query-url :file/download-file {:file-id id})}
         base-name]
        [url/Link {:page :file :params {:file id}} base-name])]
     [:div.file-row-number {:class (<class common-styles/flex-table-column-style 10)}
      [:span number]]
     [:div.file-row-suffix {:class (<class common-styles/flex-table-column-style 10)}
      [:span suffix]]
     [:div.file-row-version {:class (<class common-styles/flex-table-column-style 10)}
      [:span (str "V" version)]]
     [:div.file-row-status {:class (<class common-styles/flex-table-column-style 13)}
      [:span (tr-enum status)]]
     (when actions?
       [:div.file-row-actions {:class (<class common-styles/flex-table-column-style 13 :flex-end)}
        (if comment-action
          [IconButton {:on-click #(comment-action file)}
           [icons/communication-comment]]
          [url/Link {:class ["file-row-action-comments" (<class file-row-icon-style)]
                     :page :file
                     :params {:file id}
                     :query {:tab "comments"}}
           [icons/communication-comment]])
        [Link {:class ["file-row-action-download" (<class file-row-icon-style)]
               :target :_blank
               :href (common-controller/query-url :file/download-file {:file-id id})}
         [icons/file-cloud-download]]])]))

(defn- other-version-row
  [{id :db/id
    :file/keys [name version status]
    :meta/keys [created-at creator]
    :as _file}]
  [:div.file-row {:class [(<class common-styles/flex-row) (<class common-styles/margin-bottom 0.5)]}
   [:div.file-row-name {:class (<class common-styles/flex-table-column-style 55)}
    [url/Link {:page :file :params {:file id}}
     name]]
   [:div.file-row-version {:class (<class common-styles/flex-table-column-style 10 :center)}
    [:span (str "V" version)]]
   [:div.file-row-status {:class (<class common-styles/flex-table-column-style 10 :center)}
    [:span (tr-enum status)]]
   [:div.file-row-date {:class (<class common-styles/flex-table-column-style 25 :flex-end)}
    [:span (format/date created-at)]]])

(def ^:private sorters
  {"meta/created-at" [(juxt :meta/created-at :file/name) >]
   "file/name"       [:file/name <]
   "file/type"       [(juxt :file/type :file/name) <]
   "file/status"     [(juxt :file/status :file/name) <]})

(defn- sort-items
  []
  (mapv #(assoc % :label (tr (conj [:file :sort-by (-> % :value keyword)])))
        [{:value "meta/created-at"}
         {:value "file/name"}
         {:value "file/type"}
         {:value "file/status"}]))

(defn- file-filter-and-sorter [filter-atom sort-by-atom items]
  [:div.file-table-filters {:class (<class file-style/filter-sorter)}
   [:div.file-table-name-filter
    [TextField {:value @filter-atom
                :start-icon icons/action-search
                :on-change #(reset! filter-atom (-> % .-target .-value))}]]
   [:div.file-table-sorting
    [select/select-with-action
     {:id "file-sort-select"
      :name "file-sort"
      :show-label? false
      :value @sort-by-atom
      :items items
      :on-change #(reset! sort-by-atom %)}]]])

(defn- filter-predicate
  "matches files whose name contains one of the whitespace separated
  words, case insensitive"
  [filter-value]
  (let [words (map str/lower-case
                   (str/split filter-value #"\s+"))]
    (fn [{file-name :file/name}]
      (boolean (every? #(str/includes? (str/lower-case file-name) %)
                       words)))))

(defn- filtered-by [filter-value files]
  (if (str/blank? filter-value)
    files
    (let [filter-fn (filter-predicate filter-value)]
      (filter filter-fn files))))

(defn- sorted-by [{:keys [value]} files]
  (let [[sort-fn comparator] (sorters value)]
    (sort-by sort-fn comparator files)))

(defn file-table
  ([files] (file-table {} files))
  ([opts files]
   (r/with-let [items-for-sort-select (sort-items)
                filter-atom (r/atom "")
                sort-by-atom (r/atom (first items-for-sort-select))]
     [:<>
      [file-filter-and-sorter
       filter-atom
       sort-by-atom
       items-for-sort-select]
      [:div.file-table-files
       (->> files
            (filtered-by @filter-atom)
            (sorted-by @sort-by-atom)
            (mapc (r/partial file-row opts)))]])))

(defn file-upload-button []
  [buttons/button-primary {:href (url/set-query-param :add-document 1)
                           :start-icon (r/as-element
                                        [icons/file-cloud-upload])}
   (tr [:task :upload-files])])

(defn- file-icon-style []
  {:display :inline-block
   :margin-right "0.25rem"
   :position :relative
   :top "6px"})

(defn file-icon [{class :class
                  :file/keys [type]}]
  [:div {:class (if class
                  [class (<class file-icon-style)]
                  (<class file-icon-style))}
   (cond
     (and type (str/starts-with? type "image/"))
     [fi/image]

     :else
     [ti/file])])

(defn- file-list-field-style []
  {:flex-basis "25%" :flex-shrink 0 :flex-grow 0})

(defn- file-list [files current-file-id]
  [:div.file-list
   [typography/Heading2 (tr [:task :results])]
   [:div
    (mapc (fn [{id :db/id :file/keys [name version number status] :as f}]
            (let [[_ base-name suffix] (re-matches #"^(.*)\.([^\.]+)$" name)]
              [:div.file-list-entry
               [:div
                [file-icon (assoc f :class "file-list-icon")]
                (if (= current-file-id id)
                  [:b.file-list-name-active (or base-name name)]
                  [url/Link {:page :file
                             :params {:file id}
                             :class "file-list-name"}
                   (or base-name name)])]
               [:div {:style {:font-size "12px" :display :flex
                              :justify-content :space-between
                              :margin "0 1rem 1rem 1rem"}}
                [:span.file-list-suffix {:class (<class file-list-field-style)}
                 (if suffix
                    (str/upper-case suffix)
                    "")]
                [:span.file-list-number {:class (<class file-list-field-style)} number]
                [:span.file-list-version {:class (<class file-list-field-style)} (str "V" version)]
                (when status
                  [:span.file-list-status {:class (<class file-list-field-style)} (tr-enum status)])]]))
          files)]])

(defn- labeled-data [[class label data]]
  [:div {:class [class (<class common-styles/inline-block)]}
   [:div [:b label]]
   [:div data]])

(defn preview-style []
  {:background-color theme-colors/gray-lightest
   :width "100%"
   :height "250px"
   :text-align :center
   :vertical-align :middle
   :display :flex
   :justify-content :space-around
   :flex-direction :column
   :margin "2rem 0 2rem 0"})

(defn- file-details [e! {:keys [replacement-upload-progress] :as file} latest-file edit-open?]
  (let [old? (some? latest-file)
        other-versions (if old?
                         (into [latest-file]
                               (filter #(not= (:db/id file) (:db/id %))
                                       (:versions latest-file)))
                         (:versions file))]
    [:div.file-details
     [:div.file-details-header {:class [(<class common-styles/heading-and-action-style) (<class common-styles/margin-bottom 2)]}
      [typography/Heading2 [file-icon file] (:file/name file)]

      [buttons/button-secondary {:on-click #(reset! edit-open? true)}
       (tr [:buttons :edit])]]
     [:div.file-details-name [:span (:file/name file)]]
     [:div.file-details-upload-info
      (tr [:file :upload-info] {:author (user-model/user-name (:meta/creator file))
                                :date (format/date (:meta/created-at file))})]
     [:div {:class (<class common-styles/flex-row-space-between)}
      (mapc labeled-data
            [["file-details-number" (tr [:fields :file/number]) (:file/number file)]
             ["file-details-version" (tr [:fields :file/version]) [:span (when old?
                                                                           {:class (<class common-styles/warning-text)})
                                                                   (str "V" (:file/version file)
                                                                        (when old?
                                                                          (str " " (tr [:file :old-version]))))]]
             (if old?
               ["file-details-latest"
                "" [url/Link {:page :file
                              :params {:file (:db/id latest-file)}}
                    (tr [:file :switch-to-latest-version])]]
               ["file-details-status"
                (tr [:fields :file/status])
                (tr-enum (:file/status file))])])]

     ;; preview block (placeholder for now)
     [:div {:class (<class preview-style)}
      (if (str/starts-with? (:file/type file) "image/")
        [:img {:style {:width :auto :height :auto
                       :max-height "250px"
                       :object-fit :contain}
               :src (common-controller/query-url :file/download-file {:file-id (:db/id file)})}]
        "Preview")]

     ;; size, upload new version and download buttons
     [:div {:class (<class common-styles/flex-row-space-between)}
      [labeled-data ["file-details-size" (tr [:fields :file/size]) (format/file-size (:file/size file))]]
      (if replacement-upload-progress
        [LinearProgress {:variant :determinate
                         :value replacement-upload-progress}]
        [:<>
         (when (du/enum= :file.status/draft (:file/status (or latest-file file)))
           [file-upload/FileUploadButton {:on-drop (e! file-controller/->UploadNewVersion file)
                                          :color :secondary
                                          :icon [icons/file-cloud-upload]
                                          :multiple? false}
            (tr [:file :upload-new-version])])])
      [buttons/button-primary {:element "a"
                               :href (common-controller/query-url :file/download-file
                                                                  {:file-id (:db/id file)})
                               :target "_blank"
                               :start-icon (r/as-element
                                             [icons/file-cloud-download])}
       (tr [:file :download])]]

     ;; list previous versions
     (when (seq other-versions)
       [:<>
        [:br]
        [typography/Heading2 (tr [:file :other-versions])]
        [:div.file-table-other-versions
         (mapc other-version-row other-versions)]])]))


(defn- file-edit-dialog [{:keys [e! on-close file]}]
  [panels/modal {:title (tr [:file :edit])
                 :on-close on-close}
   [:div
    [:div {:style {:background-color theme-colors/gray-lighter
                   :width "100%"
                   :height "150px"
                   :margin-bottom "1rem"}}
     (:file/name file)]
    [:div {:style {:display :flex
                   :justify-content :space-between}}
     [:div {:style {:flex-basis "50%"}}
      [buttons/button-warning {:on-click (e! file-controller/->DeleteFile (:db/id file))}
       (tr [:buttons :delete])]]
     [:div
      [buttons/button-secondary {:on-click on-close}
       (tr [:buttons :cancel])]
      [buttons/button-primary {:on-click on-close}
       (tr [:buttons :save])]]]]])

(defn file-page [e! {{file-id :file
                      task-id :task} :params
                     :as app} project breadcrumbs]
  (r/with-let [edit-open? (r/atom false)]
    (let [task (project-model/task-by-id project task-id)
          file (project-model/file-by-id project file-id false)
          old? (nil? file)
          file (or file (project-model/file-by-id project file-id true))
          latest-file (when old?
                        (project-model/latest-version-for-file-id project file-id))]
      [project-navigator-view/project-navigator-with-content
       {:e! e!
        :app app
        :project project
        :breadcrumbs breadcrumbs
        :column-widths [2 8 2]}
       [Grid {:container true}
        [Grid {:item true :xs 3 :xl 2}
         [file-list (:task/files task) (:db/id file)]]
        [Grid {:item true :xs 9 :xl 10}
         [:<>
          (when @edit-open?
            [file-edit-dialog {:e! e! :on-close #(reset! edit-open? false) :file file}])]
         [tabs/details-and-comments-tabs
          {:e! e!
           :app app
           :entity-id (:db/id file)
           :entity-type :file
           :show-comment-form? (not old?)}
          (when file
            [file-details e! file latest-file edit-open?])]]]])))
