(ns teet.file.file-view
  (:require [clojure.string :as str]
            [herb.core :refer [<class]]
            [re-svg-icons.feather-icons :as fi]
            [re-svg-icons.tabler-icons :as ti]
            [reagent.core :as r]
            [teet.common.common-controller :as common-controller]
            [teet.common.common-styles :as common-styles]
            [teet.file.file-controller :as file-controller]
            [teet.file.file-model :as file-model]
            [teet.file.file-style :as file-style]
            [teet.localization :refer [tr tr-enum]]
            [teet.project.project-model :as project-model]
            [teet.project.project-navigator-view :as project-navigator-view]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.buttons :as buttons]
            [teet.ui.file-upload :as file-upload]
            [teet.ui.format :as format]
            [teet.ui.icons :as icons]
            [teet.ui.material-ui :refer [Grid Link LinearProgress IconButton Badge Icon]]
            [teet.ui.panels :as panels]
            [teet.ui.select :as select]
            [teet.ui.tabs :as tabs]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.typography :as typography]
            [teet.ui.url :as url]
            [teet.ui.util :refer [mapc]]
            [teet.ui.form :as form]
            [teet.user.user-model :as user-model]
            [teet.util.datomic :as du]
            [teet.log :as log]
            [teet.project.task-model :as task-model]
            [teet.file.filename-metadata :as filename-metadata]
            [teet.ui.text-field :as text-field]
            [teet.ui.common :as common]
            [goog.functions :as gfunc]
            [teet.util.string :as string]
            [goog.string :as gstr]))


(defn file-identifying-info
  "Show file identifying info: document group, seq# and version."
  [{:file/keys [document-group sequence-number version]}]
  [:strong.file-identifying-info
   (str/join " / "
             (remove nil?
                     [(when document-group
                        (tr-enum document-group))
                      (when sequence-number
                        (str "#" sequence-number))
                      (when version
                        (tr [:file :version] {:num version}))]))])

(defn- file-row-icon-style
  []
  {:margin "0 0.25rem"
   :display :flex
   :justify-content :center})

