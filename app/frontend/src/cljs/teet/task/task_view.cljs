(ns teet.task.task-view
  "View for a workflow task"
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.task.task-controller :as task-controller]
            [teet.ui.itemlist :as itemlist]
            [teet.localization :refer [tr]]
            [teet.ui.buttons :as buttons]
            [teet.ui.common :as ui-common]
            [teet.ui.format :as format]
            [teet.ui.material-ui :refer [List Grid Paper Link]]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.icons :as icons]
            [teet.ui.typography :refer [Heading1]]
            [teet.ui.layout :as layout]
            [teet.project.project-style :as project-style]
            [teet.user.user-info :as user-info]
            [teet.project.project-view :as project-view]
            [teet.ui.panels :as panels]
            [teet.ui.url :as url]
            [teet.document.document-view :as document-view]
            [teet.ui.breadcrumbs :as breadcrumbs]
            [teet.common.common-styles :as common-styles]
            [teet.project.task-model :as task-model]
            [teet.project.project-controller :as project-controller]
            [teet.document.document-controller :as document-controller]
            [teet.authorization.authorization-check :refer [when-authorized]]))

(defn task-status [e! status modified]
  [ui-common/status {:e! e!
                     :on-change (e! task-controller/->UpdateTaskStatus)
                     :status (:db/ident status)
                     :attribute :task/status
                     :modified modified}])

(defn task-page-paper-style
  []
  (merge (common-styles/content-paper-style)
         {:display :flex
          :flex 1}))

(defn task-navigation
  [{:task/keys [documents] :as task}]
  [:div {:style {:padding "2rem 0 2rem 2rem"}}
   [Link {:href (url/remove-params)} "linkki taskin pääsivulle"]
   [:p "Documents"]
   (for [{:document/keys [name status files] :as document} documents]
     ^{:key (str (:db/id document))}
     [:div {:style {:margin-bottom "2rem"}}                                                  ;;TODO iterate files here
      [Link {:href (url/set-params :document (:db/id document))}
       name]
      (for [{:file/keys [name size] :as file} files]
         [Link {:style {:margin-left "2rem"
                        :display :block}
               :href (url/set-params :file (:db/id file))}
         name " - " (format/file-size size)])])])

(defn- task-overview
  [e! {:task/keys [description status modified] :as task}]
  [:div {:style {:padding "2rem 0"}}
   [:div {:style {:justify-content :space-between
                  :display :flex}}
    [Heading1 (tr [:task :task-overview])]
    [:div {:style {:display :flex}}
     [buttons/button-secondary {:on-click (e! task-controller/->OpenEditTask)}
      (tr [:buttons :edit])]]]
   [:p description]
   [task-status e! status modified]
   [buttons/button-primary {:on-click #(e! (task-controller/->OpenAddDocumentDialog))
                            :start-icon (r/as-element [icons/content-add])}
    (tr [:task :add-document])]])

(defn- task-document-content
  [e! document]
  [:div
   [:div {:style {:display :flex
                  :justify-content :flex-end}}
    [buttons/button-warning
     {:on-click (e! document-controller/->DeleteDocument (:db/id document))}
     (tr [:buttons :delete])]]
   [:div {:style {:padding "2rem"}}
    [Heading1
     (:document/name document)]
    [teet.ui.typography/Paragraph (:document/description document)]]
   [document-view/comments e! document]])

(defn document-file-content
  [e! file]
  [:h1 "file: " (pr-str file)])

(defn task-page-content
  [e! {:keys [file document]} task]
  (cond
    file
    [document-file-content e! (task-model/file-by-id task file)]
    document
    [task-document-content e! (task-model/document-by-id task document)]
    :else
    [task-overview e! task]))

(defn task-page-modal
  [e! {:keys [params] :as app} {:keys [edit] :as query}]
  [panels/modal {:open-atom (r/wrap (boolean edit) :_)
                 :title     (if-not edit
                              ""
                              (tr [:project edit]))
                 :on-close  (e! task-controller/->CloseEditDialog)}
   (case edit
     "task"
     [project-view/task-form e!
      (merge
        {:close             task-controller/->CloseEditDialog
         :task              (:edit-task-data app)
         :initialization-fn (e! task-controller/->MoveDataForEdit)
         :save              task-controller/->PostTaskEditForm
         :on-change         task-controller/->UpdateEditTaskForm}
        (when-authorized :task/delete-task                  ;;TODO: ADD CONFIRMATION DIALOG
                         {:delete (task-controller/->DeleteTask (:task params))}))]
     [:span])])

(defn task-page [e! {{:keys [add-document edit] :as query} :query
                     new-document :new-document :as app}
                 {type :task/type
                  project :project :as task}
                 breadcrumbs]
  [:div {:style {:padding "1.5rem 1.875rem"
                 :display :flex
                 :flex-direction :column
                 :flex 1}}
   [task-page-modal e! app query]
   [panels/modal {:open-atom (r/wrap (boolean add-document) :_)
                  :title (tr [:task :add-document])
                  :on-close (e! task-controller/->CloseAddDocumentDialog)}
    [document-view/document-form {:e! e!
                                  :on-close-event task-controller/->CloseAddDocumentDialog}
     new-document]]
   [breadcrumbs/breadcrumbs breadcrumbs]
   [Heading1 (tr [:enum (:db/ident type)])]

   [Paper {:class (<class task-page-paper-style)}
    [Grid {:container true
           :spacing 3}
     [Grid {:item true
            :xs 3}
      [task-navigation task]]
     [Grid {:item true
            :xs 6}
      [task-page-content e! query task]]
     [Grid {:item true
            :style {:display :flex}
            :xs   3}
      [project-view/project-map e! app project]]]]])
