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
            [garden.color :refer [lighten as-hex]]
            [teet.task.task-style :as task-style]
            [teet.project.project-style :as project-style]
            [teet.ui.material-ui :refer [Paper Grid IconButton Divider Link]]
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
            [teet.ui.tabs :as tabs]
            [teet.log :as log]
            [teet.ui.context :as context]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.authorization-context :as authorization-context]
            [teet.ui.file-upload :as file-upload]
            [teet.file.file-controller :as file-controller]
            [teet.common.common-controller :as common-controller]
            [teet.file.file-view :as file-view]
            [cljs-time.core :as t]
            [cljs-time.coerce :as tc]
            [teet.ui.query :as query]
            [teet.util.datomic :as du]
            [clojure.set :as set]
            [clojure.string :as str]))


(defn update-meeting-warning?
  [show?]
  (when show?
    [:div {:class (<class common-styles/margin-bottom 1)}
     [typography/WarningText
      (tr [:meeting :reviews-invalidated-warning-text])]]))

(defn meeting-form
  [e! activity-id close-event form-atom]
  (let [editing? (:db/id @form-atom)]
    [:<>
     (when editing?
       [context/consume :reviews?
        [update-meeting-warning?]])
     [form/form (merge
                  {:e! e!
                   :value @form-atom
                   :on-change-event (form/update-atom-event form-atom merge)
                   :cancel-event close-event
                   :spec :meeting/form-data
                   :save-event #(meeting-controller/->SubmitMeetingForm activity-id @form-atom close-event)}
                  (when editing?
                    {:delete (meeting-controller/->CancelMeeting activity-id (:db/id @form-atom) close-event)}))
      ^{:attribute :meeting/title}
      [TextField {}]
      ^{:attribute :meeting/location}
      [TextField {}]
      ^{:attribute [:meeting/start :meeting/end]}
      [date-picker/date-time-range-input {}]
      ^{:attribute :meeting/organizer}
      [select/select-user {:e! e!}]]]))

(defn- file-attachments [{:keys [e! drag-container-id drop-message attach-to files]}]
  [project-context/consume
   (fn [{project-id :db/id}]
     [:<>
      [typography/BoldGreyText
       {:style {:margin "1rem 0"}}
       (tr [:common :files])]
      [file-view/file-table
       {:filtering? false
        :actions? true
        :no-link? true
        :attached-to attach-to
        :columns #{:suffix :download :delete :meta}
        :delete-action (fn [file]
                         (e! (file-controller/map->DeleteAttachment
                               {:file-id (:db/id file)
                                :success-message (tr [:document :file-deleted-notification])
                                :attached-to attach-to})))}
       files]

      [authorization-context/when-authorized :edit-meeting
       [file-upload/FileUpload
        {:id (str drag-container-id "-upload")
         :drag-container-id drag-container-id
         :drop-message drop-message
         :on-drop #(e! (file-controller/map->UploadFiles
                         {:files %
                          :project-id project-id
                          :attachment? true
                          :attach-to attach-to
                          :on-success common-controller/->Refresh}))}
        [buttons/button-primary
         {:start-icon (r/as-element [icons/file-cloud-upload])
          :component :span}
         (tr [:task :upload-files])]]]])])

(defn- meeting-decision-content [e! {id :db/id
                                     body :meeting.decision/body
                                     files :file/_attached-to}]
  [:div {:id (str "decision-" id)}
   [rich-text-editor/display-markdown body]
   [file-attachments {:e! e!
                      :drag-container-id (str "decision-" id)
                      :drop-message (tr [:drag :drop-to-meeting-decision])
                      :attach-to [:meeting-decision id]
                      :files files}]])

(defn decision-history-view
  [e! meetings]
  [:div
   [:div
    ;;[TextField {:label "Search"}]
    (doall
      (for [{reviews :review/_of
             :meeting/keys [title start end location agenda locked-at] :as meeting} meetings]
        ^{:key (str (:db/id meeting))}
        [common/hierarchical-container2
         {:open? true
          :heading [:div.meeting-heading {:style {:color :white}}
                    [typography/Heading3 title]
                    [:p (format/date-with-time-range start end) " " location]]
          :heading-button [url/Link
                           {:page :meeting :params {:meeting (str (:db/id meeting))}
                            :component buttons/button-secondary}
                           (tr [:dashboard :open])]
          :children (map-indexed
                      (fn [i {:meeting.agenda/keys [topic decisions] :as agenda}]
                        {:key (str (:db/id agenda))
                         :open? true
                         :heading [:div
                                   [typography/Heading3 topic]
                                   [:p (tr [:meeting :approved-by]
                                           {:approvers
                                            (str/join ", " (map
                                                             #(user-model/user-name (:review/reviewer %))
                                                             reviews))})
                                    [:strong " " (format/date locked-at)]]]
                         :children (map-indexed
                                     (fn [i decision]
                                       {:key (:db/id decision)
                                        :open? true
                                        :heading [typography/Heading3
                                                  (tr [:meeting :decision-topic] {:topic topic
                                                                                  :num (inc i)})]
                                        :content [meeting-decision-content e! decision]})
                                     decisions)
                         :color theme-colors/gray-light})
                      agenda)}
         theme-colors/gray]))]])

