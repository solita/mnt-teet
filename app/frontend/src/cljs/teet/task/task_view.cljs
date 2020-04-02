(ns teet.task.task-view
  "View for a workflow task"
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.authorization.authorization-check :refer [when-authorized]]
            [teet.common.common-controller :as common-controller]
            [teet.common.common-styles :as common-styles]
            [teet.file.file-controller :as file-controller]
            [teet.file.file-view :as file-view]
            [teet.localization :refer [tr tr-enum]]
            [teet.project.project-controller :as project-controller]
            [teet.project.project-model :as project-model]
            [teet.project.project-navigator-view :as project-navigator-view]
            [teet.project.task-model :as task-model]
            [teet.task.task-controller :as task-controller]
            teet.task.task-spec
            [teet.ui.buttons :as buttons]
            [teet.ui.date-picker :as date-picker]
            [teet.ui.file-upload :as file-upload]
            [teet.ui.form :as form]
            [teet.ui.format :as format]
            [teet.ui.material-ui :refer [LinearProgress]]
            [teet.ui.panels :as panels]
            [teet.ui.select :as select]
            [teet.ui.tabs :as tabs]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.typography :as typography]
            [teet.user.user-model :as user-model]))

(defn task-status [e! {:task/keys [status]}]
  [select/select-enum {:e! e!
                       :on-change (e! task-controller/->UpdateTaskStatus)
                       :value (:db/ident status)
                       :show-label? false
                       :attribute :task/status
                       :values-filter task-model/current-statuses}])

(defn task-basic-info
  [e! {:task/keys [deadline assignee] :as task}]
  [:div {:class [(<class common-styles/flex-row-space-between) (<class common-styles/margin-bottom 1)]}
   [:div
    [typography/BoldGreyText (tr [:common :deadline])]
    [:span (format/date deadline)]]
   [:div
    [typography/BoldGreyText (tr [:fields :task/assignee])]
    [:span (user-model/user-name assignee)]]
   [:div
    [task-status e! task]]])

(defn task-details
  [e! _params {:task/keys [description files] :as task}]
  [:div
   [typography/Heading2 {:class (<class common-styles/margin-bottom 1)} (tr-enum (:task/type task))]
   (when description
     [typography/Paragraph description])
   [task-basic-info e! task]
   [file-view/file-table files]])

(defn- task-header
  [e! task]
  [:div {:class (<class common-styles/heading-and-button-style)}
   [typography/Heading1 (tr-enum (:task/type task))]
   (when-authorized :task/update
     task
     [buttons/button-secondary {:on-click #(e! (project-controller/->OpenEditTaskDialog (:db/id task)))}
      (tr [:buttons :edit])])])

(defn task-page-content
  [e! app task]
  [:div
   [task-header e! task]
   [tabs/details-and-comments-tabs
    {:e! e!
     :app app
     :type :task-comment
     :comment-command :comment/comment-on-task
     :entity-type :task
     :entity-id (:db/id task)}
    [task-details e! (:params app) task]]])

(defn- add-files-form [e! upload-progress]
  (r/with-let [form (r/atom {})]
    [:<>
     [form/form {:e!              e!
                 :value           @form
                 :on-change-event (form/update-atom-event form merge)
                 :save-event      (partial file-controller/->AddFilesToTask (:task/files @form))
                 :cancel-event    #(common-controller/->SetQueryParam :add-files nil)
                 :in-progress?    upload-progress
                 :spec :task/add-files}
      ^{:attribute :task/files}
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
                :cancel-event project-controller/->CloseDialog
                :save-event task-controller/->SaveTaskForm
                :delete (when id (task-controller/->DeleteTask id))
                :spec :task/new-task-form}
     ^{:xs 6 :attribute :task/group}
     [select/select-enum {:e! e! :attribute :task/group}]
     ^{:xs 6 :attribute :task/type}
     [select/select-enum {:e! e! :attribute :task/type
                          :enum/valid-for (:task/group task)
                          :full-value? true}]

     ;; Show "Send to THK" if task type has associated THK type
     (when (-> task :task/type :thk/task-type)
       ^{:xs 12 :attribute :task/send-to-thk?}
       [select/checkbox {}])


     ^{:attribute :task/description}
     [TextField {:full-width true :multiline true :rows 4 :maxrows 4}]

     ^{:attribute [:task/estimated-start-date :task/estimated-end-date] :xs 12}
     [date-picker/date-range-input {:start-label (tr [:fields :task/estimated-start-date])
                                    :end-label (tr [:fields :task/estimated-end-date])}]

     ^{:attribute [:task/actual-start-date :task/actual-end-date] :xs 12}
     [date-picker/date-range-input {:start-label (tr [:fields :task/actual-start-date])
                                    :end-label (tr [:fields :task/actual-end-date])}]


     ^{:attribute :task/assignee}
     [select/select-user {:e! e! :attribute :task/assignee}]]))

(defmethod project-navigator-view/project-navigator-dialog :add-task
  [{:keys [e! app] :as _opts} _dialog]
  [task-form e! (:edit-task-data app)])

(defmethod project-navigator-view/project-navigator-dialog :edit-task
  [{:keys [e! app] :as _opts}  _dialog]
  [task-form e! (:edit-task-data app)])

(defn task-page [e! {{:keys [add-document] :as _query} :query
                     {task-id :task :as _params} :params
                     new-document :new-document :as app}
                 project
                 breadcrumbs]
  [:<>
   [panels/modal {:open-atom (r/wrap (boolean add-document) :_)
                  :title     (tr [:task :add-document])
                  :on-close  (e! task-controller/->CloseAddDocumentDialog)}
    [add-files-form e! (:in-progress? new-document)]]

   [project-navigator-view/project-navigator-with-content
    {:e! e!
     :project project
     :app app
     :breadcrumbs breadcrumbs}

    [task-page-content e! app
     (project-model/task-by-id project task-id)]]])
