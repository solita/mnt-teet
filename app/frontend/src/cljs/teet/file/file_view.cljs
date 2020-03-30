(ns teet.file.file-view
  (:require [teet.project.project-navigator-view :as project-navigator-view]
            [teet.project.project-model :as project-model]
            [teet.file.file-controller :as file-controller]
            [teet.ui.buttons :as buttons]
            [teet.ui.url :as url]
            [teet.ui.icons :as icons]
            [teet.localization :refer [tr tr-enum]]
            [teet.ui.material-ui :refer [Grid Link LinearProgress Menu MenuItem]]
            [teet.ui.typography :as typography]
            [teet.ui.util :refer [mapc]]
            [herb.core :refer [<class]]
            [teet.common.common-styles :as common-styles]
            [clojure.string :as str]
            [re-svg-icons.tabler-icons :as ti]
            [re-svg-icons.feather-icons :as fi]
            [teet.ui.tabs :as tabs]
            [teet.theme.theme-colors :as theme-colors]
            [reagent.core :as r]
            [teet.ui.select :as select]
            [teet.user.user-model :as user-model]
            [teet.ui.format :as format]
            [teet.ui.file-upload :as file-upload]
            [teet.log :as log]
            [teet.common.common-controller :as common-controller]
            [teet.ui.panels :as panels]))

(defn- file-column-style
  ([basis]
   (file-column-style basis :flex-start))
  ([basis justify-content]
   (file-column-style basis justify-content 0))
  ([basis justify-content grow]
   ^{:pseudo {:first-child {:border-left 0}
              :last-child {:border-right 0}}}
   {:flex-basis (str basis "%")
    :border-color theme-colors/gray-lighter
    :border-style :solid
    :border-width "2px 2px 0 0"
    :flex-grow grow
    :flex-shrink 0
    :word-break :break-all
    :display :flex
    :align-items :center
    :padding "0.5rem 0.25rem"
    :justify-content justify-content}))

(defn- file-row-icon-style
  []
  {:margin "0 0.25rem"})