(defn activity-meetings-decisions
  [e! activity]
  [query/query {:e! e!
                :query :meeting/activity-decision-history
                :args {:activity-id (:db/id activity)}
                :simple-view [decision-history-view e!]}])

(defn meeting-information
  [{:meeting/keys [start location end title] :as meeting}]
  [:div {:class (<class common-styles/flex-row-space-between)}

   [:div {:style {:display :flex}}
    [:div {:style {:width "120px"
                   :margin-right "0.5rem"}}
     [:span (str (format/time* start)
                 "–"
                 (format/time* end))]]
    [:div [url/Link {:page :meeting :params {:meeting (str (:db/id meeting))}}
           title]]]
   [:div {:style {:text-align :right}}
    [typography/GreyText location]]])


(defn meetings-by-month-year
  [meetings]
  (let [month-year-meetings (group-by
                              (fn [meeting]
                                (if-let [meeting-start (:meeting/start meeting)]
                                  (format/localized-month-year meeting-start)
                                  "no date given"))
                              meetings)]
    (when (seq meetings)
      [:div {:class (<class common-styles/margin-bottom 1)}
       (doall
         (for [[month-year meetings] month-year-meetings]
           ^{:key month-year}
           [:div {:class (<class common-styles/margin-bottom 1)}
            [typography/Heading2 {:class (<class common-styles/margin-bottom 0.5)}
             month-year]
            (doall
              (for [[date meetings]
                    (->> meetings
                         (group-by
                           (fn [meeting]
                             (format/date (:meeting/start meeting))))
                         (sort-by                           ;; gets [date-string meetings] sort the dates
                           first
                           (fn [x y]
                             (t/after? (tc/from-date (format/date-string->date x))
                                       (tc/from-date (format/date-string->date y))))))]
                ^{:key date}
                [:div {:class (<class common-styles/margin-bottom 1)}
                 [typography/Heading3 {:class (<class common-styles/margin-bottom 0.5)}
                  date]
                 (doall
                   (for [meeting meetings]
                     ^{:key (:db/id meeting)}
                     [meeting-information meeting]))]))]))])))


(defn activity-meeting-history-view
  [e! meetings]
  [:div
   [meetings-by-month-year (sort-by
                             :meeting/start
                             (fn [x y]
                               (t/after? (tc/from-date x)
                                          (tc/from-date y)))
                             meetings)]])

(defn historical-activity-meetings
  [e! activity]
  [query/query {:e! e!
                :query :meeting/activity-meeting-history
                :args {:activity-eid (:db/id activity)}
                :simple-view [activity-meeting-history-view e!]}])


(defn meeting-with-time-slot
  [meetings]
  (let [today-date (tc/to-local-date (t/now))]
    (mapv (fn [{:meeting/keys [start] :as meeting}]
            (let [start-date (tc/from-date start)]
              (cond
                (t/equal? (tc/to-local-date start-date) today-date)
                (assoc meeting :meeting/time-slot :today)

                (and (= (t/week-number-of-year start-date)
                        (t/week-number-of-year today-date))
                     (= (t/week-year start-date)
                        (t/week-year today-date)))
                (assoc meeting :meeting/time-slot :this-week)
                :else
                (assoc meeting :meeting/time-slot :rest))))
          (sort-by :meeting/start meetings))))

