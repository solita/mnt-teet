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
            [teet.user.user-model :as user-model]
            [teet.ui.icons :as icons]
            [teet.theme.theme-colors :as theme-colors]
            [teet.util.datomic :as du]))


(defn task-basic-info
  [e! {:task/keys [estimated-end-date assignee actual-end-date status] :as _task}]
  [:div {:class [(<class common-styles/flex-row-space-between) (<class common-styles/margin-bottom 1)]}
   [:div
    [typography/BoldGreyText (tr [:common :deadline])]
    [:span (format/date estimated-end-date)]]
   (when actual-end-date
     [:div
      [typography/BoldGreyText (tr [:fields :task/actual-end-date])]
      [:span (format/date actual-end-date)]])
   [:div
    [typography/BoldGreyText (tr [:fields :task/assignee])]
    [:span (user-model/user-name assignee)]]
   [:div
    (tr-enum status)]])

(defn submit-results-button [e! task]
  (r/with-let [clicked? (r/atom false)]
    [:<>
     [buttons/button-primary {:on-click #(reset! clicked? true)
                              :style {:float :right}}
      [icons/action-check-circle]
      (tr [:task :submit-results])]
     (when @clicked?
       [panels/modal {:title (str (tr [:task :submit-results]) "?")
                      :on-close #(reset! clicked? false)}
        [:<>
         [:div {:style {:padding "1rem"
                        :margin-bottom "2rem"
                        :background-color theme-colors/gray-lightest}}
          (tr [:task :submit-results-confirm]
              {:task (tr-enum (:task/type task))})]
         [:div {:style {:display :flex
                        :justify-content :flex-end}}
          [buttons/button-secondary {:on-click #(reset! clicked? false)}
           (tr [:buttons :cancel])]
          [buttons/button-primary {:on-click (e! task-controller/->SubmitResults)
                                   :style {:margin-left "1rem"}}
           (tr [:buttons :confirm])]]]])]))

(defn task-details
  [e! _params {:task/keys [description files] :as task}]
  [:div
   (when description
     [typography/Paragraph description])
   [task-basic-info e! task]
   [file-view/file-table files]
   (when (task-model/can-submit? task)
     [:<>
      [file-view/file-upload-button]
      [when-authorized :task/submit task
       [submit-results-button e! task]]])
   (when (task-model/reviewing? task)
     [when-authorized :task/review task
      [:div {:style {:display :flex :justify-content :space-between}}
       [buttons/button-warning {:on-click (e! task-controller/->Review :reject)}
        (tr [:task :reject-review])]
       [buttons/button-primary {:on-click (e! task-controller/->Review :accept)}
        (tr [:task :accept-review])]]])])

(defn- task-header
  [e! task]
  [:div {:class (<class common-styles/heading-and-button-style)}
   [typography/Heading1 (tr-enum (:task/type task))]
   [when-authorized :task/update
    task
    [buttons/button-secondary {:on-click #(e! (project-controller/->OpenEditTaskDialog (:db/id task)))}
     (tr [:buttons :edit])]]])

(defn- start-review [e!]
  (e! (task-controller/->StartReview))
  (fn [_]
    [:span]))

(defn task-page-content
  [e! app {status :task/status :as task} pm?]
  [:div
   (when (and pm? (du/enum= status :task.status/waiting-for-review))
     [when-authorized :task/start-review task
      [start-review e!]])
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


(defn task-form [_e! {:keys [initialization-fn]} {:keys [max-date min-date]}]
  ;;Task definition (under project activity)
  ;; Task type (a predefined list of tasks: topogeodeesia, geoloogia, liiklusuuring, KMH eelhinnang, loomastikuuuring, arheoloogiline uuring, muu)
  ;; Description (short description of the task for clarification, 255char, in case more detailed description is needed, it will be uploaded as a file under the task)
  ;; Responsible person (email)
  (when initialization-fn
    (initialization-fn))
  (fn [e! task]
    [form/form {:e! e!
                :value task
                :on-change-event task-controller/->UpdateEditTaskForm
                :cancel-event task-controller/->CancelTaskEdit
                :save-event task-controller/->SaveTaskForm
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
                                    :min-date min-date
                                    :max-date max-date
                                    :end-label (tr [:fields :task/estimated-end-date])}]

     ^{:attribute [:task/actual-start-date :task/actual-end-date] :xs 12}
     [date-picker/date-range-input {:start-label (tr [:fields :task/actual-start-date])
                                    :min-date min-date
                                    :max-date max-date
                                    :end-label (tr [:fields :task/actual-end-date])}]


     ^{:attribute :task/assignee}
     [select/select-user {:e! e! :attribute :task/assignee}]]))


(defn edit-task-form [_e! {:keys [initialization-fn]} {:keys [max-date min-date]}]
  (when initialization-fn
    (initialization-fn))
  (fn [e! {id :db/id send-to-thk? :task/send-to-thk? :as task}]
    [form/form {:e! e!
                :value task
                :on-change-event task-controller/->UpdateEditTaskForm
                :cancel-event task-controller/->CancelTaskEdit
                :save-event task-controller/->SaveTaskForm
                :delete (when (and id (not send-to-thk?))
                          (task-controller/->DeleteTask id))
                :spec :task/edit-task-form}

     ^{:attribute :task/description}
     [TextField {:full-width true :multiline true :rows 4 :maxrows 4}]

     ^{:attribute [:task/estimated-start-date :task/estimated-end-date] :xs 12}
     [date-picker/date-range-input {:start-label (tr [:fields :task/estimated-start-date])
                                    :min-date min-date
                                    :max-date max-date
                                    :end-label (tr [:fields :task/estimated-end-date])}]

     (when (not send-to-thk?)
       ^{:attribute [:task/actual-start-date :task/actual-end-date] :xs 12}
       [date-picker/date-range-input {:start-label (tr [:fields :task/actual-start-date])
                                      :min-date min-date
                                      :max-date max-date
                                      :end-label (tr [:fields :task/actual-end-date])}])
     ^{:attribute :task/assignee}
     [select/select-user {:e! e! :attribute :task/assignee}]]))

(defmethod project-navigator-view/project-navigator-dialog :add-task
  [{:keys [e! app project] :as _opts} _dialog]
  (let [activity-id (get-in app [:params :activity])
        activity (project-model/activity-by-id project activity-id)]
    (println "ACTIVITY " activity)
    [task-form e! (:edit-task-data app) {:max-date (:activity/estimated-end-date activity)
                                         :min-date (:activity/estimated-start-date activity)}]))

(defmethod project-navigator-view/project-navigator-dialog :edit-task
  [{:keys [e! app project] :as _opts}  _dialog]
  (let [activity-id (get-in app [:params :activity])
        activity (project-model/activity-by-id project activity-id)]
    [edit-task-form e! (:edit-task-data app) {:max-date (:activity/estimated-end-date activity)
                                              :min-date (:activity/estimated-start-date activity)}]))

(defn task-page [e! {{:keys [add-document] :as _query} :query
                     {task-id :task :as _params} :params
                     new-document :new-document
                     user :user :as app}
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
     (project-model/task-by-id project task-id)
     (= (:user/id user)
        (:user/id (:thk.project/manager project)))]]])
