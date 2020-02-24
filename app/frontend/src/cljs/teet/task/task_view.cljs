(ns teet.task.task-view
  "View for a workflow task"
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.task.task-controller :as task-controller]
            [teet.task.task-style :as task-style]
            [teet.localization :refer [tr]]
            [teet.ui.buttons :as buttons]
            [teet.ui.format :as format]
            [teet.ui.material-ui :refer [Grid Paper Link LinearProgress]]
            [teet.ui.icons :as icons]
            [teet.ui.typography :as typography :refer [Heading1]]
            [teet.project.project-view :as project-view]
            [teet.ui.panels :as panels]
            [teet.ui.url :as url]
            [teet.document.document-view :as document-view]
            [teet.ui.breadcrumbs :as breadcrumbs]
            [teet.project.task-model :as task-model]
            [teet.document.document-controller :as document-controller]
            [teet.comments.comments-view :as comments-view]
            [teet.ui.form :as form]
            [teet.common.common-controller :as common-controller]
            [teet.ui.file-upload :as file-upload]
            [teet.ui.select :as select]
            [teet.ui.common :as common]
            [teet.comments.comments-controller :as comments-controller]))

(defn task-status [e! status modified]

  [select/status {:e!        e!
                  :on-change (e! task-controller/->UpdateTaskStatus)
                  :status    (:db/ident status)
                  :attribute :task/status
                  :modified  modified
                  :values-filter task-model/current-statuses}])

(defn get-latest-modification
  "Takes the document and returns the latest modification time from either files or the doc it self"
  [{:meta/keys     [created-at modified-at]
    :document/keys [files]}]
  (let [latest-change
        (reduce (fn [latest-time {:meta/keys [created-at]}]
                  (if (> latest-time created-at)
                    latest-time
                    created-at))
                (or modified-at created-at)
                files)]
    latest-change))

(defn task-navigation
  [{:task/keys [documents] :as task} {selected-file-id     :file
                                      selected-document-id :document}]
  [:div {:style {:padding "2rem 0 2rem 2rem"}}
   (if (and (nil? selected-file-id) (nil? selected-document-id))
     [:b {:class (<class task-style/study-link-style)}
      (tr [:enum (-> task :task/type :db/ident)])]
     [Link {:class (<class task-style/study-link-style)
            :href  (url/set-params :document nil
                                   :file nil)}
      (tr [:enum (-> task :task/type :db/ident)])])
   [:p
    [:b (tr [:task :results])]]
   (doall
     (for [{:document/keys [name status files]
            :as document} documents]
       ^{:key (str (:db/id document))}
       [:div
        [:div
         (if (and (= (str (:db/id document)) selected-document-id) (nil? selected-file-id))
           [:b {:class (<class task-style/result-style)}
            name]
           [Link {:class (<class task-style/result-style)
                  :href  (url/set-params :document (:db/id document)
                                         :file nil)}
            name])
         [typography/SmallText {:style {:margin-bottom "0.5rem"}}
          [:span {:style {:font-weight    :bold
                          :text-transform :uppercase}}
           (tr [:enum (:db/ident status)])]
          [:span
           " " (tr [:common :last-modified]) ": " (format/date (get-latest-modification document))]]]
        (for [{:file/keys [name size] :as file
               file-id    :db/id} files]
          ^{:key (str file-id)}
          [:div {:class (<class task-style/file-container-style)}
           (if (= (str file-id) selected-file-id)
             [:b {:class (<class task-style/document-file-name)} name]
             [Link {:class (<class task-style/document-file-name)
                    :href  (url/set-params :document (:db/id document)
                                           :file (:db/id file))}
              name])
           [typography/SmallText (format/file-size size)]])]))])

(defn- task-overview
  [e! {:task/keys [description status modified type] :as _task}]
  [:div {:style {:padding "2rem 0"}}
   [:div {:style {:justify-content :space-between
                  :display         :flex}}
    [Heading1 (tr [:enum (:db/ident type)])]
    [:div {:style {:display :flex}}
     [buttons/button-secondary {:on-click (e! task-controller/->OpenEditModal :task)}
      (tr [:buttons :edit])]]]
   [:p description]
   [task-status e! status modified]
   [buttons/button-primary {:on-click   #(e! (task-controller/->OpenAddDocumentDialog))
                            :start-icon (r/as-element [icons/content-add])}
    (tr [:task :add-document])]])

(defn- task-document-content
  [e! document]
  [:<>
   [common/header-with-actions
    (:document/name document)

    [buttons/button-secondary
     {:element    "a"
      :href       (url/set-params "add-files" "1")
      :start-icon (r/as-element
                    [icons/content-add])}
     (tr [:document :add-files])]

    [buttons/button-secondary
     {:style    {:margin-left "1rem"}
      :on-click (e! task-controller/->OpenEditModal :document)}
     (tr [:buttons :edit])]]
   [typography/Paragraph {:style {:margin-bottom "2rem"}}
    (:document/description document)]
   [comments-view/comments {:e!                   e!
                            :new-comment          (:new-comment document)
                            :comments             (:document/comments document)
                            :update-comment-event comments-controller/->UpdateNewCommentForm
                            :save-comment-event   comments-controller/->CommentOnDocument}]])

