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
            [teet.ui.util :refer [mapc]]
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
            [teet.comments.comments-controller :as comments-controller]
            [teet.project.project-navigator-view :as project-navigator-view]
            [teet.project.project-style :as project-style]
            [teet.project.project-model :as project-model]
            [teet.theme.theme-colors :as theme-colors]
            [teet.user.user-model :as user-model]
            [teet.routes :as routes]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.date-picker :as date-picker]))

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

(defn- subtask-list-style []
  {:display :flex
   :flex-direction :column})

(defn- subtask-list-item []
  {:display :flex
   :justify-content :space-between
   :border (str "1px solid " theme-colors/gray-lightest)
   :border-width "1px 0px 1px 0px"
   :padding "1rem 0"})

(defn task-subtasks [e! params subtasks]
  [:div {:class (<class subtask-list-style)}
   (mapc (fn [{:subtask/keys [type assignee status] id :db/id}]
           [:div {:class (<class subtask-list-item)}
            [Link {:href (routes/url-for {:page :subtask
                                          :params (assoc params :subtask id)})}
             (tr [:enum (:db/ident type)])]
            (user-model/user-name assignee)
            (tr [:enum (:db/ident status)])])
         subtasks)])

(defn task-page-content
  [e! params {subtasks :task/subtasks :as task}]
  [task-subtasks e! params subtasks])

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


(defn task-form [_e! {:keys [initialization-fn]}]
  ;;Task definition (under project activity)
  ;; Task type (a predefined list of tasks: topogeodeesia, geoloogia, liiklusuuring, KMH eelhinnang, loomastikuuuring, arheoloogiline uuring, muu)
  ;; Description (short description of the task for clarification, 255char, in case more detailed description is needed, it will be uploaded as a file under the task)
  ;; Responsible person (email)
  (when initialization-fn
    (initialization-fn))
  (fn [e! {id :db/id :as task}]
    [form/form {:e! e!
                :value task
                :on-change-event task-controller/->UpdateEditTaskForm
                :cancel-event task-controller/->CloseEditDialog
                :save-event task-controller/->SaveTaskForm
                :delete (when id (task-controller/->DeleteTask id))
                :spec :task/new-task-form}
     ^{:xs 6 :attribute :task/group}
     [select/select-enum {:e! e! :attribute :task/group}]
     ^{:xs 6 :attribute :task/type}
     [select/select-enum {:e! e! :attribute :task/type
                          :enum/valid-for (:task/group task)}]

     ^{:attribute :task/description}
     [TextField {:full-width true :multiline true :rows 4 :maxrows 4}]

     ^{:attribute [:task/estimated-start-date :task/estimated-end-date] :xs 12}
     [date-picker/date-range-input {}]

     ^{:attribute :task/assignee}
     [select/select-user {:e! e! :attribute :task/assignee}]]))

(defmethod project-navigator-view/project-navigator-dialog :new-task
  [{:keys [e! app] :as opts} dialog]
  [task-form e! (:edit-task-data app)])

(defn task-page [e! {{:keys [add-document] :as query} :query
                     {task-id :task :as params} :params
                     new-document :new-document :as app}
                 project
                 breadcrumbs]
  [:div {:class (<class project-style/page-container)}
   [panels/modal {:open-atom (r/wrap (boolean add-document) :_)
                  :title     (tr [:task :add-document])
                  :on-close  (e! task-controller/->CloseAddDocumentDialog)}
    [document-view/document-form e! {:on-close-event task-controller/->CloseAddDocumentDialog
                                     :save           document-controller/->CreateDocument
                                     :on-change      document-controller/->UpdateDocumentForm
                                     :document       new-document}]]

   [project-navigator-view/project-navigator-with-content
    {:e! e!
     :project project
     :app app
     :breadcrumbs breadcrumbs}

    [task-page-content e! params
     (project-model/task-by-id project task-id)]]])
