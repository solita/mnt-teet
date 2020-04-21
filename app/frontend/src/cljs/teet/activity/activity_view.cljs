(ns teet.activity.activity-view
  (:require [teet.ui.select :as select]
            [teet.ui.date-picker :as date-picker]
            [reagent.core :as r]
            [teet.authorization.authorization-check :refer [authorized? when-authorized]]
            [teet.localization :refer [tr tr-enum]]
            [teet.ui.panels :as panels]
            [teet.ui.material-ui :refer [Grid]]
            [teet.ui.form :as form]
            [teet.ui.icons :as icons]
            teet.file.file-spec
            [teet.project.project-specs :as project-specs]
            [teet.project.project-navigator-view :as project-navigator-view]
            [teet.activity.activity-style :as activity-style]
            [herb.core :refer [<class]]
            [teet.activity.activity-controller :as activity-controller]
            [teet.project.project-controller :as project-controller]
            [teet.common.common-styles :as common-styles]
            [teet.ui.typography :as typography]
            [teet.ui.buttons :as buttons]
            [teet.project.project-model :as project-model]
            [teet.user.user-model :as user-model]
            [teet.ui.util :as util :refer [mapc]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.url :as url]
            [teet.util.collection :as cu]
            [teet.project.task-model :as task-model]
            [teet.app-state]
            [taoensso.timbre :as log]))

