(ns teet.meeting.meeting-view
  (:require [reagent.core :as r]
            [teet.project.project-navigator-view :as project-navigator-view]
            [teet.ui.typography :as typography]
            [teet.ui.text-field :refer [TextField]]
            [herb.core :refer [<class]]
            [teet.localization :refer [tr tr-enum] :as localization]
            teet.meeting.meeting-specs
            [teet.ui.project-context :as project-context]
            [teet.ui.icons :as icons]
            [teet.task.task-style :as task-style]
            [teet.project.project-style :as project-style]
            [teet.ui.material-ui :refer [Paper Grid IconButton]]
            [teet.ui.buttons :as buttons]
            [teet.ui.form :as form]
            [teet.meeting.meeting-controller :as meeting-controller]
            [teet.ui.select :as select]
            [teet.ui.date-picker :as date-picker]
            [teet.ui.util :refer [mapc]]
            [teet.ui.itemlist :as itemlist]
            [teet.meeting.meeting-model :as meeting-model]
            [teet.ui.url :as url]
            [teet.common.common-styles :as common-styles]
            [teet.ui.common :as common]
            [teet.ui.format :as format]
            [teet.user.user-model :as user-model]
            [teet.ui.rich-text-editor :as rich-text-editor]
            [teet.project.project-menu :as project-menu]
            [teet.navigation.navigation-style :as navigation-style]
            [teet.project.project-model :as project-model]
            [teet.log :as log]
            [teet.ui.context :as context]))


