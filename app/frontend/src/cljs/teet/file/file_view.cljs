(ns teet.file.file-view
  (:require [clojure.string :as str]
            [goog.string :as gstr]
            [herb.core :refer [<class] :as herb]
            [re-svg-icons.feather-icons :as fi]
            [re-svg-icons.tabler-icons :as ti]
            [reagent.core :as r]
            [teet.authorization.authorization-check :refer [when-authorized]]
            [teet.comments.comments-view :as comments-view]
            [teet.common.common-controller :as common-controller]
            [teet.common.common-styles :as common-styles]
            [teet.file.file-controller :as file-controller]
            [teet.file.file-model :as file-model]
            [teet.file.file-style :as file-style]
            [teet.file.filename-metadata :as filename-metadata]
            [teet.localization :refer [tr tr-enum]]
            [teet.project.project-model :as project-model]
            [teet.project.project-navigator-view :as project-navigator-view]
            [teet.project.task-model :as task-model]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.buttons :as buttons]
            [teet.ui.common :as common]
            [teet.ui.file-upload :as file-upload]
            [teet.ui.form :as form]
            [teet.ui.format :as format]
            [teet.ui.icons :as icons]
            [teet.ui.material-ui :refer [Grid LinearProgress IconButton Collapse CircularProgress
                                         DialogActions DialogContentText CircularProgress]]
            [teet.ui.panels :as panels]
            [teet.ui.select :as select]
            [teet.ui.tabs :as tabs]
            [teet.ui.text-field :refer [TextField] :as text-field]
            [teet.ui.typography :as typography]
            [teet.ui.url :as url]
            [teet.ui.util :refer [mapc]]
            [teet.user.user-model :as user-model]
            [teet.util.datomic :as du]
            [teet.ui.common :as common-ui]
            [teet.ui.container :as container]
            [taoensso.timbre :as log]
            [teet.ui.icons :as icons]
            [teet.util.collection :as cu]))



