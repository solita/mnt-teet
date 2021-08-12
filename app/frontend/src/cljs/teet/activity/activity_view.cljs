(ns teet.activity.activity-view
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [taoensso.timbre :as log]
            [teet.activity.activity-controller :as activity-controller]
            [teet.activity.activity-model :as activity-model]
            [teet.activity.activity-style :as activity-style]
            [teet.app-state]
            [teet.authorization.authorization-check :refer [authorized? when-authorized]]
            [teet.common.common-styles :as common-styles]
            teet.file.file-spec
            [teet.localization :refer [tr tr-enum]]
            [teet.project.project-controller :as project-controller]
            [teet.project.project-model :as project-model]
            [teet.project.project-navigator-view :as project-navigator-view]
            [teet.project.project-specs :as project-specs]
            [teet.project.task-model :as task-model]
            [teet.task.task-view :as task-view]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.buttons :as buttons]
            [teet.ui.date-picker :as date-picker]
            [teet.ui.form :as form]
            [teet.ui.icons :as icons]
            [teet.ui.material-ui :refer [Grid]]
            [teet.ui.panels :as panels]
            [teet.ui.select :as select]
            [teet.ui.typography :as typography]
            [teet.ui.url :as url]
            [teet.ui.util :as util]
            [teet.user.user-model :as user-model]))

