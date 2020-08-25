(ns teet.task.task-view
  "View for a workflow task"
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.activity.activity-model :as activity-model]
            [teet.authorization.authorization-check :refer [when-authorized]]
            [teet.common.common-controller :as common-controller]
            [teet.common.common-styles :as common-styles]
            [teet.file.file-controller :as file-controller]
            [teet.file.file-view :as file-view]
            [teet.localization :refer [tr tr-enum]]
            [teet.project.project-controller :as project-controller]
            [teet.project.project-model :as project-model]
            [teet.project.project-navigator-view :as project-navigator-view]
            [teet.project.project-specs :as project-specs]
            [teet.project.task-model :as task-model]
            [teet.task.task-controller :as task-controller]
            teet.task.task-spec
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.buttons :as buttons]
            [teet.ui.date-picker :as date-picker]
            [teet.ui.file-upload :as file-upload]
            [teet.ui.form :as form]
            [teet.ui.format :as format]
            [teet.ui.icons :as icons]
            [teet.ui.rich-text-editor :as rich-text-editor]
            [teet.ui.material-ui :refer [Grid LinearProgress]]
            [teet.ui.panels :as panels]
            [teet.ui.select :as select]
            [teet.ui.tabs :as tabs]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.typography :as typography]
            [teet.ui.util :as util :refer [mapc]]
            [teet.user.user-model :as user-model]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]))