(defn file-identifying-info
  "Show file identifying info: document group, seq# and version."
  [land-acquisition? {:file/keys [document-group group-name sequence-number version]}]
  [:strong.file-identifying-info {:data-version version
                                  :class (<class common-styles/margin-bottom 0.2)}
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

(def ^:private sorters
  {"meta/created-at" [(juxt :file/group-code :meta/created-at :file/name) >]
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

(defn get-document-groups
  "Returns the document groups for the file list"
  [files]
  (let [outs (mapv (fn [x]
                     (-> x
                         (assoc :file/group-code (get-in x [:file/document-group :filename/code]))
                         (assoc :file/group-name (get-in x [:file/document-group :db/ident]))
                         (dissoc :file/document-group))) files)]
    outs))

(defn- get-parts-and-groups
  [files parts]
  (let [all-parts (mapv (fn [x] {:item-name (gstr/format "%s #%02d" (:file.part/name x) (:file.part/number x))
                                 :item-value (:file.part/number x)}) (into [] parts))
        all-groups (distinct (mapv (fn [x] {:item-name (tr-enum (if (nil? (:file/group-name x)) :file.document-group/ungrouped (:file/group-name x)))
                                            :item-value (if (nil? (:file/group-name x)) :file.document-group/ungrouped (:file/group-name x))}) (get-document-groups files)))]
    [(when (some? all-parts) {:group-label "Parts" :group-items (concat [{:item-name (str (tr [:file-upload :general-part]) " #00")
                                                                       :item-value 0}]
                                                                            all-parts)})
    (when (some? all-groups) {:group-label "Document groups" :group-items all-groups})]))

(defn- str->int [s] (when (= s (str (js/parseInt s))) (js/parseInt s)))

(defn file-search
  "Given original parts and files passes searched files and parts to component view"
  [files parts file-component]                       ;; if files/parts change
  (r/with-let [search-term (r/atom "")
               on-change #(let [val (-> % .-target .-value)]
                            (reset! search-term val))
               selected-filter-id (r/atom "")
               change-part #(reset! selected-filter-id %)]
              (let [selected-part (when (number? (str->int @selected-filter-id)) @selected-filter-id)
                    selected-group (when (string? @selected-filter-id) @selected-filter-id)]
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
                  [select/form-select-grouped {:items (get-parts-and-groups files parts)
                                       :format-item (fn [x] (:item-name x))
                                       :on-change change-part
                                       :value @selected-filter-id
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
                  selected-part)])))

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
             :params {:file (:file/id file)
                      :activity (get-in file [:task/_files 0 :activity/_tasks 0 :db/id])
                      :task (get-in file [:task/_files 0 :db/id])}
             :query {:tab "comments"}}
   [file-comments-text file]])

(defn file-replacement-modal-button
  [{:keys [e! task file small? icon-button?]}]
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
     (let [selected-file-info (some-> @selected-file
                                      :file-object
                                      file-model/file-info)
           file (if selected-file-info
                  (-> file
                      (assoc :file/name (str
                                          (:description
                                            (filename-metadata/name->description-and-extension (:file/name file)))
                                          "."
                                          (:extension
                                            (filename-metadata/name->description-and-extension (:file/name selected-file-info)))))
                      (assoc :file/size (:file/size selected-file-info)))
                  file)
           {:keys [title description] :as error} (or (file-upload/common-file-validation file))]
       [panels/modal {:title (tr [:file-upload :replace-file-modal-title])
                      :open-atom open-atom
                      :actions [DialogActions {:style {:padding-bottom "1rem"}}
                                [buttons/button-secondary
                                 {:on-click close
                                  :disabled @progress?
                                  :id (str "confirmation-cancel")}
                                 (tr [:buttons :cancel])]
                                [buttons/button-primary
                                 {:id (str "confirmation-confirm")
                                  :disabled (or @progress? error)
                                  :on-click #(do (e! (file-controller/->UploadNewVersion file
                                                                                         @selected-file
                                                                                         progress?
                                                                                         (:db/id task)))
                                                 (reset! progress? true))}
                                 (tr [:file-upload :replace-file-confirm])]]}
        (if @progress?
          [:div {:style {:display :flex
                         :justify-content :center}}
           [CircularProgress]]
          (if error
            [:div {:style {:padding "2rem 1rem"
                           :background-color theme-colors/gray-lightest}}
             [common-ui/info-box {:variant :error
                                  :title [:span {:style {:word-break :break-word}}
                                          title]
                                  :content description}]]
            [DialogContentText
             (tr [:file-upload :replace-file-modal-body]
                 {:file-full-name (:file/full-name file)
                  :selected-file-name (:file/name selected-file-info)})]))])
     [when-authorized
      :file/upload task
      [file-upload/FileUpload
       {:on-drop #(do (select-file %)
                      (open))
        :drag-container-id "file-details-upload-replacement"
        :color :secondary
        :icon [icons/file-cloud-upload]
        :multiple? false}


       (if icon-button?
         [IconButton {:component :span
                      :size :small}
          [icons/file-cloud-upload-outlined {:style {:color theme-colors/primary}}]]
         [buttons/button-secondary
          (merge {:component :span
                  :start-icon (r/as-element [icons/file-cloud-upload-outlined])}
                 (when small?
                   {:size :small}))
          (tr [:file :upload-new-version])])]]]))