(defn upcoming-activity-meetings
  [activity]
  (let [{:keys [today this-week rest]}
        (group-by
          (fn [meeting]
            (:meeting/time-slot meeting))
          (meeting-with-time-slot (:activity/meetings activity)))
        this-week-by-days-of-week
        (group-by
          (fn [meeting]
            (if-let [meeting-start (:meeting/start meeting)]
              (format/date meeting-start)
              "No date given"))
          this-week)]
    [:div
     [:div {:class (<class common-styles/margin-bottom 1)}
      [typography/Heading2 {:class (<class common-styles/margin-bottom 0.5)}
       (tr [:common :today])]
      (if (seq today)
        (doall
          (for [meeting today]
            ^{:key (:db/id meeting)}
            [meeting-information meeting]))
        [typography/GreyText (tr [:meeting :no-meetings])])]
     [:div {:class (<class common-styles/margin-bottom 1)}
      [typography/Heading2 {:class (<class common-styles/margin-bottom 0.5)}
       (tr [:common :this-week])]
      [:div
       (if (seq this-week-by-days-of-week)
         (doall
           (for [[date meetings] this-week-by-days-of-week]
             ^{:key date}
             [:div {:class (<class common-styles/margin-bottom 1)}
              [:div {:style {:display :flex}}
               [typography/Heading3 {:class (<class common-styles/margin-bottom 0.5)}
                (format/localized-day-of-the-week (format/date-string->date date))]
               [typography/GreyText {:style {:margin-left "0.5rem"}}
                date]]
              (doall
                (for [meeting meetings]
                  ^{:key (:db/id meeting)}
                  [meeting-information meeting]))]))
         [typography/GreyText (tr [:meeting :no-meetings])])]]
     [meetings-by-month-year rest]]))


(defn activity-meetings-page-content
  [e! activity {:keys [query] :as app}]
  [:div
   [typography/Heading1 (tr [:meeting :activity-meetings-title]
                            {:activity-name
                             (tr-enum (:activity/name activity))})]
   [tabs/tabs
    query
    [[:upcoming [upcoming-activity-meetings activity]]
     [:history [historical-activity-meetings e! activity]]
     [:decisions [activity-meetings-decisions e! activity]]]]])

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
                        (format/localized-month-year meeting-start)
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
                       :padding "1rem 1.5rem"
                       :background-color theme-colors/gray-lightest}}
         right-panel-content]]]]]))