(defn- task-groups-for-activity [activity-name task-groups]
  (filter (comp (get activity-model/activity-name->task-groups activity-name #{})
                :db/ident)
          (sort-by (comp task-model/task-group-order :db/ident)
                   task-groups)))

(defn- task-selection [{:keys [e! on-change-selected on-change-sent existing selected sent-to-thk activity-name]
                        :or {existing #{}}}
                       task-groups task-types]
  [:div {:style {:max-height "70vh" :overflow-y :scroll}}
   (mapc (fn [g]
           [:div
            [typography/Heading2 {:style {:font-variant :all-small-caps
                                          :font-weight :bold}}
             (tr-enum g)]
            [:ul
             (mapc (fn [{id :db/ident :as t}]
                     [:div {:class (<class common-styles/flex-row)}
                      [:div {:class (herb/join (<class common-styles/flex-table-column-style 50)
                                               (<class common-styles/no-border))}
                       [select/checkbox {:label (tr-enum t)
                                         :disabled (existing id)
                                         :value (boolean (or (existing id)
                                                             (selected [(:db/ident g) id])))
                                         :on-change (when-not (existing id)
                                                      #(on-change-selected
                                                        (cu/toggle selected [(:db/ident g) id])))}]]
                      [:div {:class (herb/join (<class common-styles/flex-table-column-style 50)
                                               (<class common-styles/no-border))}
                       (when (and (:thk/task-type t)
                                  (selected [(:db/ident g) id]))
                         [select/checkbox {:label (tr [:fields :task/send-to-thk?])
                                           :value (boolean (sent-to-thk [(:db/ident g) id]))
                                           :on-change #(on-change-sent
                                                             (cu/toggle sent-to-thk [(:db/ident g) id]))}])]])
                   (filter #(= (:db/ident g) (:enum/valid-for %)) task-types))]])
         (task-groups-for-activity activity-name task-groups))])

(defn task-groups-and-tasks [{e! :e! :as opts} task-groups]
  [select/with-enum-values {:e! e! :attribute :task/type}
   [task-selection opts task-groups]])

(defn existing-uncompleted-tasks [activity]
  (or (->> activity
           :activity/tasks
           (remove task-model/completed?)
           (map (comp :db/ident :task/type))
           set)
      #{}))

(defn add-tasks-form [e! tasks activity {:keys [max-date min-date]}]
  (let [activity-name (-> activity :activity/name :db/ident)]
    [form/form2 {:e! e!
                 :value tasks
                 :on-change-event task-controller/->UpdateAddTasksForm
                 :cancel-event task-controller/->CloseAddTasksDialog
                 :save-event (partial task-controller/->SaveAddTasksForm
                                      activity-name)
                 :spec :activity/add-tasks}
     [Grid {:container true :style {:height "90%"} :spacing 3}
      [Grid {:item true :xs 4}
       [form/field {:attribute [:task/estimated-start-date :task/estimated-end-date]}
        [date-picker/date-range-input {:row? false
                                       :max-date max-date
                                       :min-date min-date
                                       :start-label (tr [:fields :task/estimated-start-date])
                                       :end-label (tr [:fields :task/estimated-end-date])}]]]

      [Grid {:item true :xs 8}
       [select/with-enum-values {:e! e!
                                 :attribute :task/group}
        [task-groups-and-tasks {:e! e!
                                :on-change-selected #(e! (task-controller/->UpdateAddTasksForm
                                                          {:activity/tasks-to-add %}))
                                :on-change-sent #(e! (task-controller/->UpdateAddTasksForm
                                                      {:sent-tasks %}))
                                :activity-name activity-name
                                :existing (existing-uncompleted-tasks activity)
                                :selected (or (:activity/tasks-to-add tasks) #{})
                                :sent-to-thk (or (:sent-tasks tasks) #{})}]]]

      [Grid {:item true :xs 12}
       [:div {:style {:display :flex :justify-content :flex-end}}
        [form/footer2]]]]]))

(defn task-basic-info
  [{:task/keys [estimated-end-date assignee actual-end-date status] :as _task}]
  [:div.task-basic-info {:class [(<class common-styles/flex-row-space-between) (<class common-styles/margin-bottom 1)]}
   [:div.task-basic-info-end-date
    [typography/BoldGreyText (tr [:common :deadline])]
    [:span.task-basic-info-value (format/date estimated-end-date)]]
   (when actual-end-date
     [:div.task-basic-info-end-date
      [typography/BoldGreyText (tr [:fields :task/actual-end-date])]
      [:span.task-basic-info-value (format/date actual-end-date)]])
   [:div.task-basic-info-assignee
    [typography/BoldGreyText (tr [:fields :task/assignee])]
    [:span.task-basic-info-value (user-model/user-name assignee)]]
   [:div.task-basic-info-status
    (tr-enum status)]])

(defn submit-results-button [e! task]
  (r/with-let [clicked? (r/atom false)]
    [:<>
     [buttons/button-primary {:on-click #(reset! clicked? true)
                              :style {:float :right}
                              :start-icon (r/as-element [icons/action-check-circle])}
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
  [:div.task-details
   (when description
     [typography/Paragraph description])
   [task-basic-info task]
   [file-view/file-table files]
   (when (task-model/can-submit? task)
     [:<>
      [file-view/file-upload-button e!]
      (when (seq files)
        [when-authorized :task/submit task
         [submit-results-button e! task]])])
   (when (task-model/reviewing? task)
     [when-authorized :task/review task
      [:div.task-review-buttons {:style {:display :flex :justify-content :space-between}}
       [buttons/button-warning {:on-click (e! task-controller/->Review :reject)}
        (tr [:task :reject-review])]
       [buttons/button-primary {:on-click (e! task-controller/->Review :accept)}
        (tr [:task :accept-review])]]])])

(defn- task-header
  [e! task]
  [:div.task-header {:class (<class common-styles/heading-and-action-style)}
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
  [:div.task-page
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
                 :cancel-event    #(common-controller/->SetQueryParam :add-document nil)
                 :in-progress?    upload-progress
                 :spec :task/add-files}
      ^{:attribute :task/files}
      [file-upload/files-field {}]]
     (when upload-progress
       [LinearProgress {:variant "determinate"
                        :value   upload-progress}])]))


(defn task-form [_e!
                 {:keys [initialization-fn]}
                 activity
                 {:keys [max-date min-date]}]
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
     [select/select-enum {:e! e!
                          :attribute :task/group
                          :sort-fn task-model/task-group-order
                          :values-filter (get activity-model/activity-name->task-groups
                                              (-> activity :activity/name :db/ident)
                                              #{})}]
     ^{:xs 6 :attribute :task/type}
     [select/select-enum {:e! e! :attribute :task/type
                          :enum/valid-for (or (:task/group task)
                                              :none-since-task-group-is-not-selected)
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
    [task-form e! (:edit-task-data app) activity
     {:max-date (:activity/estimated-end-date activity)
      :min-date (:activity/estimated-start-date activity)}]))

(defmethod project-navigator-view/project-navigator-dialog :add-tasks
  [{:keys [e! app project]} _dialog]
  (let [activity-id (get-in app [:add-tasks-data :db/id])
        activity (project-model/activity-by-id project activity-id)]
    [add-tasks-form e!
     (:add-tasks-data app)
     activity
     {:max-date (:activity/estimated-end-date activity)
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
  (let [activity-manager (cu/find-> project
                                    :thk.project/lifecycles some?
                                    :thk.lifecycle/activities (fn [{:activity/keys [tasks]}]
                                                                (du/find-by-id task-id tasks))
                                    :activity/manager)]
    [:<>
     [panels/modal {:max-width "md"
                    :open-atom (r/wrap (boolean add-document) :_)
                    :title (tr [:task :add-document])
                    :on-close (e! task-controller/->CloseAddDocumentDialog)}
      [add-files-form e! (:in-progress? new-document)]]

     [project-navigator-view/project-navigator-with-content
      {:e! e!
       :project project
       :app app
       :breadcrumbs breadcrumbs}

      [task-page-content e! app
       (project-model/task-by-id project task-id)
       (= (:db/id user)
          (:db/id activity-manager))]]]))
