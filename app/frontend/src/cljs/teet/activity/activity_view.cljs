(ns teet.activity.activity-view
  (:require [teet.ui.select :as select]
            [teet.ui.date-picker :as date-picker]
            [teet.localization :refer [tr tr-enum]]
            [teet.ui.form :as form]
            [teet.ui.icons :as icons]
            teet.file.file-spec
            [teet.project.project-navigator-view :as project-navigator-view]
            [teet.project.project-style :as project-style]
            [teet.activity.activity-style :as activity-style]
            [herb.core :refer [<class]]
            [teet.activity.activity-controller :as activity-controller]
            [teet.project.project-controller :as project-controller]
            [teet.common.common-styles :as common-styles]
            [teet.ui.typography :as typography]
            [teet.ui.buttons :as buttons]
            [teet.project.project-model :as project-model]
            [teet.user.user-model :as user-model]
            [teet.ui.util :as util]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.url :as url]))

(defn activity-form [e! activity lifecycle-type]
  ;; Activity name (drop-down selector, a predefined list of activities: eskiisprojekt, eelprojekt, põhiprojekt, maade omandamine, ehitus)
  ;; Timeline (EstStart, EstEnd, assumptions entered only)
  ;; Status (drop-down selector, a predefined list of statuses)
  [form/form {:e! e!
              :value activity
              :on-change-event activity-controller/->UpdateActivityForm
              :save-event activity-controller/->SaveActivityForm
              :cancel-event project-controller/->CloseDialog
              :delete (e! activity-controller/->DeleteActivity)
              :spec :activity/new-activity-form}
   (when-not (:db/id activity)
     ^{:attribute :activity/name}
     [select/select-enum {:e! e! :attribute :activity/name :enum/valid-for lifecycle-type}])

   ^{:attribute [:activity/estimated-start-date :activity/estimated-end-date]}
   [date-picker/date-range-input {:start-label (tr [:fields :activity/estimated-start-date])
                                  :end-label (tr [:fields :activity/estimated-end-date])}]])

(defmethod project-navigator-view/project-navigator-dialog :edit-activity
  [{:keys [e! app]}  dialog]
  [activity-form e! (:edit-activity-data app) (:lifecycle-type dialog)])

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
    :unassigned theme-colors/gray))

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
   (util/mapc task-group (group-by :task/group tasks))])

(defn activity-content
  [e! params project]
  (let [activity (project-model/activity-by-id project (:activity params))]
    [:<>
     [activity-header e! activity]
     [project-management (:thk.project/owner project) (:thk.project/manager project)]
     [task-lists (:activity/tasks activity)]]))

(defn activity-page [e! {:keys [params] :as app} project breadcrumbs]
  [project-navigator-view/project-navigator-with-content
   {:e! e!
    :project project
    :app app
    :breadcrumbs breadcrumbs}

   [activity-content e! params project]])
