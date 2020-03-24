(ns teet.file.file-view
  (:require [teet.project.project-navigator-view :as project-navigator-view]
            [teet.project.project-model :as project-model]
            [teet.ui.table :as table]
            [teet.file.file-controller :as file-controller]
            [teet.ui.buttons :as buttons]
            [teet.ui.url :as url]
            [teet.localization :refer [tr tr-enum]]
            [teet.ui.material-ui :refer [Grid]]
            [teet.ui.typography :as typography]
            [teet.ui.util :refer [mapc]]
            [herb.core :refer [<class]]
            [teet.common.common-styles :as common-styles]
            [clojure.string :as str]
            [re-svg-icons.tabler-icons :as ti]
            [re-svg-icons.feather-icons :as fi]

            [teet.ui.tabs :as tabs]))

(def file-columns
  [:file/name
   :file/number
   :file/type
   :file/version
   :file/status
   :file/controls])

(defn format-file-list
  [column value _row]
  (case column
    :file/controls
    [:div "controls"]
    (str value)))

(defn file-table
  [e! files]
  [:div
   [table/table
    {:on-row-click (e! file-controller/->NavigateToFile)
     :columns file-columns
     :format-column format-file-list
     :data files
     :get-column get
     :filter-type {}}]
   [buttons/button-primary {:href (url/set-params :add-document 1)}
    (tr [:task :upload-files])]])

(defn file-icon [{:file/keys [name type]}]
  (cond
    (and type (str/starts-with? type "image/"))
    [fi/image]

    :else
    [ti/file]))

(defn file-list [e! files]
  [:<>
   [typography/Heading2 (tr [:task :results])]
   [:div
    (mapc (fn [{id :db/id :file/keys [name type version number status] :as f}]
            ^{:key (str id)}
            [:div
             [:div name]
             (mapc (fn [item]
                     [:div {:class (<class common-styles/inline-block)}
                      item])
                   [[file-icon f]
                    type
                    number
                    version
                    (when status
                      (tr-enum status))])])
          files)]])

(defn file-info [e! {:file/keys [name] :as file}]
  [tabs/details-and-comments-tabs
   {:e! e!}
   [file-icon file]
   name])

(defn file-page [e! {{file-id :file
                      task-id :task} :params
                     :as app} project breadcrumbs]
  (let [task (project-model/task-by-id project task-id)
        file (project-model/file-by-id project file-id)]
    [project-navigator-view/project-navigator-with-content
     {:e! e!
      :app app
      :project project
      :breadcrumbs breadcrumbs}
     [Grid {:container true}
      [Grid {:item true :xs 4}
       [file-list e! (:task/files task)]]
      [Grid {:item true :xs 8}
       [:div "filen sivu " (pr-str file)]]]]))