(defn meeting-form
  [e! activity-id close-event form-atom]
  [:<>
   ;[:span (pr-str @form-atom)]                            ;debug form values
   [form/form (merge
                {:e! e!
                 :value @form-atom
                 :on-change-event (form/update-atom-event form-atom merge)
                 :cancel-event close-event
                 :spec :meeting/form-data
                 :save-event #(meeting-controller/->SubmitMeetingForm activity-id @form-atom close-event)}
                (when (:db/id @form-atom)
                  {:delete (meeting-controller/->DeleteMeeting activity-id (:db/id @form-atom) close-event)}))
    ^{:attribute :meeting/title}
    [TextField {}]
    ^{:attribute :meeting/location}
    [TextField {}]
    ^{:attribute [:meeting/start :meeting/end]}
    [date-picker/date-time-range-input {}]
    ^{:attribute :meeting/organizer}
    [select/select-user {:e! e!}]]])

(defn meetings-page-content
  [e! activity]
  [:div
   [typography/Heading1 (tr [:meeting :activity-meetings-title]
                            {:activity-name
                             (tr-enum (:activity/name activity))})]])

(defn activity-meetings-list
  [{:keys [e! dark-theme? disable-buttons? user rect-button]}
   {:keys [activity activity-id]}]
  (let [meetings (:activity/meetings activity)]
    [:<>
     (if (seq meetings)
       [:div
        (mapc
          (fn [[group meetings]]
            [:div.meeting-group {:style {:margin-bottom "0.5rem"}}
             [:ul {:class (<class project-navigator-view/ol-class)}
              [:li.meeting-group-label
               [:div
                [typography/SmallText {:style {:text-transform :uppercase
                                               :font-weight :bold}}
                 group]]]
              (doall
                (for [{:meeting/keys [start location] :as meeting} meetings]
                  ^{:key (str (:db/id meeting))}
                  [:li.meeting-group-task {:class (<class project-navigator-view/custom-list-indicator dark-theme?)}
                   [:div {:class (<class project-navigator-view/task-info)}
                    [url/Link {:page :meeting
                               :params {:activity activity-id
                                        :meeting (:db/id meeting)}
                               :class (<class project-navigator-view/stepper-button-style {:size "16px"
                                                                                           :open? false
                                                                                           :dark-theme? dark-theme?})}
                     (meeting-model/meeting-title meeting)]
                    [typography/SmallText (when dark-theme?
                                            {:style {:color "white"
                                                     :opacity "0.7"}})
                     (format/date start) " " location]]]))]])

          ;; group tasks by the task group
          (group-by (fn [meeting]
                      (if-let [meeting-start (:meeting/start meeting)]
                        (localization/localized-month-year meeting-start)
                        "No date for meeting"               ;; TODO add localization
                        ))
                    (sort-by :meeting/start meetings)))]
       [:div {:class (<class project-navigator-view/empty-section-style)}
        [typography/GreyText (tr [:meeting :no-meetings])]])
     [:div
      [:div.project-navigator-add-meeting

       [form/form-modal-button {:form-component [meeting-form e! activity-id]
                                :form-value {:meeting/organizer (select-keys user [:db/id
                                                                                   :user/id
                                                                                   :user/given-name
                                                                                   :user/family-name
                                                                                   :user/person-id])}
                                :modal-title (tr [:meeting :new-meeting-modal-title])
                                :button-component [rect-button {:size :small
                                                                :disabled disable-buttons?
                                                                :start-icon (r/as-element
                                                                              [icons/content-add])}
                                                   (tr [:meeting :new-meeting-button])]}]]]]))

(defn meeting-page-structure [e! app project
                              main-content right-panel-content]
  (let [[nav-w content-w] [3 6]]
    [project-context/provide
     {:project-id (:db/id project)
      :thk.project/id (:thk.project/id project)}
     [:div.project-navigator-with-content {:class (<class project-style/page-container)}

      [typography/Heading1 (or (:thk.project/project-name project)
                               (:thk.project/name project))]
      [Paper {:class (<class task-style/task-page-paper-style)}
       [Grid {:container true
              :wrap :nowrap
              :spacing   0}
        [Grid {:item true
               :xs nav-w
               :class (<class navigation-style/navigator-left-panel-style)}
         [project-menu/project-menu e! app project true]
         [project-navigator-view/project-navigator e! project app
          {:dark-theme? true
           :activity-link-page :activity-meetings
           :activity-section-content activity-meetings-list
           :add-activity? false}]]
        [Grid {:item  true
               :xs content-w
               :style {:padding "2rem 1.5rem"
                       :overflow-y :auto
                       ;; content area should scroll, not the whole page because we
                       ;; want map to stay in place without scrolling it
                       }}
         main-content]
        [Grid {:item  true
               :xs :auto
               :style {:display :flex
                       :flex    1
                       :padding "1rem 1.5rem"}}
         right-panel-content]]]]]))

(defn activity-meetings-view
  "Page structure showing project navigator along with content."
  [e! {{:keys [activity]} :params :as app} project]
  [meeting-page-structure e! app project
   [meetings-page-content e! (project-model/activity-by-id project activity)]
   [:h1 "participants"]])

(defn meeting-list [meetings]
  [:div
   [typography/Heading1 (tr [:meeting :project-meetings-title])]
   [itemlist/ItemList {}
    (for [{:meeting/keys [title location start end organizer number] :as meeting
           activity-id :activity-id
           id :db/id} meetings]
      ^{:key (str id)}
      [itemlist/Item {:label (str title
                                  (when number
                                    (str " #" number)))}
       [url/Link {:page :meeting
                  :params {:activity (str activity-id)
                           :meeting (str id)}}
        (str (meeting-model/meeting-title meeting) " "
             "(" location ") "
             (format/date-time start) " ")]])]])

(defn project-meetings-page-content [e! project]
  (let [meetings (mapcat
                  (fn [{:thk.lifecycle/keys [activities]}]
                    (mapcat (fn [{meetings :activity/meetings
                                  id :db/id}]
                              (map #(assoc % :activity-id id) meetings))
                            activities))
                  (:thk.project/lifecycles project))]

    [meeting-list meetings]))

(defn project-meetings-view
  "Project meetings"
  [e! app project]
  [meeting-page-structure e! app project
   [project-meetings-page-content e! project]
   [:h1 "participants"]])


(defn add-agenda-form [e! meeting close-event form-atom]
  [form/form {:e! e!
              :value @form-atom
              :on-change-event (form/update-atom-event form-atom merge)
              :cancel-event close-event
              :spec :meeting/agenda-form
              :save-event #(meeting-controller/->SubmitAgendaForm
                            meeting
                            (-> @form-atom
                                (update :meeting.agenda/body
                                        (fn [editor-state]
                                          (when editor-state
                                            (rich-text-editor/editor-state->markdown editor-state)))))
                            close-event)}
   ^{:attribute :meeting.agenda/topic}
   [TextField {}]
   ^{:attribute :meeting.agenda/responsible}
   [select/select-user {:e! e!}]
   ^{:attribute :meeting.agenda/body
     :before-save rich-text-editor/editor-state->markdown}
   [rich-text-editor/rich-text-field {}]])

(defn meeting-participant [on-remove {:meeting.participant/keys [role user] :as participant}]
  [:div.participant {:class (<class common-styles/flex-row)}
   [:div.participant-name {:class (<class common-styles/flex-table-column-style 45)}
    [user-model/user-name user]]
   [:div.participant-role {:class (<class common-styles/flex-table-column-style 45)}
    (tr-enum role)]
   [:div.participant-remove {:class (<class common-styles/flex-table-column-style 10)}
    (when on-remove
      [IconButton {:on-click #(on-remove participant)}
       [icons/content-clear]])]])


(defn- add-meeting-participant [e! meeting user]
  (r/with-let [form (r/atom nil)
               save-participant! #(meeting-controller/->AddParticipant meeting @form)]
    [:div.new-participant
     [:div
      [typography/BoldGreyText (tr [:meeting :add-person])]
      [form/form2 {:e! e!
                   :value @form
                   :on-change-event (form/update-atom-event form merge)
                   :save-event save-participant!}
       [form/field :meeting.participant/user
        [select/select-user {:e! e!}]]
       [form/field :meeting.participant/role
        [select/select-enum {:e! e! :attribute :meeting.participant/role}]]
       [form/footer2]]]]))

(defn meeting-participants [e! {:meeting/keys [participants organizer] :as meeting} user]
  (r/with-let [remove-participant! (fn [participant]
                                     (log/info "Remove participant:" participant)
                                     (e! (meeting-controller/->RemoveParticipant (:db/id participant))))]
    (let [can-edit-participants? (meeting-model/user-is-organizer-or-reviewer? user meeting)]
      [:div.meeting-participants
       [typography/Heading2 (tr [:meeting :participants-title])]
       [:div.participant-list
        [meeting-participant nil {:meeting.participant/user organizer
                                  :meeting.participant/role :meeting.participant.role/organizer}]
        (mapc (r/partial meeting-participant (and can-edit-participants? remove-participant!))
              participants)]
       (when can-edit-participants?
         [add-meeting-participant e! meeting user])])))

(defn meeting-page [e! {:keys [params user] :as app} {:keys [project meeting]}]
  [meeting-page-structure e! app project
   (let [{:meeting/keys [title number location start end organizer agenda]} meeting]
     [:div
      [:div {:class (<class common-styles/heading-and-action-style)}
       [typography/Heading2 title (when number (str " #" number))]
       [form/form-modal-button {:form-component [meeting-form e! (:activity params)]
                                :form-value meeting
                                :modal-title (tr [:meeting :edit-meeting-modal-title])
                                :button-component [buttons/button-secondary
                                                   {}
                                                   (tr [:buttons :edit])]}]]

      [common/labeled-data {:label (tr [:fields :meeting/location])
                            :data location}]
      [common/labeled-data {:label (tr [:fields :meeting/date-and-time])
                            :data (str (format/date start)
                                       " "
                                       (format/time* start)
                                       " - "
                                       (format/time* end))}]
      [common/labeled-data {:label (tr [:fields :meeting/organizer])
                            :data (user-model/user-name organizer)}]

      [itemlist/ItemList {:title (tr [:fields :meeting/agenda])}
       (for [{:meeting.agenda/keys [topic body responsible]} agenda]
         [:div.meeting-agenda
          [common/labeled-data {:label (tr [:fields :meeting.agenda/topic])
                                :data topic}]
          [common/labeled-data {:label (tr [:fields :meeting.agenda/responsible])
                                :data (user-model/user-name responsible)}]
          [:div
           [:p (tr [:fields :meeting.agenda/body]) ":"]
           [rich-text-editor/display-markdown body]]])]
      [form/form-modal-button {:form-component [add-agenda-form e! meeting]
                               :form-value {:meeting.agenda/responsible (select-keys user [:db/id
                                                                                           :user/id
                                                                                           :user/given-name
                                                                                           :user/family-name
                                                                                           :user/email
                                                                                           :user/person-id])}
                               :modal-title (tr [:meeting :new-agenda-modal-title])
                               :button-component [buttons/rect-primary {} (tr [:meeting :add-agenda-button])]}]])
   [context/consume :user
    [meeting-participants e! meeting]]])