(defn file-row2
  [{:keys [link-to-new-tab? no-link?
           allow-replacement-opts delete-action
           attached-to land-acquisition?
           comments-link? actions? title-downloads?
           link-icon? column-widths]
    :or {comments-link? true
         actions? true
         column-widths [10 1]}}
   {:file/keys [status] :as file}]
  (let [{:keys [description extension]} (filename-metadata/name->description-and-extension (:file/name file))
        [base-column-width action-column-width] column-widths]
    [:div {:class (<class (if link-icon? common-styles/flex-align-center file-style/file-row-style))
           :data-file-description description}
     [:div {:class (<class file-style/file-base-column-style
                           base-column-width (if link-icon? false actions?))}
      [:div {:class (<class file-style/file-icon-container-style)}
       [(if link-icon?
          icons/content-link
          icons/action-description-outlined)  {:style {:color theme-colors/primary}}]]    ;; This icon could be made dynamic baseed on the file-type
      [:div {:style {:min-width 0}}
       [:div {:class [(<class common-styles/flex-align-center)
                      (<class common-styles/margin-bottom 0.5)]}
        (if no-link?
          [:p {:class (<class file-style/file-list-entity-name-style)
               :title description}
           description]
          (if title-downloads?
            [common/Link {:target "_blank"
                          :class (<class file-style/file-list-entity-name-style)
                          :href (common-controller/query-url
                                  (if attached-to
                                    :file/download-attachment
                                    :file/download-file)
                                  (merge
                                    {:file-id (:db/id file)}
                                    (when attached-to
                                      {:attached-to attached-to})))}
             description]
            [url/Link (merge
                     {:title description
                      :class (<class file-style/file-list-entity-name-style)
                      :page :file
                      :params {:file (:file/id file)}}
                      (when link-to-new-tab?
                        {:target "_blank"}))
           description]))
        [typography/SmallGrayText {:style {:text-transform :uppercase
                                           :white-space :nowrap}}
         extension]]
       [:div.file-info
        [file-identifying-info land-acquisition? file]
        [typography/SmallText
         (if-let [modified-at (:meta/modified-at file)]
           (tr [:file :edit-info]
               {:date (format/date-time-with-seconds modified-at)
                :author (user-model/user-name
                          (:meta/modifier file))})
           (tr [:file :upload-info]
                 {:date (format/date-time-with-seconds (:meta/created-at file))
                  :author (user-model/user-name
                            (:meta/creator file))}))]
        [:div {:class [(<class common-styles/flex-align-center)]}
         (when status
          [typography/SmallBoldText (tr-enum status)])
        (when (and comments-link? (:comment/counts file))
          [:div {:style {:margin-left "1rem"}}[file-comments-link file]])]]]]
     (when actions?
       [:div {:class (<class file-style/file-actions-column-style action-column-width)}
        [:div {:style {:display :flex}}
         (when (and allow-replacement-opts (file-model/editable? file))
           (with-meta
             [file-replacement-modal-button (merge allow-replacement-opts
                                                   {:file file
                                                    :icon-button? true})]
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
             :trashcan? true}])]])]))

(defn document-group-heading
  [group-key]
  [:div {:class (<class common-styles/space-between-center)}
   [:div {:class (<class common-styles/margin 1 1 1 0.5)} [icons/file-folder-outlined {:style {:color theme-colors/gray-dark}}]]
   (tr-enum group-key)])

