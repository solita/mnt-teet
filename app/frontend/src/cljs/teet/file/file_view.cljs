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
            [teet.ui.material-ui :refer [Grid Link LinearProgress IconButton Badge CircularProgress
                                         DialogActions DialogContentText CircularProgress]]
            [teet.ui.panels :as panels]
            [teet.ui.select :as select]
            [teet.ui.tabs :as tabs]
            [teet.ui.text-field :refer [TextField] :as text-field]
            [teet.ui.typography :as typography]
            [teet.ui.url :as url]
            [teet.ui.util :refer [mapc]]
            [teet.ui.form :as form]
            [teet.user.user-model :as user-model]
            [teet.util.datomic :as du]
            [teet.project.task-model :as task-model]
            [teet.file.filename-metadata :as filename-metadata]
            [teet.ui.common :as common]
            [goog.string :as gstr]
            [teet.authorization.authorization-check :refer [when-authorized]]
            [teet.comments.comments-view :as comments-view]))



(defn file-identifying-info
  "Show file identifying info: document group, seq# and version."
  [land-acquisition? {:file/keys [document-group sequence-number version]}]
  [:strong.file-identifying-info
   (str/join " / "
             (remove nil?
                     [(when document-group
                        (tr-enum document-group))
                      (when sequence-number
                        (str (tr [:fields (if land-acquisition?
                                            :file/position-number
                                            :file/sequence-number)]) sequence-number))
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
   "file/name"       [(comp str/lower-case :file/name) <]
   "file/type"       [(comp file-model/filename->suffix :file/name) <]
   "file/status"     [(juxt :file/status :file/name) <]})

(defn sort-items
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

(defn file-sorter [sort-by-atom items]
  [select/select-with-action
   {:id "file-sort-select"
    :name "file-sort"
    :show-label? false
    :value @sort-by-atom
    :items items
    :on-change #(reset! sort-by-atom %)}])

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

(defn- sorted-by [{:keys [value] :as sort-by-value} files]
  (if-not sort-by-value
    files
    (let [[sort-fn comparator] (sorters value)]
      (sort-by sort-fn comparator files))))

(defn file-search
  "Given original parts and files passes searched files and parts to component view"
  [files parts file-component]                       ;; if files/parts change
  (r/with-let [search-term (r/atom "")
               on-change #(let [val (-> % .-target .-value)]
                            (reset! search-term val))
               selected-part (r/atom nil)
               change-part #(do (reset! selected-part %))]
    [:div
     [:div {:class [(<class common-styles/flex-row)
                    (<class common-styles/margin-bottom 1)]}
      [TextField {:value @search-term
                  :style {:margin-right "1rem"
                          :flex 1}
                  :placeholder (tr [:file :filter-file-listing])
                  :start-icon icons/action-search
                  :on-change on-change}]
      [:div {:style {:flex 1}}
       [select/form-select {:items (concat [{:file.part/name (tr [:file-upload :general-part])
                                             :file.part/number 0}]
                                           parts)
                            :format-item (fn [{:file.part/keys [name number]}]
                                           (gstr/format "%s #%02d" name number))
                            :on-change change-part
                            :value @selected-part
                            :empty-selection-label (tr [:file :all-parts])
                            :show-empty-selection? true}]]]
     (conj
       file-component
       (filterv
         (fn [{:file/keys [name] :as file}]
           (and
             (str/includes? (str/lower-case name) (str/lower-case @search-term))
             file))
         files)
       parts
       @selected-part)]))

(defn file-comments-text
  [{:comment/keys [counts] :as file}]
  (let [{:comment/keys [new-comments old-comments]} counts
        amount (+ new-comments
                 old-comments)]
    [:span {:style {:text-transform :lowercase}}
     [common/comment-count-chip file]
     (if (= amount 1)
       (tr [:comment :comment])
       (tr [:comment :comments]))]))

(defn file-comments-button
  [button-opts file]
  [buttons/link-button (merge button-opts
                              {:style {:pointer :cursor}})
   [file-comments-text file]])

(defn file-comments-link
  [file]
  [url/Link {:page :file
             :params {:file (:db/id file)
                      :activity (get-in file [:task/_files 0 :activity/_tasks 0 :db/id])
                      :task (get-in file [:task/_files 0 :db/id])}
             :query {:tab "comments"}}
   [file-comments-text file]])

(defn- replace-file-form [e! project-id task file form close!]
  (let [{:keys [description extension]} (filename-metadata/name->description-and-extension
                                          (:file/name file))]
    [panels/modal {:max-width "lg"
                   :on-close close!
                   :title [:<>
                           (tr [:file :replace-dialog-title])
                           [typography/GreyText
                            (str description "." extension)]]}
     [:div

      [common/info-box {:title (tr [:file :replace-dialog-info-title])
                        :content (tr [:file :replace-dialog-info-text])}]

      [form/form {:e! e!
                  :value form
                  :on-change-event file-controller/->UpdateFilesForm
                  :save-event #(file-controller/->UploadNewVersion
                                 file
                                 (get-in form [:task/files 0]))
                  :cancel-fn close!}
       ^{:attribute :task/files
         :validate (fn [files]
                     (or
                       (some some?
                             (map (partial file-upload/validate-file e! project-id task) files))
                       (when (not= 1 (count files))
                         "expect exactly one file")))}
       [file-upload/files-field {:e! e!
                                 :project-id project-id
                                 :task task
                                 :single? true}]]]]))

(defn file-replacement-modal-button
  [{:keys [e! task file small?]}]
  ;; The progress? and open-atom atoms are expected to go false automatically after uploading happens
  ;; since the app state is either refreshed or navigation happens
  (r/with-let [selected-file (r/atom nil)
               progress? (r/atom false)
               select-file #(reset! selected-file (first %))
               open-atom (r/atom false)
               open #(reset! open-atom true)
               close #(do (reset! open-atom false)
                          (reset! selected-file nil))]
    [:div#file-details-upload-replacement

     [panels/modal {:title (tr [:file-upload :replace-file-modal-title])
                    :open-atom open-atom
                    :actions [DialogActions
                              [buttons/button-secondary
                               {:on-click close
                                :disabled @progress?
                                :id (str "confirmation-cancel")}
                               (tr [:buttons :cancel])]
                              [buttons/button-primary
                               {:id (str "confirmation-confirm")
                                :disabled @progress?
                                :on-click #(do (e! (file-controller/->UploadNewVersion file @selected-file))
                                               (reset! progress? true))}
                               (tr [:file-upload :replace-file-confirm])]]}

      [DialogContentText
       (if @progress?
         [:div {:style {:display :flex
                        :justify-content :center}}
          [CircularProgress]]
         (tr [:file-upload :replace-file-modal-body] {:file-full-name (:file/full-name file)
                                                      :selected-file-name (some-> @selected-file
                                                                                  :file-object
                                                                                  file-model/file-info
                                                                                  :file/name)}))]]
     [when-authorized
      :file/upload task
      [file-upload/FileUpload
       {:on-drop #(do (select-file %)
                      (open))
        :drag-container-id "file-details-upload-replacement"
        :color :secondary
        :icon [icons/file-cloud-upload]
        :multiple? false}


       (if small?
         [IconButton {:component :span
                      :size :small}
          [icons/file-cloud-upload-outlined {:style {:color theme-colors/primary}}]]
         [buttons/button-secondary
          {:component :span
           :start-icon (r/as-element [icons/file-cloud-upload-outlined])}
          (tr [:file :upload-new-version])])]]]))

(defn file-row2
  [{:keys [link-to-new-tab? no-link?
           allow-replacement-opts delete-action
           attached-to land-acquisition?
           comments-link? actions?
           column-widths]
    :or {comments-link? true
         actions? true
         column-widths [3 1]}}
   {:file/keys [status] :as file}]
  (let [{:keys [description extension]} (filename-metadata/name->description-and-extension (:file/name file))
        [base-column-width action-column-width] column-widths]
    [:div {:class (<class file-style/file-row-style)}
     [:div {:class (<class file-style/file-base-column-style
                           base-column-width actions?)}
      [:div {:class (<class file-style/file-icon-container-style)}
       [fi/file {:style {:color theme-colors/primary}}]]    ;; This icon could be made dynamic baseed on the file-type
      [:div {:style {:min-width 0}}
       [:div {:class [(<class common-styles/flex-align-center)
                      (<class common-styles/margin-bottom 0.5)]}
        (if no-link?
          [:p {:class (<class file-style/file-list-entity-name-style)
               :title description}
           description]
          [url/Link (merge
                     {:title description
                      :class (<class file-style/file-list-entity-name-style)
                      :page :file :params {:file (:db/id file)
                                            :activity (get-in file [:task/_files 0 :activity/_tasks 0 :db/id])
                                            :task (get-in file [:task/_files 0 :db/id])}}
                      (when link-to-new-tab?
                        {:target :_blank}))
           description])
        [typography/SmallGrayText {:style {:text-transform :uppercase
                                           :white-space :nowrap}}
         extension]]
       [:div.file-info
        [file-identifying-info land-acquisition? file]
        [typography/SmallText
         (if-let [modified-at (:meta/modified-at file)]
           (tr [:file :edit-info]
               {:date (format/date-time modified-at)
                :author (user-model/user-name
                          (:meta/modifier file))})
           (tr [:file :upload-info]
                 {:date (format/date-time (:meta/created-at file))
                  :author (user-model/user-name
                            (:meta/creator file))}))]
        (when status
          [typography/SmallBoldText (tr-enum status)])]]]
     (when actions?
       [:div {:class (<class file-style/file-actions-column-style action-column-width)}
        [:div {:style {:display :flex}}
         (when allow-replacement-opts
           (with-meta
             [file-replacement-modal-button (merge allow-replacement-opts
                                                   {:file file
                                                    :small? true})]
             {:key (str "file-replace-button-" (:db/id file))}))
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
        (when (and comments-link? (:comment/counts file))
          [file-comments-link file])])]))

(defn file-list2
  [{:keys [sort-by-value] :as opts} files]
  [:div {:class (<class common-styles/margin-bottom 1.5)}
   (mapc (r/partial file-row2 opts)
         (sorted-by
           sort-by-value
           files))])

(defn file-list2-with-search
  [opts files]
  (r/with-let [items-for-sort-select (sort-items)
               filter-atom (r/atom "")
               sort-by-atom (r/atom (first items-for-sort-select))]
    [:<>
     [file-filter-and-sorter
      filter-atom
      sort-by-atom
      items-for-sort-select]
     [file-list2 (assoc opts :sort-by-value @sort-by-atom)
      (->> files
           (filtered-by @filter-atom))]]))

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


(defn file-list-entry-style
  []
  {:padding "0.5rem 0"
   :border-top (str "2px solid " theme-colors/black-coral-1)})

(defn- file-list [parts files current-file-id]
  (let [parts (sort-by :file.part/number
                       (concat [{:file.part/number 0 :file.part/name (tr [:file-upload :general-part])}]
                               parts))]
    [:div.file-list {:style {:margin-right "0.5rem"
                             :border-right (str "2px solid " theme-colors/black-coral-1)}}
     (mapc
      (fn [{part-id :db/id :file.part/keys [name number]}]
        (let [files (filter (fn [{part :file/part}]
                              (or (and (nil? part)
                                       (zero? number))
                                  (= part-id (:db/id part))))
                            files)]
          (when (seq files)
            [:<>
             [:div {:style {:padding "1rem 0"}
                    :class (<class common-styles/flex-row-end)}
              [typography/Heading2 name]
              [typography/GreyText {:style {:padding-left "0.25rem"}} (str "#" number)]]
             [:div
              (mapc (fn [{id :db/id :file/keys [name version number status] :as f}]
                      (let [{:keys [description extension]}
                            (filename-metadata/name->description-and-extension name)
                            active? (= current-file-id id)]
                        [:div.file-list-entry {:class (<class file-list-entry-style)}
                         [:div {:class (<class common-styles/flex-row-center)}
                          [file-icon (assoc f :class "file-list-icon")]
                          [:div.file-list-name {:class (<class file-style/file-name-truncated)
                                                :title description}
                           (if active?
                             [:strong description]
                             [url/Link {:page :file
                                        :params {:file id}
                                        :class "file-list-name"}
                              description])]]
                         [:div {:style {:font-size "12px" :display :flex
                                        :justify-content :space-between
                                        :margin "0 1rem 1rem 1rem"}}
                          [:span.file-list-suffix {:class (<class file-list-field-style)}
                           (if extension
                             (str/upper-case extension)
                             "")]
                          [:span.file-list-number {:class (<class file-list-field-style)} number]
                          [:span.file-list-version {:class (<class file-list-field-style)} (str "V" version)]
                          (when status
                            [:span.file-list-status {:class (<class file-list-field-style)} (tr-enum status)])]]))
                    files)]])))
      parts)]))

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

(defn- edited [{:meta/keys [creator created-at modifier modified-at]}]
  (if modified-at
    [modifier modified-at]
    [creator created-at]))

(defn- file-details [e! app project-id task {:keys [replacement-upload-progress] :as file}
                     latest-file can-replace-file? replace-form]
  (r/with-let [selected-file (r/atom nil)
               select-file #(reset! selected-file (if (= @selected-file %)
                                                    nil
                                                    %))]
    (let [old? (some? latest-file)
          other-versions (if old?
                           (into [latest-file]
                                 (filter #(not= (:db/id file) (:db/id %))
                                         (:versions latest-file)))
                           (:versions file))]
      ^{:key (str (:db/id file))}
      [:div.file-details
       [:div {:class (<class common-styles/margin-bottom 1)}
        [:div.file-details-full-name
         [typography/BoldGreyText
          (str (tr [:file :full-name]) ": " (:file/full-name file))]]
        [:div.file-details-original-name
         [typography/GreyText
          [:strong (str (tr [:fields :file/original-name]) ": ")]
          [:span (:file/original-name file)]]]

        (let [first-version (or (last other-versions) file)
              [edited-by edited-at :as edited?]
              (if (not= first-version file)
                (edited file)
                (when (:meta/modified-at first-version)
                  (edited first-version)))]
          [:<>
           [:div.file-details-upload-info
            (tr [:file :upload-info] {:author (user-model/user-name (:meta/creator first-version))
                                      :date (format/date-time (:meta/created-at first-version))})]

           (when edited?
             [:div.file-details-edit-info
              (tr [:file :edit-info] {:author (user-model/user-name edited-by)
                                      :date (format/date-time edited-at)})])])]



       ;; size, upload new version and download buttons
       [:div {:class (<class common-styles/flex-row-space-between)}
        [common/labeled-data {:class "file-details-size"
                              :label (tr [:fields :file/size])
                              :data (format/file-size (:file/size file))}]
        (if replacement-upload-progress
          [LinearProgress {:variant :determinate
                           :value replacement-upload-progress}]
          [:<>
           (when (and can-replace-file?
                      (nil? latest-file)
                      (du/enum= :file.status/draft (:file/status file)))
             [file-replacement-modal-button {:e! e!
                                             :project-id project-id
                                             :task task
                                             :file file
                                             :replace-form replace-form}])])
        [buttons/button-primary {:element "a"
                                 :href (common-controller/query-url :file/download-file
                                                                    {:file-id (:db/id file)})
                                 :target "_blank"
                                 :start-icon (r/as-element
                                               [icons/file-cloud-download])}
         (tr [:file :download])]]

       (when (file-model/image? file)
         [:div {:class (<class preview-style)}
          [:img {:style {:width :auto :height :auto
                         :max-height "250px"
                         :object-fit :contain}
                 :src (common-controller/query-url
                        :file/thumbnail
                        {:file-id (:db/id file)
                         :size 250})}]])

       ;; list previous versions
       (when (seq other-versions)
         [:div {:class (<class common-styles/margin-bottom 1)}
          [:br]
          [typography/Heading2 (tr [:file :other-versions])]
          (mapc (fn [file]
                  [:div
                   [:div {:class (<class common-styles/flex-row)
                          :style {:padding "0.5rem 0"
                                  :justify-content :space-between
                                  :border-top (str "1px solid " theme-colors/gray-lighter)}}
                    [Link {:target :_blank
                           :href (common-controller/query-url :file/download-file
                                                              {:file-id (:db/id file)})}
                     (tr [:file :version]
                         {:num (:file/version file)})]
                    [:div {:class (<class common-styles/flex-row)}
                     [:span {:style {:margin "0 0.5rem"}}
                      (format/date (:meta/created-at file))]

                     [:div {:class (<class common-styles/no-break)}
                      [file-comments-button {:on-click #(select-file file)}
                       file]]]]])
                other-versions)])
       [:div
        (when @selected-file
          [:<>
           [typography/Heading3 {:class (<class common-styles/margin-bottom 1)}
            (tr [:comment :comments-for] {:file-name (str (:file/name @selected-file) " / "
                                                          (tr [:file :version]
                                                              {:num (:file/version @selected-file)}))})]
           [comments-view/lazy-comments {:e! e!
                                         :app app
                                         :after-comment-deleted-event common-controller/->Refresh
                                         :after-comment-list-rendered-event common-controller/->Refresh
                                         :entity-type :file
                                         :entity-id (:db/id @selected-file)
                                         :show-comment-form? false}]])]])))

(defn file-part-heading
  [{heading :heading
       number :number} opts]
  [:div {:style {:margin-bottom "1.5rem"
                 :display :flex
                 :justify-content :space-between}}
   [:div {:class (<class common-styles/flex-row-end)
          :style {:margin-right "0.5rem"}}
    [typography/Heading2 {:style {:margin-right "0.5rem"}}
     heading]
    (when number
      [typography/GreyText
       {:style {:white-space :nowrap}}
       (goog.string/format "#%02d" number)])]
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

(defn- file-edit-dialog [{:keys [e! on-close file parts activity]}]
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
       ^{:attribute :file/sequence-number :xs 4
         :validate (fn [n]
                     (when (and (not (str/blank? n))
                                (file-upload/validate-seq-number
                                 {:file/sequence-number (js/parseInt n)}))
                       (tr [:file-upload :invalid-sequence-number])))}
       [TextField {:type :number
                   :label (if (du/enum= :activity.name/land-acquisition
                                        (:activity/name activity))
                            (tr [:fields :file/position-number])
                            (tr [:fields :file/sequence-number]))
                   :disabled (nil? (:file/document-group @form-data))}]

       ^{:attribute [:file/name :file/original-name]}
       [file-edit-name {}]]]]))

(defn- file-tab-wrapper [{:file.part/keys [name number]} tab-links]
  [:div {:class (<class common-styles/flex-row-space-between)
         :style {:align-items :center}}
   [typography/Heading3 (gstr/format "%s #%02d" name number)]
   [:div tab-links]])

(defn file-page [e! {{file-id :file
                      project-id :project} :params} _project]
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
                 task-id :task
                 activity-id :activity} :params
                :as app} project]
         (let [activity (project-model/activity-by-id project activity-id)
               task (project-model/task-by-id project task-id)
               file (project-model/file-by-id project file-id false)]
           (if (nil? file)
             [CircularProgress {}]
             (let [old? (nil? file)
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
                 :column-widths [3 :auto :auto]
                 :show-map? false}
                [Grid {:container true}
                 [Grid {:item true :xs 3}
                  [file-list (:file.part/_task task) (:task/files task) (:db/id file)]]
                 [Grid {:item true :xs 9}
                  [:<>
                   (when @edit-open?
                     [file-edit-dialog {:e! e!
                                        :on-close #(reset! edit-open? false)
                                        :activity activity
                                        :file file
                                        :parts (:file.part/_task task)}])]

                  [:div {:class (<class common-styles/flex-row)}
                   [typography/Heading2
                    [:span description]
                    [typography/GreyText {:style {:display :inline-block
                                                  :margin "0 0.5rem"}}
                     (str/upper-case extension)]]
                   [:div {:style {:flex-grow 1
                                  :text-align :end}}
                    [buttons/button-secondary {:on-click #(reset! edit-open? true)}
                     (tr [:buttons :edit])]]]
                  [file-identifying-info (du/enum= :activity.name/land-acquisition
                                                   (:activity/name activity))
                   file]
                  [typography/SmallText [:strong (tr-enum (:file/status file))]]
                  ^{:key (str (:db/id file) "-details-and-comments")}
                  [tabs/details-and-comments-tabs
                   {:e! e!
                    :app app
                    :after-comment-list-rendered-event common-controller/->Refresh
                    :comment-link-comp [file-comments-link file]
                    :after-comment-added-event common-controller/->Refresh
                    :entity-id (:db/id file)
                    :entity-type :file
                    :show-comment-form? (not old?)
                    :tab-wrapper (r/partial file-tab-wrapper file-part)}
                   (when file
                     [file-details e! app project-id task file latest-file
                      (task-model/can-submit? task)
                      (:files-form project)])]]]]))))})))
