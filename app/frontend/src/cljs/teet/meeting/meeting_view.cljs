(ns teet.meeting.meeting-view
  (:require [reagent.core :as r]
            [teet.project.project-navigator-view :as project-navigator-view]
            [teet.ui.breadcrumbs :as breadcrumbs]
            [teet.ui.typography :as typography]
            [teet.ui.text-field :refer [TextField]]
            [herb.core :refer [<class]]
            [teet.localization :refer [tr]]
            teet.meeting.meeting-specs
            [teet.ui.project-context :as project-context]
            [teet.task.task-style :as task-style]
            [teet.project.project-style :as project-style]
            [teet.ui.material-ui :refer [Link Collapse Paper Grid]]
            [teet.ui.buttons :as buttons]
            [teet.ui.panels :as panels]
            [teet.ui.form :as form]
            [teet.meeting.meeting-controller :as meeting-controller]
            [teet.ui.select :as select]
            [teet.ui.date-picker :as date-picker]))


(defn create-meeting-form
  [e! activity-id close-event form-atom]
  [:<>
   #_[:span (pr-str @form-atom)]                            ;debug form values
   [form/form {:e! e!
               :value @form-atom
               :on-change-event (form/update-atom-event form-atom merge)
               :cancel-event close-event
               :spec :meeting/form-data
               :save-event #(meeting-controller/->SubmitMeetingForm activity-id @form-atom close-event)}
    ^{:attribute :meeting/title}
    [TextField {}]
    ^{:attribute :meeting/location}
    [TextField {}]
    ^{:attribute [:meeting/start :meeting/end]}
    [date-picker/date-time-range-input {}]
    ^{:attribute :meeting/organizer}
    [select/select-user {:e! e!}]]])

(defn form-modal-button
  [{:keys [form-component button-component]} label]
  (r/with-let [open-atom (r/atom false)
               form-atom (r/atom {})
               open #(reset! open-atom true)
               close #(reset! open-atom false)
               close-event (form/reset-atom-event open-atom false)]
    [:<>
     [panels/modal {:max-width "md"
                    :open-atom open-atom
                    :title (tr [:meeting :add-meeting])
                    :on-close close}
      [form-component close-event form-atom]]
     [button-component
      {:on-click open}
      label]]))

(defn meetings-page-content
  [e! activity]
  [:div
   [:h1 (:tr [:meetings :meetings-title]) activity]
   [form-modal-button {:form-component (r/partial create-meeting-form e! activity)
                       :button-component buttons/rect-primary}
    (:tr [:meetings :add-meeting])]])

(defn activity-meetings-view
  "Page structure showing project navigator along with content."
  [e! {{:keys [activity]} :params :as app} project breadcrumbs]
  (let [[nav-w content-w] [3 6]]
    [project-context/provide
     {:project-id (:db/id project)}
     [:div.project-navigator-with-content {:class (<class project-style/page-container)}
      [:div
       [breadcrumbs/breadcrumbs breadcrumbs]]
      [typography/Heading1 (or (:thk.project/project-name project)
                               (:thk.project/name project))]
      [Paper {:class (<class task-style/task-page-paper-style)}
       [Grid {:container true
              :wrap :nowrap
              :spacing   0}
        [Grid {:item  true
               :xs nav-w
               :style {:max-width "400px"}}
         [project-navigator-view/project-navigator e! project (:stepper app) (:params app) true] ;;TODO create new meetings navigator
         ]
        [Grid {:item  true
               :xs content-w
               :style {:padding "2rem 1.5rem"
                       :overflow-y :auto
                       ;; content area should scroll, not the whole page because we
                       ;; want map to stay in place without scrolling it
                       }}
         [meetings-page-content e! activity]]
        [Grid {:item  true
               :xs :auto
               :style {:display :flex
                       :flex    1
                       :padding "1rem 1.5rem"}}
         [:h1 "participants"]                               ;; TODO create participants view
         ]]]]]))