(defn document-group-content
  [e! opts file-group grouped-files]
    (mapc
      (r/partial file-row2 opts)
      (filter #(= (:file/group-name %) file-group) grouped-files)))

(defn file-list2
  [{:keys [e! sort-by-value data-cy] :as opts} files]
  (r/with-let [closed? (r/atom #{})
               toggle-open! #(swap! closed? cu/toggle %)]
  [:div (merge {:class (<class common-styles/margin-bottom 1.5)}
               (when data-cy
                 {:data-cy data-cy}))
   (let
     [grouped-files (get-document-groups files)]
     (doall
       (for [file-group (distinct (mapv #(:file/group-name %) grouped-files))]
       ^{:key (str file-group)}
       [:<>
        (if
          (some? file-group)
                      [:div {:class (<class common/hierarchical-heading-container2 theme-colors/white theme-colors/black-coral @closed?)}
                       [:div {:class (<class common-styles/space-between-center)} (document-group-heading file-group)
                       [:div {:class [(<class common-styles/flex-row-end)]}
                        [buttons/button-secondary
                         {:size :small
                          :on-click (r/partial toggle-open! file-group)}
                         [(if (contains? @closed? file-group) icons/hardware-keyboard-arrow-down icons/hardware-keyboard-arrow-up)]]]]
                       [Collapse {:in (not (contains? @closed? file-group))
                                  :mount-on-enter true}
                        [:div
                         (document-group-content e! opts file-group grouped-files)]]]
          (document-group-content e! opts file-group grouped-files))])))]))

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

(defn- file-icon-style []
  {:display :inline-block
   :margin-right "0.5rem"
   :position :relative
   :top "6px"})

(defn file-icon [{class :class
                  :as file}]
  [:div {:class (if class
                  [class (<class file-icon-style)]
                  (<class file-icon-style))}
   (cond
     (file-model/image? file)
     [fi/image]

     :else
     [ti/file])])

(defn file-list-entry-style
  []
  {:padding "0.5rem 0.5rem 0.5rem 0"
   :max-width "100%"
   :border-top (str "2px solid " theme-colors/black-coral-1)})

(defn- file-listing-entry
  [{:file/keys [name version number status] :as f} active?]
  (let [{:keys [description extension]}
        (filename-metadata/name->description-and-extension name)]
    [:div.file-list-entry {:class (<class file-list-entry-style)}
     [:div {:class (<class common-styles/flex-row)}
      [file-icon (assoc f :class "file-list-icon")]
      [:div {:class (<class common-styles/min-width-0)}
       [:div {:class (<class common-styles/flex-align-center)}
        [:div.file-list-name {:class (<class file-style/file-name-truncated)
                              :title description}
         (if active?
           [:strong description]
           [url/Link {:page :file
                      :params {:file (:file/id f)}
                      :class "file-list-name"}
            description])]
        [typography/SmallGrayText (if extension
                                    (str/upper-case extension)
                                    "")]]
       [:div
        [:span.file-list-number
         number]
        [typography/Text2Bold {:class (<class common-styles/inline-block)}
         (str "V" version)]
        (when status
          [typography/Text2 {:class (<class common-styles/inline-block)}
           " / " (tr-enum status)])]]]]))

(defn- file-list [parts files current-file-id]
  (let [parts (sort-by :file.part/number
                       (concat [{:file.part/number 0 :file.part/name (tr [:file-upload :general-part])}]
                               parts))]
    [:div.file-list {:style {:flex 1
                             :padding "1rem 0 1rem 1rem"
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
              [typography/Heading2
               name
               [typography/GrayText {:style {:padding-left "0.25rem"}}
                (gstr/format "#%02d" number)]]]
             [:div
              (mapc (fn [{id :db/id :as f}]
                      (let [active? (= current-file-id id)]
                        [file-listing-entry f active?]))
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

(defn- file-part-sub-heading [{:file.part/keys [name number]}]
  [:div
   [typography/Heading3 {:class (<class common-styles/margin-bottom 1)}
    name
    [typography/GrayText {:class (<class common-styles/margin-left 0.5)}
     (gstr/format "#%02d" number)]]])

(defn- file-details [{:keys [e! app project-id task file file-part
                             latest-file can-submit? replace-form]}]
  (r/with-let [selected-file (r/atom nil)
               select-file #(reset! selected-file (if (= @selected-file %)
                                                    nil
                                                    %))]
    (let [{:keys [replacement-upload-progress]} file
          old? (some? latest-file)
          other-versions (if old?
                           (into [latest-file]
                                 (filter #(not= (:db/id file) (:db/id %))
                                         (:versions latest-file)))
                           (:versions file))]
      ^{:key (str (:db/id file))}
      [:div.file-details
       [:div {:class (<class common-styles/margin-bottom 1)}
        [file-part-sub-heading file-part]
        [:div.file-details-full-name {:class (herb/join (<class common-styles/flex-row)
                                                        (<class common-styles/margin-bottom 0.2))}
         [typography/TextBold {:class (<class common-styles/margin-right 0.25)}
          (tr [:file :full-name]) ":"]
         [typography/Text (:file/full-name file)]]
        [:div.file-details-original-name {:class (herb/join (<class common-styles/flex-row)
                                                            (<class common-styles/margin-bottom 0.2))}
         [typography/SmallBoldText {:class (<class common-styles/margin-right 0.25)}
          (tr [:fields :file/original-name]) ":"]
         [typography/Text2
          [:span (:file/original-name file)]]]

        (let [first-version (or (last other-versions) file)
              [edited-by edited-at :as edited?]
              (if (not= first-version file)
                (edited file)
                (when (:meta/modified-at first-version)
                  (edited first-version)))]
          [:<>
           [:div.file-details-upload-info {:class (<class common-styles/margin-bottom 0.2)}
            [typography/SmallText
             (tr [:file :upload-info] {:author (user-model/user-name (:meta/creator file))
                                       :date (format/date-time (:meta/created-at file))})]]

           (when edited?
             [:div.file-details-edit-info {:class (<class common-styles/margin-bottom 0.2)}
              [typography/SmallText
               (tr [:file :edit-info] {:author (user-model/user-name edited-by)
                                       :date (format/date-time edited-at)})]])])]



       ;; size, upload new version and download buttons
       [:div {:class (<class common-styles/flex-row-space-between)}
        [common/labeled-data {:class "file-details-size"
                              :label (tr [:fields :file/size])
                              :data (format/file-size (:file/size file))}]
        [:div {:class (<class common-styles/flex-row)}
         (if replacement-upload-progress
           [LinearProgress {:variant :determinate
                            :value replacement-upload-progress}]
           [:<>
            (when (and can-submit?
                       (nil? latest-file)
                       (file-model/editable? file))
              [file-replacement-modal-button {:e! e!
                                              :project-id project-id
                                              :task task
                                              :file file
                                              :replace-form replace-form
                                              :small? true}])])
         [:div {:class (<class common-styles/margin-left 0.5)}
          [buttons/button-primary {:element "a"
                                   :href (common-controller/query-url :file/download-file
                                                                      {:file-id (:db/id file)})
                                   :size :small
                                   :target "_blank"
                                   :start-icon (r/as-element
                                                 [icons/file-cloud-download-outlined])}
           (tr [:file :download])]]]]

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
                    [common/Link {:target :_blank
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

(defn no-files
  []
  [typography/GrayTextDiv {:class [(<class typography/gray-text-style)
                                (<class common-styles/flex-row)
                                (<class common-styles/margin-bottom 1)]}
   [:div {:class [(<class common-styles/flex-row)
                  (<class common-styles/margin-bottom 1.5)]}
    [ti/file {:class (<class common-styles/margin-right 0.5)}]
   [:span {:class (<class common-styles/flex-align-center)} (tr [:file :no-files])]]])

(defn- file-edit-name [{:keys [value on-change error error-text]}]
  (let [[name original-name] value
        {:keys [description extension]} (filename-metadata/name->description-and-extension name)]
    [:<>
     [TextField {:label (tr [:file-upload :description])
                 :value description
                 :error error
                 :error-text error-text
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
                  :delete delete
                  :delete-message (tr [:file :delete])}
       ^{:attribute :file/part}
       [select/form-select {:items parts
                            :format-item #(gstr/format "%s #%02d"
                                                       (:file.part/name %)
                                                       (:file.part/number %))
                            :empty-selection-label (str (tr [:file-upload :general-part]) " #00")
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

       ^{:attribute [:file/name :file/original-name]
         :validate (fn [[name _orig-name]]
                     (let [{:keys [description]} (filename-metadata/name->description-and-extension name)]
                       (cond
                         (not (file-model/valid-chars-in-description? description))
                         [:span {:style {:word-break :break-word}}
                          (tr [:error :invalid-chars-in-description] {:characters file-model/allowed-chars-string})]
                         (not (file-model/valid-description-length? description))
                         (tr [:error :description-too-long] {:limit file-model/max-description-length})
                         :else
                         nil)))}
       [file-edit-name {}]]]]))

(defn- file-db-id
  "Get the file's actual :db/id from page queried state."
  [state]
  (get-in state [:navigation :file]))

(defn file-page [e! {{project-id :project} :params} project]
  ;; Update the file seen timestamp for this user
  (e! (file-controller/->UpdateFileSeen (file-db-id project)))
  (let [edit-open? (r/atom false)]
    (r/create-class
      {:component-did-update
       (fn [this [_ _ _ prev-project _ :as _prev-args] _ _]
         (let [[_ _ _ curr-project _ :as _curr-args] (r/argv this)
               prev-file-id (file-db-id prev-project)
               curr-file-id (file-db-id curr-project)]
           (when (not= curr-file-id prev-file-id)
             (e! (file-controller/->UpdateFileSeen curr-file-id)))))

       :reagent-render
       (fn [e! app project]
         (let [{file-id :file
                task-id :task
                activity-id :activity} (:navigation project)
               activity (project-model/activity-by-id project activity-id)
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
               [:div
                [project-navigator-view/project-navigator-with-content
                 {:e! e!
                  :app app
                  :project project
                  :column-widths [3 9 :auto]
                  :show-map? false
                  :content-padding "0rem"}
                 [Grid {:container true :spacing 0}
                  [Grid {:item true :xs 4
                         :class (<class common-styles/flex-column-1)}
                   [file-list (:file.part/_task task) (:task/files task) (:db/id file)]]
                  [Grid {:item true :xs 8
                         :class (<class common-styles/padding 2 2 2 1.75)}
                   [:<>
                    (when @edit-open?
                      [file-edit-dialog {:e! e!
                                         :on-close #(reset! edit-open? false)
                                         :activity activity
                                         :file file
                                         :parts (:file.part/_task task)}])]

                   [:div {:class (<class common-styles/flex-row)}
                    [typography/Heading1 {:title description
                                          :class (herb/join (<class common-styles/margin-bottom 0.5)
                                                            (<class common-styles/flex-shrink-no-wrap))}
                     [:span {:class (<class common-styles/overflow-ellipsis)}
                      description]
                     [typography/GrayText {:style {:display :inline-block
                                                   :margin  "0 0.5rem"}}
                      (str/upper-case extension)]]
                    [:div {:style {:flex-grow  1
                                   :text-align :end}}
                     (when (file-model/editable? file)
                       [buttons/button-secondary {:on-click #(reset! edit-open? true)
                                                  :data-cy  "edit-file-button"}
                        (tr [:buttons :edit])])]]
                   [file-identifying-info (du/enum= :activity.name/land-acquisition
                                                    (:activity/name activity))
                    file]
                   [:div {:class (<class common-styles/margin-bottom 0.5)}
                    [typography/Text3
                     (tr-enum (:file/status file))]]

                   ^{:key (str (:db/id file) "-details-and-comments")}
                   [tabs/details-and-comments-tabs
                    {:e! e!
                     :app app
                     :after-comment-list-rendered-event common-controller/->Refresh
                     :comment-link-comp [file-comments-link file]
                     :after-comment-added-event common-controller/->Refresh
                     :entity-id (:db/id file)
                     :entity-type :file
                     :show-comment-form? (not old?)}
                    (when file
                      [file-details {:e! e!
                                     :app app
                                     :project-id project-id
                                     :task task
                                     :file file
                                     :latest-file latest-file
                                     :file-part file-part
                                     :can-submit? (if
                                                    (> 0 (:file.part/number file-part))
                                                    (task-model/can-submit-part? file-part)
                                                    (task-model/can-submit? task))
                                     :replace-form (:files-form project)}])]]]]]))))})))
