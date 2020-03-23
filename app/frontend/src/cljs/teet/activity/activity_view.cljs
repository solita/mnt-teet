(ns teet.activity.activity-view
  (:require [teet.ui.select :as select]
            [teet.ui.date-picker :as date-picker]
            [teet.localization :refer [tr]]
            [teet.ui.form :as form]
            teet.file.file-spec
            [teet.project.project-navigator-view :as project-navigator-view]
            [teet.project.project-style :as project-style]
            [herb.core :refer [<class]]
            [teet.activity.activity-controller :as activity-controller]
            [teet.project.project-controller :as project-controller]))

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
              :spec :document/new-activity-form}
   (when-not (:db/id activity)
     ^{:attribute :activity/name}
     [select/select-enum {:e! e! :attribute :activity/name :enum/valid-for lifecycle-type}])

   ^{:attribute [:activity/estimated-start-date :activity/estimated-end-date]}
   [date-picker/date-range-input {:start-label (tr [:fields :activity/estimated-start-date])
                                  :end-label (tr [:fields :activity/estimated-end-date])}]])

(defmethod project-navigator-view/project-navigator-dialog :edit-activity
  [{:keys [e! app]}  dialog]
  [activity-form e! (:edit-activity-data app) (:lifecycle-type dialog)])

(defn activity-page [e! app project breadcrumbs]
  [:div {:class (<class project-style/page-container)}
   [project-navigator-view/project-navigator-with-content
    {:e! e!
     :project project
     :app app
     :breadcrumbs breadcrumbs}

    [:div "tässäpä activity"]]])