(defn task-selection [{:keys [e! on-change selected activity-name]} task-groups task-types]
  [:div {:style {:max-height "70vh" :overflow-y :scroll}}
   (mapc (fn [g]
           [:div
            [typography/Heading2 {:style {:font-variant :all-small-caps
                                          :font-weight :bold}}
             (tr-enum g)]
            [:ul
             (mapc (fn [{id :db/ident :as t}]
                     [:div
                      [select/checkbox {:label (tr-enum t)
                                        :value (boolean (selected [(:db/ident g) id]))
                                        :on-change #(on-change
                                                     (cu/toggle selected [(:db/ident g) id]))}]])
                   (filter #(= (:db/ident g) (:enum/valid-for %)) task-types))]])
         (filter #(= (:db/ident activity-name)
                     (:enum/valid-for %))
                 (sort-by (comp task-model/task-group-order :db/ident)
                          task-groups)))])

(defn- task-groups-and-tasks [{e! :e! :as opts} task-groups]
  [select/with-enum-values {:e! e! :attribute :task/type}
   [task-selection opts task-groups]])

(defn create-activity-form [e! activity lifecycle-type]
  [form/form2 {:e! e!
               :value activity
               :on-change-event activity-controller/->UpdateActivityForm
               :cancel-event project-controller/->CloseDialog
               :save-event activity-controller/->SaveActivityForm
               :spec ::project-specs/activity}
   [Grid {:container true :style {:height "90%"} :spacing 3}
    [Grid {:item true :xs 4}

     [form/field :activity/name
      [select/select-enum {:e! e! :attribute :activity/name :enum/valid-for lifecycle-type}]]

     [form/field {:attribute [:activity/estimated-start-date :activity/estimated-end-date]}
      [date-picker/date-range-input {:row? false
                                     :start-label (tr [:fields :activity/estimated-start-date])
                                     :end-label (tr [:fields :activity/estimated-end-date])}]]]
    [Grid {:item true :xs 8}
     [select/with-enum-values {:e! e!
                               :attribute :task/group}
      [task-groups-and-tasks {:e! e!
                              :on-change #(e! (activity-controller/->UpdateActivityForm
                                               {:selected-tasks %}))
                              :selected (or (:selected-tasks activity) #{})}]]]

    [Grid {:item true :xs 12}
     [:div {:style {:display :flex :justify-content :flex-end}}
      [form/footer2]]]]])

(defn edit-activity-form [e! activity]
  [form/form {:e! e!
              :value activity
              :on-change-event activity-controller/->UpdateActivityForm
              :save-event activity-controller/->SaveActivityForm
              :cancel-event project-controller/->CloseDialog
              :delete (e! activity-controller/->DeleteActivity)
              :spec :activity/new-activity-form}

   ^{:attribute [:activity/estimated-start-date :activity/estimated-end-date]}
   [date-picker/date-range-input {:start-label (tr [:fields :activity/estimated-start-date])
                                  :end-label (tr [:fields :activity/estimated-end-date])}]])

(defmethod project-navigator-view/project-navigator-dialog :edit-activity
  [{:keys [e! app]} _dialog]
  [edit-activity-form e! (:edit-activity-data app)])

(defmethod project-navigator-view/project-navigator-dialog :new-activity
  [{:keys [e! app]} dialog]
  [create-activity-form e! (:edit-activity-data app) (:lifecycle-type dialog)])

(defn project-management
  [owner manager]
  [:div {:style {:display :flex}
         :class (<class common-styles/margin-bottom 1)}
   [:div {:style {:margin-right "2rem"}}
    [typography/SectionHeading (tr [:roles :owner])]
    [:span (user-model/user-name owner)]]
   [:div
    [typography/SectionHeading (tr [:roles :manager])]
    [:span (user-model/user-name manager)]]])

(defn activity-header
  [e! activity]
  [:div {:class (<class common-styles/heading-and-button-style)}
   [typography/Heading1 (tr-enum (:activity/name activity))]
   [buttons/button-secondary {:on-click #(e! (project-controller/->OpenEditActivityDialog (:db/id activity)))}
    (tr [:buttons :edit])]])

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
  [{:task/keys [type assignee status] :as task}]
  [:div {:class (<class activity-style/task-row-style)}
   [task-name-and-status task]
   [:div {:class (<class activity-style/task-row-column-style :center)}
    [:span (user-model/user-name assignee)]]
   [:div {:class (<class activity-style/task-row-column-style :end)}
    [:span (tr-enum status)]]])

(defn task-group
  [[group tasks]]
  [:div {:class (<class common-styles/margin-bottom 1)}
   [typography/Heading2 {:class (<class common-styles/margin-bottom 1)}  (tr-enum group)]
   [:div
    (util/mapc task-row tasks)]])

(defn task-lists
  [tasks]
  [:div
   (util/mapc task-group
              (sort-by (comp task-model/task-group-order :db/ident first)
                       (group-by :task/group tasks)))])

(defn approvals-button [e! params button-kw confirm-kw event-fn]
  (r/with-let [clicked? (r/atom false)]
    [:<>
     [buttons/button-primary {:on-click #(reset! clicked? true)
                              :style {:float :right}}
      [icons/action-check-circle]
      (tr [:activity button-kw])]
     (when @clicked?
       [panels/modal {:title (str (tr [:activity button-kw]) "?")
                      :on-close #(reset! clicked? false)}
        [:<>
         [:div {:style {:padding "1rem"
                        :margin-bottom "2rem"
                        :background-color theme-colors/gray-lightest}}
          (tr [:activity confirm-kw])]
         [:div {:style {:display :flex
                        :justify-content :flex-end}}
          [buttons/button-secondary {:on-click #(reset! clicked? false)}
           (tr [:buttons :cancel])]
          [buttons/button-primary {:on-click (e! event-fn params)
                                   :style {:margin-left "1rem"}}
           (tr [:buttons :confirm])]]]])]))

(defn approve-button [e! params]
  (approvals-button e! params :approve-activity :approve-activity-confirm activity-controller/->SubmitResults))

(defn submit-for-approval-button [e! params]
  (approvals-button e! params :submit-for-approval :submit-results-confirm activity-controller/->ApproveResults))

(defn all-tasks-completed? [activity]
  (let [statuses (->> activity :activity/tasks (mapv (comp :db/ident :task/status)))
        all-complete? (every? task-model/completed-statuses statuses)]
    (log/debug "all-complete? ->" all-complete? "because statuses=" statuses)
    all-complete?))


(defn activity-content
  [e! params project]
  (let [activity (project-model/activity-by-id project (:activity params))]
    [:<>
     [activity-header e! activity]
     [project-management (:thk.project/owner project) (:thk.project/manager project)]
     [task-lists (:activity/tasks activity)]     
     (when (and (authorized? @teet.app-state/user :activity/change-activity-status nil)
                (all-tasks-completed? activity)) 
       [submit-for-approval-button e! params])
     (when (and (authorized? @teet.app-state/user :activity/change-activity-status nil)
                (-> activity :activity/status :db/ident (= :activity.status/in-review))) 
       [approve-button e! params])]))

(defn activity-page [e! {:keys [params] :as app} project breadcrumbs]
  [project-navigator-view/project-navigator-with-content
   {:e! e!
    :project project
    :app app
    :breadcrumbs breadcrumbs}

   [activity-content e! params project]])