(defn- names-of-unfinished-activities [existing-activities]
  (->> existing-activities
       (remove activity-model/finished?)
       (map #(-> % :activity/name :db/ident))
       set))

(defn create-activity-form [e! activity lifecycle-type existing-activities {:keys [max-date min-date]}]
  (let [unfinished-names (names-of-unfinished-activities existing-activities)
        remove-active (complement unfinished-names)]
    [form/form2 {:e! e!
                 :value activity
                 :on-change-event activity-controller/->UpdateActivityForm
                 :cancel-event project-controller/->CloseDialog
                 :save-event activity-controller/->SaveActivityForm
                 :spec ::project-specs/activity}
     [Grid {:container true :style {:height "90%"} :spacing 3}
      [Grid {:item true :xs 4}

       [form/field :activity/name
        [select/select-enum {:e! e!
                             :attribute :activity/name
                             :sort-fn activity-model/activity-order
                             :values-filter remove-active
                             :enum/valid-for lifecycle-type}]]

       [form/field :activity/manager
        [select/select-user {:e! e!}]]

       [form/field {:attribute [:activity/estimated-start-date :activity/estimated-end-date]}
        [date-picker/date-range-input {:row? false
                                       :max-date max-date
                                       :min-date min-date
                                       :start-label (tr [:fields :activity/estimated-start-date])
                                       :end-label (tr [:fields :activity/estimated-end-date])}]]]
      [Grid {:item true :xs 8}
       [select/with-enum-values {:e! e!
                                 :attribute :task/group}
        [task-view/task-groups-and-tasks {:e! e!
                                          :on-change-selected #(e! (activity-controller/->UpdateActivityForm
                                                                    {:selected-tasks %}))
                                          :on-change-sent #(e! (activity-controller/->UpdateActivityForm
                                                                {:sent-tasks %}))
                                          :activity-name (:activity/name activity)
                                          :selected (or (:selected-tasks activity) #{})
                                          :sent-to-thk (or (:sent-tasks activity) #{})}]]]

      [Grid {:item true :xs 12}
       [:div {:style {:display :flex :justify-content :flex-end}}
        [form/footer2]]]]]))

(defn edit-activity-form [e! activity project {:keys [max-date min-date]}]
  (let [user-authorized? (authorized? @teet.app-state/user :activity/delete-activity
                           {:project-id (:db/id project)
                            :entity activity})]
    [form/form {:e! e!
                :value activity
                :on-change-event activity-controller/->UpdateActivityForm
                :save-event activity-controller/->SaveActivityForm
                :cancel-event project-controller/->CloseDialog
                :delete (when user-authorized?
                          (activity-controller/->DeleteActivity (:db/id activity)))
                :spec :activity/new-activity-form}

     ^{:attribute :activity/manager}
     [select/select-user {:e! e!}]

     ^{:attribute [:activity/estimated-start-date :activity/estimated-end-date]}
     [date-picker/date-range-input {:start-label (tr [:fields :activity/estimated-start-date])
                                    :max-date max-date
                                    :min-date min-date
                                    :end-label (tr [:fields :activity/estimated-end-date])}]]))

(defmethod project-navigator-view/project-navigator-dialog :edit-activity
  [{:keys [e! app project]} _dialog]
  (let [lifecycle-id (get-in app [:stepper :lifecycle])
        lifecycle (project-model/lifecycle-by-id project lifecycle-id)]
    [edit-activity-form e! (:edit-activity-data app) project {:min-date (:thk.lifecycle/estimated-start-date lifecycle)
                                                      :max-date (:thk.lifecycle/estimated-end-date lifecycle)}]))

(defmethod project-navigator-view/project-navigator-dialog :new-activity
  [{:keys [e! app project]} dialog]
  (let [lifecycle-id (get-in app [:stepper :lifecycle])
        lifecycle (project-model/lifecycle-by-id project lifecycle-id)]
    [create-activity-form e!
     (:edit-activity-data app)
     (:lifecycle-type dialog)
     (:thk.lifecycle/activities lifecycle)
     {:min-date (:thk.lifecycle/estimated-start-date lifecycle)
      :max-date (:thk.lifecycle/estimated-end-date lifecycle)}]))

(defn project-management-and-status
  [owner manager status]
  [:div {:style {:display :flex}
         :class (<class common-styles/margin-bottom 1)}
   [:div {:style {:margin-right "2rem"}}
    [typography/SectionHeading (tr [:roles :owner])]
    [:span (user-model/user-name owner)]]
   [:div {:style {:margin-right "3rem"}}
    [typography/SectionHeading (tr [:roles :ta-project-manager])]
    [:span (user-model/user-name manager)]]
   [:div
    [typography/SectionHeading (tr [:activity :status])]
    [:span (tr-enum status)]]])

(defn activity-header
  [e! activity]
  [:div {:class (<class common-styles/heading-and-action-style)}
   [typography/Heading1 (tr-enum (:activity/name activity))]
   [when-authorized
    :activity/update
    activity
    [buttons/button-secondary {:data-cy "activity-edit-button"
                               :on-click #(e! (project-controller/->OpenEditActivityDialog (:db/id activity)))}
     (tr [:buttons :edit])]]])

(defn task-status-color
  [derived-status]
  (case derived-status
    :unassigned-past-start-date theme-colors/red
    :task-over-deadline theme-colors/red
    :close-to-deadline theme-colors/yellow
    :in-progress theme-colors/green
    :done theme-colors/green
    theme-colors/gray-lighter))

(defn task-name-and-status
  [{:task/keys [derived-status] :as task}]
  [:div {:class [(<class activity-style/task-row-column-style :start) (<class common-styles/flex-align-center)]}

   (if (= :done derived-status)
     [icons/action-done {:style {:color "white"}
                         :class (<class common-styles/status-circle-style (task-status-color derived-status))}]
     [:div {:class (<class common-styles/status-circle-style (task-status-color derived-status))}])
   [:div
    [url/Link {:page :activity-task :params {:task (str (:db/id task))}}
     (tr-enum (:task/type task))]]])

(defn task-row
  [{:task/keys [assignee status] :as task}]
  [:div {:class (<class activity-style/task-row-style)}
   [task-name-and-status task]
   [:div {:class (<class activity-style/task-row-column-style :center)}
    [:span (user-model/user-name assignee)]]
   [:div {:class (<class activity-style/task-row-column-style :end)}
    [:span (tr-enum status)]]])

(defn task-group
  [[group tasks]]
  [:div {:class (<class common-styles/margin-bottom 1)}
   [typography/Heading2 {:class (<class common-styles/margin-bottom 1)} (tr-enum group)]
   [:div
    (util/mapc task-row tasks)]])

(defn task-lists
  [tasks]
  [:div
   (util/mapc task-group
              (sort-by (comp task-model/task-group-order :db/ident first)
                       (group-by :task/group tasks)))])

(defn approvals-button [e! params button-kw confirm-kw icon button-component event-fn]
  (r/with-let [clicked? (r/atom false)]
    [:<>
     [button-component {:on-click #(reset! clicked? true)
                        :style {:margin-bottom "1rem"}
                        :start-icon (r/as-element [icon])}
      (tr [:activity-approval button-kw])]
     (when @clicked?
       [panels/modal {:title (str (tr [:activity-approval button-kw]) "?")
                      :on-close #(reset! clicked? false)}
        [:<>
         [:div {:style {:padding "1rem"
                        :margin-bottom "2rem"
                        :background-color theme-colors/gray-lightest}}
          (tr [:activity-approval confirm-kw])]
         [:div {:style {:display :flex
                        :justify-content :flex-end}}
          [buttons/button-secondary {:on-click #(reset! clicked? false)}
           (tr [:buttons :cancel])]
          (log/debug "approvals-button: params" params)
          [buttons/button-primary {:on-click #(do (reset! clicked? false)
                                                  (log/debug "on click: params" params)
                                                  ((e! event-fn params)))

                                   :style {:margin-left "1rem"}}
           (tr [:buttons :confirm])]]]])]))

(defn approve-button [e! params]
  (approvals-button e! params (:status params) :approve-activity-confirm icons/action-check-circle buttons/button-green activity-controller/->Review))

(defn reject-button [e! params]
  (approvals-button e! params (:status params) :reject-activity-confirm icons/navigation-cancel buttons/button-warning activity-controller/->Review))

(defn submit-for-approval-button [e! params]
  (approvals-button e! params :submit-for-approval :submit-results-confirm icons/action-check-circle buttons/button-green activity-controller/->SubmitResults))

(defn activity-content
  [e! params project]
  (let [activity (project-model/activity-by-id project (:activity params))
        not-reviewed-status? (complement activity-model/reviewed-statuses)
        status (-> activity :activity/status :db/ident)
        tasks-complete? (activity-model/all-tasks-completed? activity)]
    [:div.activity-content
     [activity-header e! activity]
     [project-management-and-status
      (:thk.project/owner project)
      (:activity/manager activity)
      (:activity/status activity)]
     [task-lists (:activity/tasks activity)]
     (if (and (authorized? @teet.app-state/user :activity/change-activity-status
                           {:project-id (:db/id project)
                            :entity activity})
              tasks-complete?
              (= (-> activity :activity/manager :db/id)
                 (-> @teet.app-state/user :db/id))
              (-> activity :activity/status :db/ident not-reviewed-status?))
       (when (not= status :activity.status/in-review)
         [submit-for-approval-button e! params])
       (when-not tasks-complete?
         [:div (tr [:activity :note-all-tasks-need-to-be-completed])]))

     (when (and (authorized? @teet.app-state/user :activity/change-activity-status
                             {:project-id (:db/id project)})
                (= (-> project :thk.project/owner :user/id)
                   (-> @teet.app-state/user :user/id)))
       (if (= status :activity.status/in-review)
           [:div {:class (<class common-styles/flex-space-between-wrap)}
            [approve-button e! (assoc params :status :activity.status/completed)]
            [reject-button e! (assoc params :status :activity.status/archived)]
            [reject-button e! (assoc params :status :activity.status/canceled)]]
           ;; else
           (when (not-reviewed-status? status)
             [:div (tr [:activity :waiting-for-submission])])))]))

(defn- export-activity-files [e! activity]
  (when (:activity-has-files? activity)
    [{:id "export-activity-files"
      :label #(tr [:file :export-files-zip :activity-button])
      :icon [icons/communication-email-outlined {:style {:color theme-colors/primary}}]
      :on-click #(e! (activity-controller/->ExportFiles (:db/id activity)))}]))

(defn activity-page [e! {:keys [params] :as app} project]
  [project-navigator-view/project-navigator-with-content
   {:e! e!
    :project project
    :app app
    :export-menu-items (export-activity-files e! (project-model/activity-by-id project (:activity params)))}

   [activity-content e! params project]])