(defn activity-meetings-view
  "Page structure showing project navigator along with content."
  [e! {{:keys [activity]} :params :as app} project]
  [meeting-page-structure e! app project
   [activity-meetings-page-content e! (project-model/activity-by-id project activity) app]
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


(defn agenda-form [e! meeting close-event form-atom]
  [:<>
   [context/consume :reviews?
    [update-meeting-warning?]]
   [form/form (merge {:e! e!
                      :value @form-atom
                      :on-change-event (form/update-atom-event form-atom merge)
                      :cancel-event close-event
                      :spec :meeting/agenda-form
                      :save-event #(meeting-controller/->SubmitAgendaForm
                                     meeting
                                     (-> @form-atom
                                         (update :meeting.agenda/body
                                                 (fn [editor-state]
                                                   (when (and editor-state (not (string? editor-state)))
                                                     (rich-text-editor/editor-state->markdown editor-state)))))
                                     close-event)}
                     (when-let [agenda-id (:db/id @form-atom)]
                       {:delete (meeting-controller/->DeleteAgendaTopic agenda-id close-event)}))
    ^{:attribute :meeting.agenda/topic}
    [TextField {:id "agenda-title"}]
    ^{:attribute :meeting.agenda/responsible}
    [select/select-user {:e! e!}]
    ^{:attribute :meeting.agenda/body}
    [rich-text-editor/rich-text-field {}]]])

(defn meeting-participant [on-remove {:participation/keys [role participant] :as participation}]
  [:div.participant {:class (<class common-styles/flex-row)}
   [:div.participant-name {:class (<class common-styles/flex-table-column-style 45)}
    [user-model/user-name participant]]
   [:div.participant-role {:class (<class common-styles/flex-table-column-style 45)}
    (tr-enum role)]
   [:div.participant-remove {:class (<class common-styles/flex-table-column-style 10)}
    (when on-remove
      [IconButton {:size :small
                   :on-click #(on-remove participation)}
       [icons/content-clear {:font-size :small}]])]])


(defn- add-meeting-participant [e! meeting]
  (r/with-let [initial-form {:participation/role :participation.role/participant}
               form (r/atom initial-form)
               add-non-teet-user! #(reset! form {:non-teet-user? true})]
    (let [save-participant! #(let [form-data @form]
                               (reset! form initial-form)
                               (meeting-controller/->AddParticipant meeting form-data))
          non-teet? (:non-teet-user? @form)]
      [:div.new-participant
       [:div
        [typography/BoldGreyText {:style {:margin-bottom "1rem"}}
         (tr [:meeting :add-person])]
        [context/consume :reviews?
         [update-meeting-warning?]]
        ;; Split in to 2 forms so we can have separate specs for each
        (if non-teet?
          ^{:key "non-teet-user"}
          [form/form2 {:e! e!
                       :value @form
                       :on-change-event (form/update-atom-event form merge)
                       :save-event save-participant!
                       :spec :meeting/add-non-teet-user-form
                       :cancel-fn #(reset! form initial-form)}
           [common/column-with-space-between 0.5
            [form/field :user/given-name
             [TextField {:placeholder (tr [:fields :user/given-name])}]]
            [form/field :user/family-name
             [TextField {:placeholder (tr [:fields :user/family-name])}]]
            [form/field :user/email
             [TextField {:placeholder (tr [:fields :user/email])}]]]
           [form/footer2]]
          ^{:key "teet-user"}
          [form/form2 {:e! e!
                       :value @form
                       :on-change-event (form/update-atom-event form merge)
                       :save-event save-participant!
                       :spec :meeting/add-teet-user-form}
           [common/column-with-space-between 0.5
            [form/field :participation/participant
             [select/select-user {:e! e!
                                  :after-results-action {:title (tr [:meeting :add-non-teet-participant])
                                                         :on-click add-non-teet-user!}}]]
            [form/field :participation/role
             [select/select-enum {:e! e!
                                  :show-empty-selection? false
                                  :attribute :participation/role}]]]

           [form/footer2]])]])))

(defn meeting-participants [e! {organizer :meeting/organizer
                                participations :participation/_in :as meeting} edit-rights? user]
  (r/with-let [remove-participant! (fn [participant]
                                     (log/info "Remove participant:" participant)
                                     (e! (meeting-controller/->RemoveParticipant (:db/id participant))))]
    (let [can-edit-participants? (and edit-rights? (meeting-model/user-is-organizer-or-reviewer? user meeting))]
      [:div.meeting-participants {:style {:flex 1}}
       [typography/Heading2 {:class (<class common-styles/margin-bottom 1)}
        (tr [:meeting :participants-title])]
       [:div.participant-list {:class (<class common-styles/margin-bottom 1)}
        [meeting-participant nil {:participation/participant organizer
                                  :participation/role :participation.role/organizer}]
        (mapc (r/partial meeting-participant (and can-edit-participants? remove-participant!))
              participations)]
       (when can-edit-participants?
         [:<>
          [add-meeting-participant e! meeting]
          [Divider {:style {:margin "1rem 0"}}]
          [typography/Heading3 {:class (<class common-styles/margin-bottom 1)}
           (tr [:meeting :notifications-title])]
          [:p {:class (<class common-styles/margin-bottom 1)}
           (tr [:meeting :notifications-help])]
          [:div {:class (<class common-styles/flex-align-center)}
           [buttons/button-primary {:on-click (e! meeting-controller/->SendNotifications meeting)}
            (tr [:buttons :send])]
           [typography/GreyText {:style {:margin-left "1rem"}}
            (tr [:meeting :send-notification-to-participants]
                {:count (inc (count participations))})]]])])))

(defn decision-form
  [e! agenda-eid close-event form-atom]
  [:<>
   [context/consume :reviews?
    [update-meeting-warning?]]
   [form/form (merge {:e! e!
                      :value @form-atom
                      :on-change-event (form/update-atom-event form-atom merge)
                      :cancel-event close-event
                      :spec :meeting/decision-form
                      :save-event #(meeting-controller/->SubmitDecisionForm
                                     agenda-eid
                                     (-> @form-atom
                                         (update :meeting.decision/body
                                                 (fn [editor-state]
                                                   (when (and editor-state (not (string? editor-state)))
                                                     (rich-text-editor/editor-state->markdown editor-state)))))
                                     close-event)}
                     (when (:db/id @form-atom)
                       {:delete (meeting-controller/->DeleteDecision (:db/id @form-atom) close-event)}))

    ^{:attribute :meeting.decision/body}
    [rich-text-editor/rich-text-field {}]]])

(defn add-decision-component
  [e! meeting agenda-topic]
  [:div {:class (<class common/hierarchical-container-style (as-hex (lighten theme-colors/gray-lighter 5)))}
   [form/form-modal-button {:form-component [decision-form e! (:db/id agenda-topic)]
                            :modal-title (tr [:meeting :new-decision-modal-title])
                            :button-component [buttons/button-primary {} (tr [:meeting :add-decision-button])]}]])


(defn- link [{:link/keys [type info]}]
  [:div "Link to " (name type) ": " (pr-str info)])

(defn- links
  "List links to other entities (like tasks).
  Shows input for adding new links."
  [{:keys [e! links from]}]
  [:div.links
   (mapc link links)
   (tr [:link :search])
   [select/select-search
    {:e! e!
     :query (fn [text]
              {:args {:lang @localization/selected-language
                      :text text
                      :from from}
               :query :meeting/search-link-task})
     :format-result (fn [{:task/keys [type assignee estimated-end-date]}]
                      [:div {:class (<class common-styles/flex-row-space-between)}
                       [:div (tr-enum type)]
                       [:div (user-model/user-name assignee)]
                       [:div (format/date estimated-end-date)]])}]])

(defn- meeting-agenda-content [e! {id :db/id
                                   body :meeting.agenda/body
                                   files :file/_attached-to
                                   links-from :link/_from}]

  [:div {:id (str "agenda-" id)}
   (when body
     [:div
      [rich-text-editor/display-markdown body]])

   [links {:e! e!
           :links links-from
           :from [:meeting-agenda id]}]

   [file-attachments {:e! e!
                      :drag-container-id (str "agenda-" id)
                      :drop-message (tr [:drag :drop-to-meeting-agenda])
                      :attach-to [:meeting-agenda id]
                      :files files}]])

(defn approval-status-symbol
  [status]
  (case status
    :review.decision/approved
    [icons/action-done {:style {:color "white"}
                        :class (<class common-styles/status-circle-style theme-colors/green)}]
    :review.decision/rejected
    [icons/content-clear {:style {:color "white"}
                        :class (<class common-styles/status-circle-style theme-colors/red)}]
    [:div {:class (<class common-styles/status-circle-style theme-colors/gray-lighter)}]))

(defn participant-approval-status
  [user {:review/keys [comment decision] :as review}]
  (let [review-decision (:db/ident decision)]
    [:div
     [:div.participant {:class (<class common-styles/flex-row)}
      [:div.participant-name {:class (<class common-styles/flex-table-column-style 30)}
       [approval-status-symbol review-decision]
       [:span {:style {:margin-left "0.2rem"}}
        [user-model/user-name user]]]
      [:div.participant-comment {:class (<class common-styles/flex-table-column-style 70 :space-between)}
       [:p comment]
       [typography/GreyText
        {:style {:margin-left "0.5rem"}}
        (format/date-time (:meta/created-at review))]]]]))

(defn reviews
  [{participations :participation/_in
    meeting-reviews :review/_of
    :meeting/keys [organizer]}]
  (let [participations (into [{:db/id "organizer"
                               :participation/participant organizer}]
                             (filter (comp #(du/enum= % :participation.role/reviewer)
                                              :participation/role))
                             participations)]
    [:div {:class (<class common-styles/padding-bottom 1)}
     [typography/Heading2 {:style {:margin "1.5rem 0"}} (tr [:meeting :approvals])]
     [:div
      (doall
        (for [{:participation/keys [participant] :as participation} participations]
          ^{:key (str (:db/id participation))}
          [participant-approval-status participant (some (fn [{:review/keys [reviewer] :as review}]
                                                           (when (= (:db/id reviewer) (:db/id participant))
                                                             review))
                                                         meeting-reviews)]))]]))

(defn review-form
  [e! meeting-id close-event form-atom]
  [:<>
   [form/form {:e! e!
               :value @form-atom
               :on-change-event (form/update-atom-event form-atom merge)
               :cancel-event close-event
               :spec :meeting/review-form
               :save-event #(meeting-controller/->SubmitReview
                              meeting-id
                              @form-atom
                              close-event)}
    ^{:attribute :review/comment}
    [TextField {:multiline true}]]])

(defn review-actions
  [e! meeting]
  (when (some
          #(seq (:meeting.agenda/decisions %))              ;; check that the meeting has decisions
          (:meeting/agenda meeting))
    [:div {:class (<class common-styles/padding-bottom 2)}
     [form/form-modal-button {:form-component [review-form e! (:db/id meeting)]
                              :form-value {:review/decision :review.decision/approved}
                              :modal-title (tr [:meeting :approve-meeting-modal-title])
                              :button-component [buttons/button-green {:style {:margin-right "1rem"}}
                                                 (tr [:meeting :approve-meeting-button])]}]
     [form/form-modal-button {:form-component [review-form e! (:db/id meeting)]
                              :form-value {:review/decision :review.decision/rejected}
                              :modal-title (tr [:meeting :reject-meeting-modal-title])
                              :button-component [buttons/button-warning {}
                                                 (tr [:meeting :reject-meeting-button])]}]]))

(defn meeting-details*
  [e! user {:meeting/keys [start end location agenda] :as meeting} rights]
  (let [edit? (get rights :edit-meeting)]
    [:div
     [common/basic-information-row [[(tr [:fields :meeting/date-and-time])
                                     (str (format/date start)
                                          " "
                                          (format/time* start)
                                          " - "
                                          (format/time* end))]
                                    [(tr [:fields :meeting/location])
                                     location]]]
     [:div {:style {:margin-bottom "1rem"}}
      (doall
       (for [{id :db/id
              :meeting.agenda/keys [topic responsible body decisions] :as agenda-topic} agenda]
         ^{:key id}
         [common/hierarchical-container2
          {:heading [:div.agenda-heading
                     [typography/Heading3 {:class (<class common-styles/margin-bottom "0.5")} topic]
                     [:span (user-model/user-name responsible)]]
           :heading-button (when edit?
                             [form/form-modal-button {:form-component [agenda-form e! meeting]
                                                      :id (str "edit-agenda-" id)
                                                      :form-value agenda-topic
                                                      :modal-title (tr [:meeting :edit-agenda-modal-title])
                                                      :button-component [buttons/button-secondary {}
                                                                         (tr [:buttons :edit])]}])
           :content [meeting-agenda-content e! agenda-topic]
           :children (map-indexed
                      (fn [i decision]
                        {:key (:db/id decision)
                         :heading [typography/Heading3 (tr [:meeting :decision-topic] {:topic topic
                                                                                       :num (inc i)})]
                         :heading-button (when edit?
                                           [form/form-modal-button
                                            {:form-component [decision-form e! (:db/id agenda-topic)]
                                             :form-value decision
                                             :modal-title (tr [:meeting :edit-decision-modal-title])
                                             :button-component [buttons/button-secondary {:size :small}
                                                                (tr [:buttons :edit])]}])
                         :content [meeting-decision-content e! decision]})
                      decisions)
           :after-children-component (when edit?
                                       [add-decision-component e! meeting agenda-topic])}
          theme-colors/gray-lighter]))]
     (when edit?
       [form/form-modal-button {:form-component [agenda-form e! meeting]
                                :id "add-agenda"
                                :form-value {:meeting.agenda/responsible (select-keys user [:db/id
                                                                                            :user/id
                                                                                            :user/given-name
                                                                                            :user/family-name
                                                                                            :user/email
                                                                                            :user/person-id])}
                                :modal-title (tr [:meeting :new-agenda-modal-title])
                                :button-component [buttons/button-primary {} (tr [:meeting :add-agenda-button])]}])
     [reviews meeting]
     (when edit?
       [review-actions e! meeting])]))

(defn meeting-details [e! user meeting]
  [authorization-context/consume
   [meeting-details* e! user meeting]])

(defn meeting-main-content
  [e! {:keys [params user query]} meeting]
  (let [{:meeting/keys [title number locked?]} meeting]
    [:div
     [:div {:class (<class common-styles/heading-and-action-style)}
      [typography/Heading2 {:class (<class common-styles/flex-align-center)}
       title (when number (str " #" number)) (when locked? [icons/action-lock])]
      [authorization-context/when-authorized :edit-meeting
       [form/form-modal-button {:form-component [meeting-form e! (:activity params)]
                                :form-value meeting
                                :modal-title (tr [:meeting :edit-meeting-modal-title])
                                :id "edit-meeting"
                                :button-component [buttons/button-secondary
                                                   {}
                                                   (tr [:buttons :edit])]}]]]
     [tabs/tabs
      query
      [[:details [meeting-details e! user meeting]]
       [:notes [:div [:h1 "notes"]]]]]]))


(defn meeting-page [e! {:keys [params user query] :as app} {:keys [project meeting]}]
  (let [edit-rights? (and (meeting-model/user-is-organizer-or-reviewer? user meeting)
                          (not (:meeting/locked? meeting)))]
    [authorization-context/with
     (set/union
       (when edit-rights?
         #{:edit-meeting}))
     [context/provide :reviews? (seq (:review/_of meeting))
      [meeting-page-structure e! app project
       [meeting-main-content e! app meeting]
       [context/consume :user
        [meeting-participants e! meeting edit-rights?]]]]]))