(defn document-file-content
  [e! {:file/keys [name timestamp]
       id         :db/id :as file}]
  [:<>
   [common/header-with-actions
    name
    [buttons/delete-button-with-confirm {:action (e! document-controller/->DeleteFile id)}
     (tr [:buttons :delete])]]
   [typography/SmallText {:style {:margin-bottom "1rem"}}
    (tr [:document :updated]) " "
    (format/date-time timestamp)]
   [buttons/button-primary {:href       (document-controller/download-url id)
                            :style      {:margin-bottom "2rem"}
                            :element    "a"
                            :target     "_blank"
                            :start-icon (r/as-element
                                          [icons/file-cloud-download])}
    (tr [:document :download-file])]

   [comments-view/comments {:e!                   e!
                            :update-comment-event comments-controller/->UpdateFileNewCommentForm
                            :save-comment-event   comments-controller/->CommentOnFile
                            :new-comment          (:new-comment file)
                            :comments             (:file/comments file)}]])

(defn task-page-content
  [e! {:keys [file document]} task]
  (cond
    file
    [document-file-content e! (task-model/file-by-id task file)]
    document
    [task-document-content e! (task-model/document-by-id task document)]
    :else
    [task-overview e! task]))

(defn- add-files-form [e! upload-progress]
  (r/with-let [form (r/atom {})]
    [:<>
     [form/form {:e!              e!
                 :value           @form
                 :on-change-event (form/update-atom-event form merge)
                 :save-event      (partial document-controller/->AddFilesToDocument (:document/files @form))
                 :cancel-event    #(common-controller/->SetQueryParam :add-files nil)
                 :in-progress?    upload-progress
                 :spec :document/add-files}
      ^{:attribute :document/files}
      [file-upload/files-field {}]]
     (when upload-progress
       [LinearProgress {:variant "determinate"
                        :value   upload-progress}])]))

(defn task-page-modal
  [e! {:keys [params] :as app} {:keys [edit add-files] :as query}]
  [:<>
   [panels/modal {:open-atom (r/wrap (boolean add-files) :_)
                  :title     (tr [:document :add-files])
                  :on-close  #(e! (common-controller/->SetQueryParam :add-files nil))}
    [add-files-form e! (get-in app [:new-document :in-progress?])]]
   [panels/modal {:open-atom (r/wrap (boolean edit) :_)
                  :title     (if-not edit
                               ""
                               (tr [:task (keyword (str "edit-" edit))]))
                  :on-close  (e! task-controller/->CloseEditDialog)}
    (case edit
      "task"
      [project-view/task-form e!
       (merge
         {:close             task-controller/->CloseEditDialog
          :task              (:edit-task-data app)
          :initialization-fn (e! task-controller/->MoveDataForEdit)
          :save              task-controller/->PostTaskEditForm
          :on-change         task-controller/->UpdateEditTaskForm
          :delete            (task-controller/->DeleteTask (:task params))})]
      "document"
      [document-view/document-form e!
       (merge
         {:on-close-event    task-controller/->CloseEditDialog
          :initialization-fn (e! document-controller/->MoveDocumentDataForEdit (:document query))
          :save              document-controller/->PostDocumentEdit
          :on-change         document-controller/->UpdateDocumentEditForm
          :document          (:edit-document-data app)
          :editing?          true
          :delete            (document-controller/->DeleteDocument (:document query))})]
      [:span])]])

(defn task-page [e! {{:keys [add-document] :as query} :query
                     new-document :new-document :as app}
                 {project :project :as task}
                 breadcrumbs]
  [:div {:style {:padding        "1.5rem 1.875rem"
                 :display        :flex
                 :flex-direction :column
                 :flex           1}}
   [task-page-modal e! app query]
   [panels/modal {:open-atom (r/wrap (boolean add-document) :_)
                  :title     (tr [:task :add-document])
                  :on-close  (e! task-controller/->CloseAddDocumentDialog)}
    [document-view/document-form e! {:on-close-event task-controller/->CloseAddDocumentDialog
                                     :save           document-controller/->CreateDocument
                                     :on-change      document-controller/->UpdateDocumentForm
                                     :document       new-document}]]
   [breadcrumbs/breadcrumbs breadcrumbs]
   [Heading1 (:thk.project/name project)]

   [Paper {:class (<class task-style/task-page-paper-style)}
    [Grid {:container true
           :spacing   3}
     [Grid {:item  true
            :xs    3
            :style {:max-width "300px"}}
      [task-navigation task query]]
     [Grid {:item  true
            :xs    6
            :style {:max-width "800px"}}
      [task-page-content e! query task]]
     [Grid {:item  true
            :xs    :auto
            :style {:display :flex
                    :flex    1}}
      [project-view/project-map e! app project]]]]])