(defn- base-name-and-suffix [name]
  (let [[_ base-name suffix] (re-matches #"^(.*)\.([^\.]+)$" name)]
    (if base-name
      [base-name (str/upper-case suffix)]
      [name ""])))

(defn- file-row
  [{:keys [link-download? no-link? attached-to actions? comment-action columns delete-action]
    :or {link-download? false
         actions? true
         columns (constantly true)}}
   {id :db/id :file/keys [number version status name] comments :comment/counts :as file}]
  (let [[base-name suffix] (base-name-and-suffix name)
        {:comment/keys [new-comments old-comments]} comments
        seen (:file-seen/seen-at file)]
    [:div {:class (<class common-styles/margin-bottom 0.5)}
     [:div.file-row {:class (<class common-styles/flex-row)}
      [:div.file-row-name {:class [(<class file-style/file-row-name seen)
                                   (<class common-styles/flex-table-column-style 44)]}
       (cond
         ;; if no-link? specified, just show the name text
         no-link?
         base-name

         ;; if link-download? specified, download file
         link-download?
         [Link {:target :_blank
                :href (common-controller/query-url
                       :file/download-file
                       (merge {:file-id id
                               :attached-to attached-to}))}
          base-name]

         ;;  Otherwise link to task file page
         :else
         [url/Link {:page :file :params {:file id}} base-name])]
      (when (columns :number)
        [:div.file-row-number {:class (<class common-styles/flex-table-column-style 10)}
         [:span number]])
      (when (columns :suffix)
        [:div.file-row-suffix {:class (<class common-styles/flex-table-column-style 10)}
         [:span suffix]])
      (when (columns :version)
        [:div.file-row-version {:class (<class common-styles/flex-table-column-style 10)}
         [:span (str "V" version)]])
      (when (columns :status)
        [:div.file-row-status {:class (<class common-styles/flex-table-column-style 13)}
         [:span (tr-enum status)]])
      (when actions?
        [:div.file-row-actions {:class (<class common-styles/flex-table-column-style 13 :flex-end)}
         (when (columns :comment)
           [Badge {:badge-content (+ (or new-comments 0)
                                     (or old-comments 0))
                   :color (if (pos? new-comments)
                            :error
                            :primary)}
            (if comment-action
              [IconButton {:on-click #(comment-action file)}
               [icons/communication-comment]]
              [url/Link {:class ["file-row-action-comments" (<class file-row-icon-style)]
                         :page :file
                         :params {:file id}
                         :query {:tab "comments"}}
               [icons/communication-comment]])])
         (when (columns :download)
           [Link {:class ["file-row-action-download" (<class file-row-icon-style)]
                  :target :_blank
                  :href (common-controller/query-url
                         (if attached-to
                           :file/download-attachment
                           :file/download-file)
                         (merge
                          {:file-id id}
                          (when attached-to
                            {:attached-to attached-to})))}
            [icons/file-cloud-download]])

         (when (and (columns :delete) delete-action)
           [buttons/delete-button-with-confirm
            {:action #(delete-action file)
             :trashcan? true}])])]
     (when (columns :meta)
       [:div.file-row-meta {:class (<class file-style/file-row-meta)}
        (tr [:file :upload-info]
            {:author (user-model/user-name (:meta/creator file))
             :date (format/date-time (:meta/created-at file))})])]))

(def ^:private sorters
  {"meta/created-at" [(juxt :meta/created-at :file/name) >]
   "file/name"       [:file/name <]
   "file/type"       [(comp file-model/filename->suffix :file/name) <]
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

(defn file-search
  "Given original parts and files passes searched files and parts to component view"
  [files parts file-component]                       ;; if files/parts change
  (r/with-let [search-term (r/atom "")
               on-change #(let [val (-> % .-target .-value)]
                            (reset! search-term val))
               selected-part (r/atom nil)
               change-part #(do (reset! selected-part %))]
    [:div
     [:div {:class (<class common-styles/flex-row)}
      [TextField {:value @search-term
                  :style {:margin-right "1rem"}
                  :placeholder (tr [:files :filter-file-listing])
                  :start-icon icons/action-search
                  :on-change on-change}]
      [:div {:style {:max-width "200px"}}
       [select/form-select {:items (concat [{:file.part/name (tr [:file-upload :general-part])
                                             :file.part/number 0}]
                                           parts)
                            :format-item (fn [{:file.part/keys [name number]}]
                                           (gstr/format "%s #%02d" name number))
                            :on-change change-part
                            :value @selected-part
                            :empty-selection-label (tr [:file :all-parts])
                            :show-empty-selection? true}]]]
     [file-component
      (filterv
        (fn [{:file/keys [name original-name] :as file}]
          (and
            (string/contains-words? (str name " " original-name) @search-term)
            file))
        files)
      (cond                                                 ;; This feels hacky
        (= (:file.part/number @selected-part) 0)
        []
        @selected-part
        [@selected-part]
        :else
        parts)]]))

(defn file-comments-link
  [{comment-counts :comment/counts :as file}]
  (let [{:comment/keys [new-comments old-comments]} comment-counts
        comments? (not (zero? (+ new-comments old-comments)))
        new-comments? (not (zero? new-comments))]
    [url/Link {:class (<class file-style/file-comments-link-style new-comments?)
               :page :file
               :params {:file (:db/id file)}
               :query {:tab "comments"}}
     (if comments?
       (if new-comments?
           [:span [common/comment-count-chip file] (tr [:common :new])]
           [:span {:style {:text-transform :lowercase}}
            [common/comment-count-chip file] (tr [:document :comments])])
       [:span (tr [:land-modal-page :no-comments])])]))

(defn file-row2
  [{e! :e!
    no-link? :no-link?
    delete-action :delete-action
    attached-to :attached-to} {:file/keys [document-group sequence-number status version] :as file}]
  (let [{:keys [description extension]} (filename-metadata/name->description-and-extension (:file/name file))]
    [:div {:class (<class file-style/file-row-style)}
     [:div {:class (<class file-style/file-base-column-style)}
      [:div {:class (<class file-style/file-icon-container-style)}
       [fi/file {:style {:color theme-colors/primary}}]]    ;; This icon could be made dynamic baseed on the file-type
      [:div
       [:div {:class [(<class common-styles/flex-align-center)
                      (<class common-styles/margin-bottom 0.5)]}

        (if no-link?
          [:p {:class (<class file-style/file-list-entity-name-style)}
           description]
          [url/Link {:class (<class file-style/file-list-entity-name-style)
                     :page :file :params {:file (:db/id file)}}
           description])
        [typography/SmallGrayText {:style {:text-transform :uppercase}}
         extension]]
       [:div.file-info
        [:strong (str/join " / " (filterv some?
                                         [(when document-group
                                            (tr-enum document-group))
                                          (when sequence-number
                                            (str "#" sequence-number))
                                          (when version
                                            (tr [:file :version] {:num version}))]))]
        [typography/SmallText
         (tr [:file :upload-info]
             {:date (format/date-time (:meta/created-at file))
              :author (user-model/user-name
                        (:meta/creator file))})]
        (when status
          [typography/SmallBoldText (tr-enum status)])]]]
     [:div {:class (<class file-style/file-actions-column-style)}
      [:div {:style {:display :flex}}
       #_[file-upload/FileUploadButton
        {:on-drop (e! file-controller/->UploadNewVersion file)
         :drag-container-id (str (:db/id file) "-file-row")
         :color :primary
         :multiple? false}
        [icons/file-cloud-upload-outlined]]                 ;;  TODO this should be done after file edit modal is working
       [IconButton
        {:target :_blank
         :size :small
         :style {:margin-left "0.25rem"}
         :href (common-controller/query-url
                 (if attached-to
                   :file/download-attachment
                   :file/download-file)
                 (merge
                   {:file-id (:db/id file)}
                   (when attached-to
                     {:attached-to attached-to})))}
        [icons/file-cloud-download-outlined {:style {:color theme-colors/primary}}]]
       (when delete-action
         [buttons/delete-button-with-confirm
          {:action #(delete-action file)
           :trashcan? true}])]
      (when (:comment/counts file)
        [file-comments-link file])]]))

(defn file-list2
  [opts files]
  [:div {:class (<class common-styles/margin-bottom 1.5)}
   (mapc (r/partial file-row2 opts) files)])

(defn file-table
  ([files] (file-table {} files))
  ([{:keys [filtering?]
     :or {filtering? true} :as opts} files]
   (r/with-let [items-for-sort-select (sort-items)
                filter-atom (r/atom "")
                sort-by-atom (r/atom (first items-for-sort-select))]
     [:<>
      (when filtering?
        [file-filter-and-sorter
         filter-atom
         sort-by-atom
         items-for-sort-select])
      [:div.file-table-files
       (->> files
            (filtered-by @filter-atom)
            (sorted-by @sort-by-atom)
            (mapc (r/partial file-row opts)))]])))

(defn file-upload-button []
  [buttons/button-primary {:start-icon (r/as-element
                                        [icons/file-cloud-upload])}
   (tr [:task :upload-files])])

(defn- file-icon-style []
  {:display :inline-block
   :margin-right "0.25rem"
   :position :relative
   :top "6px"})

(defn file-icon [{class :class
                  :file/keys [type]
                  :as file}]
  [:div {:class (if class
                  [class (<class file-icon-style)]
                  (<class file-icon-style))}
   (cond
     (file-model/image? file)
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

(defn- file-details [e! {:keys [replacement-upload-progress] :as file} latest-file edit-open? show-preview? can-replace-file?]
  (log/info "file-details: file map is" (pr-str file))
  (let [old? (some? latest-file)
        other-versions (if old?
                         (into [latest-file]
                               (filter #(not= (:db/id file) (:db/id %))
                                       (:versions latest-file)))
                         (:versions file))]
    [:div.file-details
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

     (if @show-preview?
       [:div
        [:div {:class (<class preview-style)}
         (if (file-model/image? file)
           [:img {:style {:width :auto :height :auto
                          :max-height "250px"
                          :object-fit :contain}
                  :src (common-controller/query-url :file/download-file {:file-id (:db/id file)})}]
           "Preview")]
        [buttons/button-primary {:on-click #(reset! show-preview? false)}
          (tr [:file :hide-preview])]]
       ;; else
       [buttons/button-primary {:on-click #(reset! show-preview? true)}
        (tr [:file :show-preview])])

     ;; size, upload new version and download buttons
     [:div {:class (<class common-styles/flex-row-space-between)}
      [labeled-data ["file-details-size" (tr [:fields :file/size]) (format/file-size (:file/size file))]]
      (if replacement-upload-progress
        [LinearProgress {:variant :determinate
                         :value replacement-upload-progress}]
        [:<>
         (when (and can-replace-file?
                    (nil? latest-file)
                    (du/enum= :file.status/draft (:file/status file)))
           [:div#file-details-upload-replacement
            [file-upload/FileUploadButton
             {:on-drop (e! file-controller/->UploadNewVersion file)
              :drag-container-id "file-details-upload-replacement"
              :color :secondary
              :icon [icons/file-cloud-upload]
              :multiple? false}
             (tr [:file :upload-new-version])]])])
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
         [file-table other-versions]]])]))

(defn file-part-heading
  [{heading :heading
       number :number} opts]
  [:div {:style {:margin-bottom "1.5rem"
                 :display :flex
                 :justify-content :space-between}}
   [:div {:class (<class common-styles/flex-row-end)}
    [typography/Heading2 {:style {:margin-right "0.5rem"}}
     heading]
    (when number
      [typography/GreyText (goog.string/format "#%02d" number)])]
   [:div
    (when-let [action-comp (:action opts)]
      action-comp)]])

(defn no-files
  []
  [typography/GreyText {:class [(<class typography/grey-text-style)
                                (<class common-styles/flex-row)
                                (<class common-styles/margin-bottom 1)]}
   [ti/file {:style {:margin-right "0.5rem"}}]
   [:span (tr [:file :no-files])]])

(defn- file-edit-name [{:keys [value on-change]}]
  (let [[name original-name] value
        {:keys [description extension]} (filename-metadata/name->description-and-extension name)]
    [:<>
     [TextField {:label (tr [:file-upload :description])
                 :value description
                 :on-change (fn [e]
                              (let [descr (-> e .-target .-value)]
                                (on-change [(str descr "." extension)
                                            original-name])))
                 :end-icon [text-field/file-end-icon extension]}]
     (when-not (str/blank? original-name)
       [:div
        [typography/SmallGrayText {}
         [:b (tr [:file-upload :original-filename] {:name original-name})]]])]))

(defn- file-edit-dialog [{:keys [e! on-close file parts]}]
  (r/with-let [form-data (r/atom (update file :file/part
                                         (fn [p]
                                           ;; Part in file only has id,
                                           ;; fetch it from parts list
                                           (some #(when (= (:db/id p)
                                                           (:db/id %))
                                                    %)
                                                 parts))))
               change-event (form/update-atom-event
                             form-data
                             #(file-controller/file-updated (merge %1 %2)))
               save-event #(file-controller/->ModifyFile @form-data on-close)
               delete (file-controller/->DeleteFile (:db/id file))
               cancel-event (form/callback-event on-close)]
    [panels/modal {:title (tr [:file :edit])
                   :on-close on-close}
     [:div
      [form/form {:value @form-data
                  :e! e!
                  :on-change-event change-event
                  :save-event save-event
                  :cancel-event cancel-event
                  :delete delete}
       ^{:attribute :file/part}
       [select/form-select {:items parts
                            :format-item #(gstr/format "%s #%02d"
                                                       (:file.part/name %)
                                                       (:file.part/number %))
                            :empty-selection-label (tr [:file-upload :general-part])
                            :show-empty-selection? true}]
       ^{:attribute :file/document-group :xs 8}
       [select/select-enum {:e! e!
                            :attribute :file/document-group}]
       ^{:attribute :file/sequence-number :xs 4}
       [TextField {:type :number :label "#"
                   :disabled (nil? (:file/document-group @form-data))}]

       ^{:attribute [:file/name :file/original-name]}
       [file-edit-name {}]]]]))

(defn- file-tab-wrapper [{:file.part/keys [name number]} tab-links]
  [:div {:class (<class common-styles/flex-row-space-between)
         :style {:align-items :center}}
   [typography/Heading3 (gstr/format "%s #%02d" name number)]
   [:div tab-links]])

(defn file-page [e! {{file-id :file} :params} _project]
  ;; Update the file seen timestamp for this user
  (e! (file-controller/->UpdateFileSeen file-id))
  (let [edit-open? (r/atom false)]
    (r/create-class
      {:component-did-update
       (fn [this [_ _ {{prev-file-id :file} :params} _ _ :as _prev-args] _ _]
         (let [[_ _ {{curr-file-id :file} :params} _ _ :as _curr-args] (r/argv this)]
           (when (not= curr-file-id prev-file-id)
             (e! (file-controller/->UpdateFileSeen curr-file-id)))))

       :reagent-render
       (fn [e! {{file-id :file
                 task-id :task} :params
                :as app} project]
         (let [task (project-model/task-by-id project task-id)
               file (project-model/file-by-id project file-id false)
               show-preview? (r/atom (and file (file-model/image? file)))
               old? (nil? file)
               file (or file (project-model/file-by-id project file-id true))
               latest-file (when old?
                             (project-model/latest-version-for-file-id project file-id))
               {:keys [description extension]}
               (filename-metadata/name->description-and-extension (:file/name file))
               file-part (or (some #(when (= (:db/id %)
                                             (get-in file [:file/part :db/id]))
                                      %)
                                   (:file.part/_task task))
                             {:file.part/name (tr [:file-upload :general-part])
                              :file.part/number 0})]
           [project-navigator-view/project-navigator-with-content
            {:e! e!
             :app app
             :project project
             :column-widths [3 9 :auto]
             :show-map? false}
            [Grid {:container true}
             [Grid {:item true :xs 3 :xl 2}
              [file-list (:task/files task) (:db/id file)]]
             [Grid {:item true :xs 9 :xl 10}
              [:<>
               (when @edit-open?
                 [file-edit-dialog {:e! e!
                                    :on-close #(reset! edit-open? false)
                                    :file file
                                    :parts (:file.part/_task task)}])]
              [typography/Heading2
               [:div {:class (<class common-styles/flex-row)}
                description
                [typography/GreyText {:style {:margin-left "0.5rem"}}
                 (str/upper-case extension)]
                [:div {:style {:flex-grow 1
                               :text-align :end}}
                 [buttons/button-secondary {:on-click #(reset! edit-open? true)}
                  (tr [:buttons :edit])]]]]
              [file-identifying-info file]
              [tabs/details-and-comments-tabs
               {:e! e!
                :app app
                :entity-id (:db/id file)
                :entity-type :file
                :show-comment-form? (not old?)
                :tab-wrapper (r/partial file-tab-wrapper file-part)}
               (when file
                 [file-details e! file latest-file edit-open? show-preview? (task-model/can-submit? task)])]]]]))})))