(defn- base-name-and-suffix [name]
  (let [[_ base-name suffix] (re-matches #"^(.*)\.([^\.]+)$" name)]
    (if base-name
      [base-name (str/upper-case suffix)]
      [name ""])))

(defn- file-row
  [{id :db/id :file/keys [number version status name] :as _file}]
  (let [[base-name suffix] (base-name-and-suffix name)]
    [:div {:class [(<class common-styles/flex-row) (<class common-styles/margin-bottom 0.5)]}
     [:div {:class (<class file-column-style 44)}
      [url/Link {:page :file :params {:file id}} base-name]]
     [:div {:class (<class file-column-style 10)}
      [:span number]]
     [:div {:class (<class file-column-style 10)}
      [:span suffix]]
     [:div {:class (<class file-column-style 10)}
      [:span version]]
     [:div {:class (<class file-column-style 13)}
      [:span status]]
     [:div {:class (<class file-column-style 13 :flex-end)}
      [url/Link {:class (<class file-row-icon-style)
                 :page :file
                 :params {:file id}
                 :query {:tab "comments"}}
       [icons/communication-comment]]
      [Link {:class (<class file-row-icon-style)
             :target :_blank
             :href (common-controller/query-url :file/download-file {:file-id id})}
       [icons/file-cloud-download]]]]))

(defn- other-version-row
  [{id :db/id
    :file/keys [name version]
    :meta/keys [created-at creator]
    :as _file}]
  [:div {:class [(<class common-styles/flex-row) (<class common-styles/margin-bottom 0.5)]}
   [:div {:class (<class file-column-style 60)}
    [url/Link {:page :file :params {:file id}}
     name]]
   [:div {:class (<class file-column-style 10 :center)}
    [:span (str "V" version)]]
   [:div {:class (<class file-column-style 30 :flex-end)}
    [:span (format/date created-at)]]])

(defn file-table
  [files]
  [:div
   (mapc file-row files)
   [buttons/button-primary {:href (url/set-query-param :add-document 1)
                            :start-icon (r/as-element
                                         [icons/file-cloud-upload])}
    (tr [:task :upload-files])]])

(defn- file-icon-style []
  {:display :inline-block
   :margin-right "0.25rem"
   :position :relative
   :top "6px"})

(defn file-icon [{:file/keys [type]}]
  [:div {:class (<class file-icon-style)}
   (cond
     (and type (str/starts-with? type "image/"))
     [fi/image]

     :else
     [ti/file])])

(defn- file-list [files current-file-id]
  [:<>
   [typography/Heading2 (tr [:task :results])]
   [:div
    (mapc (fn [{id :db/id :file/keys [name version number status] :as f}]
            (let [[_ base-name suffix] (re-matches #"^(.*)\.([^\.]+)$" name)]
              [:div
               [:div
                [file-icon f]
                (if (= current-file-id id)
                  [:b (or base-name name)]
                  [url/Link {:page :file
                             :params {:file id}}
                   (or base-name name)])]
               [:div {:style {:font-size "12px" :display :flex
                              :justify-content :space-between
                              :margin "0 1rem 1rem 1rem"}}
                (mapc
                 (fn [item]
                   [:span {:style {:flex-basis "25%" :flex-shrink 0 :flex-grow 0}} item])
                 [(if suffix
                    (str/upper-case suffix)
                    "")
                  number
                  (str "V" version)
                  (when status
                    (tr-enum status))])]]))
          files)]])

(defn- file-status [e! file]
  [select/select-enum {:e! e!
                       :show-label? false
                       :value (:file/status file)
                       :attribute :file/status
                       :on-change (e! file-controller/->UpdateFileStatus (:db/id file))}])

(defn- labeled-data [[label data]]
  [:div {:class (<class common-styles/inline-block)}
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

(defn- file-details [e! {:keys [replacement-upload-progress] :as file} latest-file]
  (let [old? (some? latest-file)
        other-versions (if old?
                         (into [latest-file]
                               (filter #(not= (:db/id file) (:db/id %))
                                       (:versions latest-file)))
                         (:versions file))]
    [:div
     [:div (:file/name file)]
     [:div (tr [:file :upload-info] {:author (user-model/user-name (:meta/creator file))
                                     :date (format/date (:meta/created-at file))})]
     [:div {:class (<class common-styles/flex-row-space-between)}
      (mapc labeled-data
            [[(tr [:fields :file/number]) (:file/number file)]
             [(tr [:fields :file/version]) [:span (when old?
                                                    {:class (<class common-styles/warning-text)})
                                            (str "V" (:file/version file)
                                                 (when old?
                                                   (str " " (tr [:file :old-version]))))]]
             (if old?
               ["" [url/Link {:page :file
                              :params {:file (:db/id latest-file)}}
                    (tr [:file :switch-to-latest-version])]]
               [(tr [:fields :file/status]) [file-status e! file]])])]

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
      [labeled-data [(tr [:fields :file/size]) (format/file-size (:file/size file))]]
      (if replacement-upload-progress
        [LinearProgress {:variant :determinate
                         :value replacement-upload-progress}]
        [file-upload/FileUploadButton {:on-drop (e! file-controller/->UploadNewVersion file)
                                       :color :secondary
                                       :icon [icons/file-cloud-upload]
                                       :multiple? false}
         (tr [:file :upload-new-version])])
      [buttons/button-primary {:element "a"
                               :href (common-controller/query-url :file/download-file
                                                                  {:file-id (:db/id file)})
                               :target "_blank"
                               :start-icon (r/as-element
                                            [icons/file-cloud-download])}
       (tr [:file :download])]]

     ;; list previous versions
     [typography/Heading2 (tr [:file :other-versions])]
     [:div
      (mapc other-version-row other-versions)]]))


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
      [buttons/button-warning {:on-click :D}
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
        [Grid {:item true :xs 9}
         [:div
          [typography/Heading2 [file-icon file] (:file/name file)

           [buttons/button-secondary {:on-click #(reset! edit-open? true)
                                      :style {:float :right}}
            (tr [:buttons :edit])]
           (when @edit-open?
             [file-edit-dialog {:e! e! :on-close #(reset! edit-open? false) :file file}])]]

         [tabs/details-and-comments-tabs
          {:e! e!
           :app app
           :entity-id (:db/id file)
           :entity-type :file
           :show-comment-form? (not old?)}
          [file-details e! file latest-file]]]]])))
