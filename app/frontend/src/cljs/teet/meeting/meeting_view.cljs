(ns teet.meeting.meeting-view
  (:require [reagent.core :as r]
            [teet.project.project-navigator-view :as project-navigator-view]
            [teet.ui.typography :as typography]
            [teet.ui.text-field :refer [TextField]]
            [herb.core :refer [<class]]
            [teet.localization :refer [tr tr-enum] :as localization]
            teet.meeting.meeting-specs
            [teet.ui.project-context :as project-context]
            [teet.project.project-view :as project-view]
            [teet.ui.icons :as icons]
            [re-svg-icons.feather-icons :as fi]
            [garden.color :refer [lighten as-hex]]
            [teet.ui.material-ui :refer [IconButton Divider ButtonBase Chip Collapse Popper]]
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
            [teet.project.project-model :as project-model]
            [teet.ui.tabs :as tabs]
            [teet.log :as log]
            [teet.ui.context :as context]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.authorization-context :as authorization-context]
            [teet.authorization.authorization-check :as authorization-check]
            [teet.ui.file-upload :as file-upload]
            [teet.file.file-controller :as file-controller]
            [teet.common.common-controller :as common-controller]
            [teet.file.file-view :as file-view]
            [cljs-time.core :as t]
            [cljs-time.coerce :as tc]
            [teet.ui.query :as query]
            [teet.util.datomic :as du]
            [clojure.set :as set]
            [clojure.string :as str]
            [teet.link.link-view :as link-view]
            [teet.ui.panels :as panels]
            [teet.util.date :as dateu]
            [teet.comments.comments-controller :as comments-controller]
            [teet.file.file-style :as file-style]
            [teet.meeting.meeting-style :as meeting-style]))

(def milliseconds-when-resent (* 1000 5))                  ;; 5 seconds

(defn- modified-since-last-seen
  "Show new indicator icon if entity has changed since last seen."
  [seen-at {:meta/keys [created-at modified-at]}]
  (let [changed-at (or modified-at created-at)]
    (when (and seen-at changed-at
               (> (.getTime changed-at) (.getTime seen-at)))
      [icons/alert-error-outline {:class "new-indicator"
                                  :style {:color theme-colors/orange}}])))

(defn update-meeting-warning?
  [show?]
  (when show?
    [:div {:class (<class common-styles/margin-bottom 1)}
     [typography/WarningText
      (tr [:meeting :reviews-invalidated-warning-text])]]))

(defn- meeting-merge [old new]
  (merge old new
         (when (contains? new :meeting/start)
           {:date-set? true})))

(defn meeting-form
  [{:keys [e! activity-id duplicate?]} close-event form-atom]
  (let [editing? (and (not duplicate?) (:db/id @form-atom))
        form-data @form-atom]
    [:<>
     (when editing?
       [context/consume :reviews?
        [update-meeting-warning?]])
     [form/form (merge
                  {:e! e!
                   :value form-data
                   :on-change-event (form/update-atom-event form-atom meeting-merge)
                   :cancel-event close-event
                   :spec :meeting/form-data
                   :save-event #(meeting-controller/->SubmitMeetingForm duplicate? activity-id form-data close-event)}
                  (when editing?
                    {:delete (meeting-controller/->CancelMeeting activity-id (:db/id form-data) close-event)}))
      ^{:attribute :meeting/title}
      [TextField {}]
      ^{:attribute :meeting/location}
      [TextField {}]
      ^{:attribute [:meeting/start :meeting/end]}
      [date-picker/date-time-range-input {:empty-date? (and duplicate?
                                                            (not (:date-set? form-data)))}]
      ^{:attribute :meeting/organizer}
      [select/select-user {:e! e!}]]]))

(defn- file-attachments [{:keys [e! drag-container-id allow-delete? drop-message attach-to files]}]
  [project-context/consume
   (fn [{project-id :db/id}]
     [:<>
      (when (seq files)
        [:<>
         [typography/Subtitle1
          {:style {:margin "1rem 0"}}
          (tr [:common :files])]
         [file-view/file-list2
          (merge {:filtering? false
                  :actions? true
                  :no-link? false
                  :title-downloads? true
                  :attached-to attach-to
                  :columns #{:suffix :download :delete :meta}}
                 (when allow-delete?
                   {:delete-action (fn [file]
                                     (e! (file-controller/map->DeleteAttachment
                                           {:file-id (:db/id file)
                                            :success-message (tr [:document :file-deleted-notification])
                                            :attached-to attach-to})))}))
          files]])

      [authorization-context/when-authorized :edit-meeting
       [context/consume :meeting
        (fn [meeting]
          [authorization-check/when-authorized :meeting/update meeting
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
            [buttons/button-secondary
             {:end-icon (r/as-element [icons/file-cloud-upload])
              :component :span
              :size :small
              :style {:margin "0.5rem 0"}}
             (tr [:task :upload-files])]]])]]])])

(defn- links-content [e! from links editable?]
  [link-view/links {:e! e!
                    :links links
                    :from from
                    :editable? editable?}])

(defn- meeting-decision-content [e! {id :db/id
                                     body :meeting.decision/body
                                     files :file/_attached-to
                                     links-from :link/_from}
                                 edit? editing?]
  [:div {:id (str "decision-" id)}
   (when-not editing?
     [rich-text-editor/display-markdown body])
   [file-attachments {:e! e!
                      :drag-container-id (str "decision-" id)
                      :allow-delete? edit?
                      :drop-message (tr [:drag :drop-to-meeting-decision])
                      :attach-to [:meeting-decision id]
                      :files files}]
   [links-content e! [:meeting-decision id] links-from edit?]])

(defn meeting-view-container
  ([param]
   [meeting-view-container param theme-colors/white])
  ([{:keys [text-color content open? heading heading-button children after-children-component
            on-toggle-open]
     :or {text-color :inherit
          open? false}}
    bg-color]
   (r/with-let [open? (r/atom open?)
                toggle-open! #(do
                                (when on-toggle-open
                                  (on-toggle-open))
                                (.stopPropagation %)
                                (swap! open? not))]
     [:div {:class [(<class meeting-style/meeting-container-heading)]}
      [:div {:class (<class common/hierarchical-heading-container2 bg-color text-color
                            (and
                             (or (seq children) after-children-component)
                             @open?))}
       [:div
        [:div {:class [(<class meeting-style/meeting-container-heading-box)]}
         [:div {:style {:flex-grow 1
                        :text-align :start}}
          heading]
         (when (and heading-button @open?)
           [:div {:style {:flex-grow 0}
                  :on-click (fn [e]
                              (.stopPropagation e))}
            heading-button])
         [:div {:style {:margin-left "1rem"}}
          [buttons/button-primary
           {:size :small
            :on-click toggle-open!}
           [(if @open? icons/hardware-keyboard-arrow-up icons/hardware-keyboard-arrow-down)]]
          ]]]
       (when content
         [Collapse {:in @open?
                    :mount-on-enter true}
          [:div {:style {:padding "1rem"}}
           content]])]

      (when (or children after-children-component)
        [Collapse {:in @open?
                   :mount-on-enter true}
         [:div {:class (<class common/hierarchical-child-container)
                :style {:border-top (str "1px solid " theme-colors/gray-lighter)}}
          (doall
           (for [child children]
             (if (vector? child)                            ;;Check if it's component and render that instaed
               child
               (with-meta
                 [meeting-view-container child (or (:color child) (as-hex (lighten bg-color 5)))]
                 {:key (:key child)}))))
          after-children-component]])])))

(defn decision-history-view
  [e! meetings]
  (if (seq meetings)
    [:div
     (doall
       (for [[activity grouped-meetings] (group-by #(get-in % [:activity/_meetings 0]) meetings)]
         ^{:key (str (:db/id activity))}
         [:<>
          (when (:activity/name activity)
            [:div
             {:style {:margin "1rem 0"}}
             [typography/Heading3 (tr-enum (:activity/name activity))]])
          (doall
            (for [{reviews :review/_of
                   :meeting/keys [title start number end location agenda locked-at] :as meeting} grouped-meetings]
              ^{:key (:db/id meeting)}
              [meeting-view-container
               {:open? true
                :heading [:div {:style {:color :black}}
                          [:div {:style {:display :flex}}
                           (let
                             [agenda-files (:file/_attached-to meeting)
                              agenda-links (:link/_from meeting)]
                             (if (or (seq agenda-files) (seq agenda-links))
                               [:div {:class (<class file-style/file-icon-container-style)
                                      :title (tr [:meeting :agenda-has-attachments])} [icons/content-link
                                                                                       {:size :small
                                                                                        :style {:color theme-colors/primary}}]]))
                           [typography/Heading3 {:class (<class common-styles/margin-bottom "0.3")}
                            title " " (str "#" number)]]
                          [typography/SmallText
                           [:b (format/date-with-time-range start end)]
                           [:span " " location]]]
                :heading-button [url/Link
                                 {:page :meeting :params (merge
                                                           {:meeting (str (:db/id meeting))}
                                                           (when-let [ac-id (:db/id activity)]
                                                             {:activity (str ac-id)}))
                                  :component buttons/button-secondary}
                                 (tr [:dashboard :open])]
                :children
                (doall
                  (for [topic agenda
                        d (:meeting.agenda/decisions topic)]
                    {:key (:db/id d)
                     :color theme-colors/white
                     :open? true
                     :heading [:div
                               [:div {:style {:display :flex}}
                                (let
                                  [decision-files (:file/_attached-to d)
                                   decision-links (:link/_from d)]
                                  (if (or (seq decision-files) (seq decision-links))
                                    [:div {:class (<class file-style/file-icon-container-style)
                                           :title (tr [:meeting :decision-has-attachments])} [icons/content-link
                                                                                              {:style {:color theme-colors/primary}}]]))
                                [typography/Heading3 {:class (<class common-styles/margin-bottom "0.3")}
                                 (tr [:meeting :decision-topic] {:topic (:meeting.agenda/topic topic)
                                                                 :num (:meeting.decision/number d)})]]
                               [typography/SmallText
                                (tr [:meeting :approved-by]
                                    {:approvers
                                     (str/join ", " (map
                                                      #(user-model/user-name (:review/reviewer %))
                                                      reviews))})
                                [:b " " (format/date locked-at)]]]
                     :content [meeting-decision-content e! d
                               false                        ;;in history view never allow editing
                               false                        ;; In history view we are never editing
                               ]}))}
               theme-colors/white]))]))]
    [typography/GrayText (tr [:meeting :no-decisions-found])]))

(defn activity-meetings-decisions
  [e! activity]
  (r/with-let [input-value (r/atom "")
               on-change (fn [e]
                           (reset! input-value (-> e .-target .-value)))]
    [:<>
     [TextField {:value @input-value
                 :placeholder (tr [:meeting :search-decisions])
                 :on-change on-change
                 :style {:display :block
                         :max-width "300px"
                         :margin-bottom "1rem"}}]
     [query/debounce-query
      {:e! e!
       :query :meeting/activity-decision-history
       :args {:activity-id (:db/id activity)
              :search-term @input-value}
       :simple-view [decision-history-view e!]}
      250]]))

(defn meeting-information
  [{:meeting/keys [start number location end title activity-name] :as meeting}]
  [:div {:class (<class common-styles/flex-row-space-between)}
   [:div {:style {:display :flex}}
    [:div {:style {:width "120px"
                   :margin-right "0.5rem"}}
     [:strong (str (format/time* start)
                 "–"
                 (format/time* end))]]
    [:div {:class (<class common-styles/margin-bottom 0.5)}
     [url/Link {:page :meeting :params (merge
                                         {:meeting (str (:db/id meeting))}
                                         (when-let [activity-id (get-in meeting [:activity/_meetings 0 :db/id])]
                                           {:activity (str activity-id)}))}
      (str title (when number
                   (str " #" number)))]
     (when activity-name
       [typography/SmallText
        (tr-enum activity-name)])]]
   [:div {:style {:text-align :right}}
    [typography/GrayText location]]])


(defn meetings-by-month-year
  [meetings historical-order?]
  (let [month-year-meetings (group-by
                              (fn [meeting]
                                (if-let [meeting-start (:meeting/start meeting)]
                                  (format/localized-month-year meeting-start)
                                  "no date given"))
                              meetings)
        date-compare-fn (if historical-order?
                          t/after?
                          t/before?)]
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
                             (date-compare-fn (tc/from-date (format/date-string->date x))
                                              (tc/from-date (format/date-string->date y))))))]
                ^{:key date}
                [:div {:class (<class common-styles/margin-bottom 1)}
                 [typography/Heading3 {:class (<class common-styles/margin-bottom 0.5)}
                  (str/capitalize (format/localized-day-of-the-week (format/date-string->date date))) " " date]
                 (doall
                   (for [meeting meetings]
                     ^{:key (:db/id meeting)}
                     [meeting-information meeting]))]))]))])))


(defn meeting-history-view
  [meetings]
  [:div
   [meetings-by-month-year (->> meetings
                                (sort-by
                                  :meeting/start)
                                reverse)
    true]])

(defn historical-activity-meetings
  [e! activity]
  [query/query {:e! e!
                :query :meeting/activity-meeting-history
                :args {:activity-id (:db/id activity)}
                :simple-view [meeting-history-view]}])

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

(defn upcoming-meetings
  [meetings]
  (let [{:keys [today this-week rest]}
        (group-by
          (fn [meeting]
            (:meeting/time-slot meeting))
          (meeting-with-time-slot meetings))
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
        [typography/GrayText (tr [:meeting :no-meetings])])]
     [:div {:class (<class common-styles/margin-bottom 1)}
      [typography/Heading2 {:class (<class common-styles/margin-bottom 0.5)}
       (tr [:common :this-week])]
      [:div
       (if (seq this-week-by-days-of-week)
         (doall
           (for [[date meetings] this-week-by-days-of-week]
             ^{:key date}
             [:div {:class (<class common-styles/margin-bottom 1)}
              [:div {:class (<class common-styles/flex-align-center)}
               [typography/Heading3 {:class (<class common-styles/margin-bottom 0.5)}
                (str/capitalize (format/localized-day-of-the-week (format/date-string->date date)))]
               [typography/GrayText {:style {:margin-left "0.5rem"}}
                date]]
              (doall
                (for [meeting meetings]
                  ^{:key (:db/id meeting)}
                  [meeting-information meeting]))]))
         [typography/GrayText (tr [:meeting :no-meetings])])]]
     [meetings-by-month-year rest false]]))

(defn activity-meetings-page-content
  [e! {:keys [query] :as app} activity]
  [:div
   [typography/Heading1 (tr [:meeting :activity-meetings-title]
                            {:activity-name
                             (tr-enum (:activity/name activity))})]
   [tabs/tabs
    query
    [[:upcoming [upcoming-meetings (:activity/meetings activity)]]
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
                [typography/SmallGrayText {:style {:text-transform :uppercase
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
                    [typography/SmallGrayText (when dark-theme?
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
        [typography/GrayText (tr [:meeting :no-meetings])]])
     [:div
      [:div.project-navigator-add-meeting

       [form/form-modal-button {:form-component [meeting-form {:e! e! :activity-id activity-id}]
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
  [project-view/project-full-page-structure
   {:e! e!
    :app app
    :project project
    :main main-content
    :right-panel right-panel-content
    :project-navigator {:activity-section-content activity-meetings-list}}])

(defn activity-meetings-view
  "Page structure showing project navigator along with content."
  [e! {{:keys [activity]} :params :as app} project]
  [meeting-page-structure e! app project
   [activity-meetings-page-content e! app (project-model/activity-by-id project activity)]
   nil])

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

(defn upcoming-project-meetings
  [project]
  (let [meetings (->> project
                     :thk.project/lifecycles
                     (mapcat :thk.lifecycle/activities)
                     (mapcat (fn [activity]
                               (map
                                 #(assoc % :meeting/activity-name (:activity/name activity))
                                 (:activity/meetings activity)))))]
    [:<>
     [upcoming-meetings meetings]]))

(defn project-meeting-history
  [e! project]
  [query/query {:e! e!
                :query :meeting/project-meeting-history
                :args {:project-id (:db/id project)}
                :simple-view [meeting-history-view]}])

(defn project-decisions
  [e! project]
  (r/with-let [input-value (r/atom "")
               on-change (fn [e]
                           (reset! input-value (-> e .-target .-value)))]
    [:<>
     [TextField {:value @input-value
                 :placeholder (tr [:meeting :search-decisions])
                 :on-change on-change
                 :style {:display :block
                         :max-width "300px"
                         :margin-bottom "1rem"}}]
     [query/debounce-query
      {:e! e!
       :query :meeting/project-decision-history
       :args {:project-id (:db/id project)
              :search-term @input-value}
       :simple-view [decision-history-view e!]}
      250]]))

(defn project-meetings-page-content [e! {query :query :as app} project]
  [:<>
   [typography/Heading1 (tr [:meeting :project-meetings-title])]
   [tabs/tabs
    query
    [[:upcoming [upcoming-project-meetings project]]
     [:history [project-meeting-history e! project]]
     [:decisions [project-decisions e! project]]]]])

(defn project-meetings-view
  "Project meetings"
  [e! app project]
  [meeting-page-structure e! app project
   [project-meetings-page-content e! app project]
   nil])


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
                                     (form/to-value @form-atom)
                                     close-event)}
                     (when-let [agenda-id (:db/id @form-atom)]
                       {:delete (meeting-controller/->DeleteAgendaTopic agenda-id close-event)}))
    ^{:attribute :meeting.agenda/topic :xs 6}
    [TextField {:id "agenda-title"}]
    ^{:attribute :meeting.agenda/responsible :xs 6}
    [select/select-user {:e! e!}]
    ^{:attribute :meeting.agenda/body}
    [rich-text-editor/rich-text-field {}]]])

(defn meeting-user-row [{:keys [actions] :as _opts} {:participation/keys [role participant] :as participation}]
  [:div.participant {:class (<class common-styles/flex-row)}
   [:div.participant-name {:class (<class common-styles/flex-table-column-style 45)}
    [:span (user-model/user-name participant)]]
   [:div.participant-role {:class (<class common-styles/flex-table-column-style 55 :space-between)}
    [:span (tr-enum role)]
    [:div
     actions]]])

(defn meeting-participation-row
  [{:keys [remove-participant change-absence confirm-changes?]}
   {:participation/keys [absent? role] :as participation}]
  (let [show-remove? (and remove-participant (not (= (:db/ident role) :participation.role/organizer)))
        icon-button [IconButton (merge {:size :small}
                                       (when (not confirm-changes?)
                                         {:on-click #(change-absence participation (not absent?))})
                                       (when (not show-remove?)
                                         {:style {:margin-right "26px"}} ;;size of the remove button
                                         ))
                     (if absent?
                       [icons/navigation-arrow-upward {:font-size :small}]
                       [icons/navigation-arrow-downward {:font-size :small}])]]
    [meeting-user-row {:actions [:div {:class (<class common-styles/flex-row)}
                                 (when change-absence
                                   (if confirm-changes?
                                     [buttons/button-with-confirm
                                      {:action #(change-absence participation (not absent?))
                                       :modal-title (tr [:meeting (if absent? :move-to-participants-modal-title
                                                                              :move-to-absentees-modal-title)])
                                       :confirm-button-text (tr [:meeting :confirm-move])
                                       :modal-text (tr [:meeting (if absent? :move-to-participants-modal-body
                                                                             :move-to-absentees-modal-body)]
                                                       {:participant
                                                        (user-model/user-name
                                                          (:participation/participant participation))})}
                                      icon-button]
                                     icon-button))
                                 (when show-remove?
                                   [buttons/button-with-confirm
                                    {:action #(remove-participant participation)
                                     :modal-title (tr [:meeting :remove-participant-confirm-title])
                                     :confirm-button-text (tr [:meeting :remove-participant-confirm-button])
                                     :modal-text (tr [:meeting :remove-participant-modal-content]
                                                     {:participant
                                                      (user-model/user-name
                                                        (:participation/participant participation))})}
                                    [IconButton {:size :small
                                                 :title "Remove participant from meeting"}
                                     [icons/content-clear {:font-size :small}]]])]}
     participation]))


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
        [context/consume :reviews?
         [update-meeting-warning?]]
        [typography/BoldGrayText {:style {:margin-bottom "1rem"}}
         (tr [:meeting :add-person])]
        ;; Split in to 2 forms so we can have separate specs for each
        (if non-teet?
          ^{:key "non-teet-user"}
          [form/form2 {:e! e!
                       :value @form
                       :on-change-event (form/update-atom-event form merge)
                       :save-event save-participant!
                       :spec :meeting/add-non-teet-user-form
                       :cancel-fn #(reset! form initial-form)}
           [form/field :user/given-name
            [TextField {:placeholder (tr [:fields :user/given-name])}]]
           [form/field :user/family-name
            [TextField {:placeholder (tr [:fields :user/family-name])}]]
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

(defn meeting-participants [e! {participations :participation/_in :as meeting} edit-rights?]
  (r/with-let [remove-participant! (fn [participation]
                                     (log/info "Remove participation:" participation)
                                     (e! (meeting-controller/->RemoveParticipant (:db/id participation))))
               change-absence (fn [participation absent?]
                                  (log/info "Change absent status of" participation " to " absent?)
                                  (e! (meeting-controller/->ChangeAbsentStatus (:db/id participation) absent?)))]
    (let [participations (sort-by
                           meeting-model/role-id-name
                           participations)
          participating-teet-users (filter #(get-in % [:participation/participant :user/id]) participations)
          attendees (filterv
                      #(not (:participation/absent? %))
                      participations)
          absentees (filterv
                      :participation/absent?
                      participations)
          has-reviews? (boolean (seq (:review/_of meeting)))
          notifications-sent-recently? (> milliseconds-when-resent (- (js/Date.now) (:meeting/notifications-sent-at meeting)))]
      [:div.meeting-participants {:style {:flex 1}}
       [typography/Heading2 {:class (<class common-styles/margin-bottom 1)}
        (tr [:meeting :participants-title])]
       [:div.participant-list {:class (<class common-styles/margin-bottom 1)}
        (doall
          (for [participation attendees]
            ^{:key (:db/id participation)}
            [meeting-participation-row {:confirm-changes? has-reviews?
                                        :remove-participant (when edit-rights?
                                                              remove-participant!)
                                        :change-absence (when edit-rights?
                                                          change-absence)}
             participation]))]
       (when edit-rights?
         [:<>
          [add-meeting-participant e! meeting]
          [Divider {:style {:margin "1rem 0"}}]])
       [:div
        [typography/Heading2 {:class (<class common-styles/margin-bottom 1)}
         (tr [:meeting :absentees-title])]
        (if (seq absentees)
          (doall
            (for [participation absentees]
              ^{:key (:db/id participation)}
              [meeting-participation-row {:confirm-changes? has-reviews?
                                          :remove-participant (when edit-rights?
                                                                remove-participant!)
                                          :change-absence (when edit-rights?
                                                            change-absence)}
               participation]))
          [typography/GrayText (tr [:meeting :no-absentees])])]
       (when edit-rights?
         [:div.notification {:style {:margin "1rem 0"}}
          [typography/Heading2 {:class [(<class common-styles/margin-bottom 1)
                                        (<class common-styles/flex-row)]}
           (tr [:meeting :notifications-title])
           [typography/SmallGrayText {:style {:margin-left "1rem"}}
            (tr [:meeting :send-notification-to-participants]
                {:count (dec (count participations))})]]
          [:div {:class (<class common-styles/margin-bottom 1)}
           (if (or (nil? (:meeting/notifications-sent-at meeting))
                   (dateu/date-after? (:meta/modified-at meeting)
                                      (:meeting/notifications-sent-at meeting)))
             [common/info-box {:variant :warning
                               :icon [fi/alert-triangle]
                               :title (tr [:meeting :notifications-not-sent])
                               :content (tr [:meeting :contents-changed])}]
             (if notifications-sent-recently?
               [common/info-box {:variant :success
                                 :title (tr [:meeting :notifications-sent-resent])
                                 :content (str (tr [:meeting :latest-notifications-were-sent])
                                               "\n"
                                               (format/date-time (:meeting/notifications-sent-at meeting)))}]
               [common/info-box {:variant :info
                                 :title (tr [:meeting :latest-notifications-sent])
                                 :content (format/date-time (:meeting/notifications-sent-at meeting))}]))]
          [:div
           (if (= (count participating-teet-users) 1)
             [typography/WarningText (tr [:meeting :not-enough-participants])]
             [:div {:style {:text-align :right}}
              [buttons/button-primary {:on-click (e! meeting-controller/->SendNotifications (:db/id meeting))}
               (tr [:buttons :send])]])]])])))

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
                                     (form/to-value @form-atom)
                                     close-event)}
                     (when (:db/id @form-atom)
                       {:delete (meeting-controller/->DeleteDecision (:db/id @form-atom) close-event)}))

    ^{:attribute :meeting.decision/body
      :validate #(rich-text-editor/validate-rich-text-form-field-not-empty %)}
    [rich-text-editor/rich-text-field {}]]])

(defn- add-decision-component
  [e! agenda-topic]
  (r/with-let [[pfrom pto] (common/portal)]
    [:div {:class (<class common/hierarchical-container-style (as-hex (lighten theme-colors/white 5)))}
     [form/form-container-button
      {:form-component [decision-form e! (:db/id agenda-topic)]
       :modal-title (tr [:meeting :new-decision-modal-title])
       :container pfrom
       :button-component [buttons/button-primary {} (tr [:meeting :add-decision-button])]}]
     [pto]]))


(defn- meeting-agenda-content [e! {id :db/id
                                   body :meeting.agenda/body
                                   files :file/_attached-to
                                   links-from :link/_from}
                               edit-right? editing?]

  [:div {:id (str "agenda-" id)}
   (when (and body (not editing?))
     [:div
      [rich-text-editor/display-markdown body]])

   [file-attachments {:e! e!
                      :drag-container-id (str "agenda-" id)
                      :drop-message (tr [:drag :drop-to-meeting-agenda])
                      :attach-to [:meeting-agenda id]
                      :files files
                      :allow-delete? edit-right?}]
   [links-content e! [:meeting-agenda id] links-from edit-right?]])

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
       (if comment
         [:p comment]
         [typography/GrayText {:style {:font-style :italic}}
          (tr [:meeting :no-review-comment])])
       [typography/GrayText
        {:style {:margin-left "0.5rem"}}
        (format/date-time (:meta/created-at review))]]]]))

(defn reviews
  [{participations :participation/_in
    meeting-reviews :review/_of}]
  (let [participations (->> (filter (comp #(not (du/enum= % :participation.role/participant))
                                          :participation/role)
                                    participations)
                            (sort-by
                              meeting-model/role-id-name))]
    [:div {:class (<class common-styles/padding-bottom 1)}
     [typography/Heading2 {:style {:margin "1.5rem 0"}} (tr [:meeting :approvals])]
     [:div
      (doall
        (for [{:participation/keys [participant absent?] :as participation} participations
              :when (not absent?)]
          ^{:key (str (:db/id participation))}
          [:div
           [participant-approval-status participant (some (fn [{:review/keys [reviewer] :as review}]
                                                            (when (= (:db/id reviewer) (:db/id participant))
                                                              review))
                                                          meeting-reviews)]]))]]))

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

(defn- meeting-details-info
  "Display meeting info: location, date/time and download link."
  [{:meeting/keys [start end location] :as meeting}]
  [common/basic-information-row
   {:right-align-last? true}
   [[(tr [:fields :meeting/date-and-time])
     (str (format/date start)
          " "
          (format/time* start)
          " - "
          (format/time* end))]
    [(tr [:fields :meeting/location])
     location]
    [(tr "PDF")
     [:a {:href (common-controller/query-url :meeting/download-pdf
                                             {:db/id (:db/id meeting)
                                              :language @localization/selected-language})
          :target "_blank" } "Download"]]]])

(defn meeting-details-add-agenda [e! user meeting pfrom]
  [form/form-container-button
   {:form-component [agenda-form e! meeting]
    :container pfrom
    :id "add-agenda"
    :form-value {:meeting.agenda/responsible
                 (select-keys user [:db/id
                                    :user/id
                                    :user/given-name
                                    :user/family-name
                                    :user/email
                                    :user/person-id])}
    :modal-title (tr [:meeting :new-agenda-modal-title])
    :button-component [buttons/button-primary {:start-icon (r/as-element
                                                             [icons/content-add])}
                       (tr [:meeting :add-agenda-button])]}])

(defn- meeting-agenda-heading [seen-at {:meeting.agenda/keys [topic responsible] :as agenda-topic}]
  [:div.agenda-heading
   [:div {:class (<class common-styles/flex-align-center)}
    [modified-since-last-seen seen-at agenda-topic]
    (let
        [agenda-files (:file/_attached-to agenda-topic)
         agenda-links (:link/_from agenda-topic)]
      (when (or (seq agenda-files) (seq agenda-links))
        [:div {:class (<class file-style/file-icon-container-style)
               :title (tr [:meeting :agenda-has-attachments])}
         [icons/content-link
          {:style {:color theme-colors/primary}}]]))
    [typography/Heading3 {:class (<class common-styles/margin-bottom "0.5")} topic]]
   [:span (user-model/user-name responsible)]])

(defn- meeting-agenda-decisions [e! edit? seen-at {:meeting.agenda/keys [topic] :as agenda-topic} d]
  (r/with-let [[pfrom pto] (common/portal)
               edit-open-atom (r/atom false)]
    [meeting-view-container
     {:heading [:div {:class (<class common-styles/flex-align-center)}
                [modified-since-last-seen seen-at d]
                (let [decision-files (:file/_attached-to d)
                      decision-links (:link/_from d)]
                  (when (or (seq decision-files) (seq decision-links))
                    [:div {:class (<class file-style/file-icon-container-style)
                           :title (tr [:meeting :decision-has-attachments])}
                     [icons/content-link
                      {:style {:color theme-colors/primary}}]]))
                [typography/Heading3
                 (tr [:meeting :decision-topic]
                     {:topic topic
                      :num (:meeting.decision/number d)})]]
      :on-toggle-open #(e! (meeting-controller/->MarkMeetingAsSeen))
      :open? true
      :heading-button (when edit?
                        [form/form-container-button
                         {:form-component [decision-form e! (:db/id agenda-topic)]
                          :container pfrom
                          :form-value d
                          :open-atom edit-open-atom
                          :modal-title (tr [:meeting :edit-decision-modal-title])
                          :button-component [buttons/button-secondary {:size :small}
                                             (tr [:buttons :edit])]}])
      :content
      [:<>
       [pto]
       [meeting-decision-content e! d edit? @edit-open-atom]]}]))


(defn meeting-agenda-topic
  [e! {:keys [edit-rights?]}
   {id :db/id
    :meeting.agenda/keys [decisions]
    :as agenda-topic}
   {:entity-seen/keys [seen-at]
    :as meeting}]
  (r/with-let [[pfrom pto] (common/portal)
               edit-open-atom (r/atom false)]
    [meeting-view-container
     {:heading [meeting-agenda-heading seen-at agenda-topic]
      :on-toggle-open #(e! (meeting-controller/->MarkMeetingAsSeen))
      :open? true
      :heading-button (when edit-rights?
                        [form/form-container-button
                         {:form-component [agenda-form e! meeting]
                          :container pfrom
                          :open-atom edit-open-atom
                          :id (str "edit-agenda-" id)
                          :form-value (select-keys agenda-topic [:meeting.agenda/body
                                                                 :meeting.agenda/topic :db/id
                                                                 :meeting.agenda/responsible])
                          :button-component [buttons/button-secondary {:size :small}
                                             (tr [:buttons :edit])]}])
      :content
      [:<>
       [pto]
       [meeting-agenda-content e! agenda-topic edit-rights? @edit-open-atom]]
      :children (for [d decisions]
                  ^{:key (str (:db/id d))}
                  [meeting-agenda-decisions e! edit-rights? seen-at agenda-topic d])
      :after-children-component (when edit-rights?
                                  [add-decision-component e! agenda-topic])}]))

(defn meeting-details*
  [e! user {:meeting/keys [agenda] :as meeting} rights]
  (r/with-let [[pfrom pto] (common/portal)]
    (let [edit-rights? (get rights :edit-meeting)
          review-rights? (get rights :review-meeting)]
      [context/provide :meeting meeting
       [:div {:data-cy "meeting-details"}
        [meeting-details-info meeting]
        [:div {:class (<class common-styles/heading-and-action-style)
               :style {:margin-bottom "1rem" :padding "1rem 0 1rem 0"
                       :border-bottom (str "1px solid " theme-colors/gray-lighter)}}
         [typography/Heading2 (tr [:fields :meeting/agenda])]
         (when edit-rights?
           [meeting-details-add-agenda e! user meeting pfrom])]
        [pto]
        [:div {:style {:margin-bottom "1rem"}}
         (doall
          (for [{id :db/id :as agenda-topic} agenda]
            ^{:key id}
            [meeting-agenda-topic
             e! {:edit-rights? edit-rights?
                 :review-rights? review-rights?}
             agenda-topic meeting]))]

        [reviews meeting]
        (when review-rights?
          [review-actions e! meeting])]])))

(defn meeting-details [e! user meeting]
  [authorization-context/consume
   [meeting-details* e! user meeting]])

(defn meeting-duplicate [e! activity-id meeting close! duplicate-open?]
  (r/with-let [form (r/atom meeting)
               close-event (form/callback-event close!)]
              [panels/modal {:title (tr [:meeting :duplicate-meeting-modal-title])
                             :max-width "md"
                             :open-atom duplicate-open?}
               [meeting-form {:e! e!
                              :activity-id activity-id
                              :duplicate? true}
                close-event
                form]]))

(def ^:private comment-count-path
  [:route :meeting :meeting :comment/counts :comment/old-comments])

(defn meeting-main-content
  [e! {:keys [params user] :as app} meeting]
  (r/with-let [duplicate-open? (r/atom false)
               open-duplicate! #(reset! duplicate-open? true)
               close-duplicate! #(reset! duplicate-open? false)]
              (let [{:meeting/keys [title number locked?]} meeting]
                [:div
                 [:div {:class (<class common-styles/heading-and-action-style)}
                  [typography/Heading2 {:class (<class common-styles/flex-align-center)}
                   title (when number (str " #" number)) (when locked? [icons/action-lock])]
                  [:div {:class (<class common-styles/flex-align-end)}
                   [authorization-context/when-authorized :edit-meeting
                    [authorization-check/when-authorized :meeting/update meeting
                     [form/form-modal-button {:form-component [meeting-form {:e! e! :activity-id (:activity params)}]
                                              :form-value meeting
                                              :modal-title (tr [:meeting :edit-meeting-modal-title])
                                              :id "edit-meeting"
                                              :button-component
                                              [buttons/button-secondary {:on-click meeting} (tr [:buttons :edit])]}]]]
                   [buttons/button-secondary {:style    {:margin-left "0.25rem"}
                                              :on-click open-duplicate!
                                              :title (tr [:buttons :duplicate])
                                              :size :large} (r/as-element [icons/content-content-copy])]]]

       (when @duplicate-open?
         [meeting-duplicate e! (:activity params) meeting close-duplicate! duplicate-open?])
       [tabs/details-and-comments-tabs
           {:e! e!
            :app app
            :type :meeting-comment
            :entity-type :meeting
            :entity-id (:db/id meeting)
            :comment-counts (:comment/counts meeting)
            :after-comment-list-rendered-event common-controller/->Refresh
            :after-comment-added-event
            #(comments-controller/->IncrementCommentCount comment-count-path)
            :after-comment-deleted-event
            #(comments-controller/->DecrementCommentCount comment-count-path)}
        [meeting-details e! user meeting]]])))

(defn- maybe-mark-meeting-as-seen!
  "Mark meeting as seen if there are no new indicators.
  If there are indicators, user must open the items to mark as seen."
  [e! {seen-at :entity-seen/seen-at :as m}]
  (when m
    (let [seen (some-> seen-at .getTime)
          new-indicators? (and seen
                               (some #(when-let [time (or (:meta/modified-at %)
                                                          (:meta/created-at %))]
                                        (> (.getTime time) seen))
                                     (concat [m]
                                             (for [a (:meeting/agenda m)] a)
                                             (for [a (:meeting/agenda m)
                                                   d (:meeting.agenda/decisions a)]
                                               d))))]
      (when-not new-indicators?
        (e! (meeting-controller/->MarkMeetingAsSeen))))))

(defn meeting-page [e! _ {m :meeting}]
  (let [allowed-atom (r/atom false)]
    (r/create-class
      {:component-did-mount
       (fn [_]
         (maybe-mark-meeting-as-seen! e! m)
         (e! (common-controller/map->QueryUserAccess {:action :meeting/update
                                                      :entity m
                                                      :result-event (form/update-atom-event allowed-atom)})))
       :component-did-update
       (fn [this [_ _ _ {prev-meeting :meeting}]]
         (let [[_ _ _ {new-meet :meeting}] (r/argv this)]
           (when-not (= (:db/id new-meet) (:db/id prev-meeting))
             (maybe-mark-meeting-as-seen! e! (:db/id new-meet))
             (e! (common-controller/map->QueryUserAccess {:action :meeting/update
                                                          :entity new-meet
                                                          :result-event (form/update-atom-event allowed-atom)})))))
       :reagent-render
       (fn [e! {:keys [user] :as app} {:keys [project meeting]}]
         (let [edit-rights? (and (or (authorization-check/authorized?
                                       user :meeting/edit-meeting
                                       {:entity meeting
                                        :link :meeting/organizer-or-reviewer
                                        :project-id (:db/id project)})
                                     @allowed-atom)
                                 (not (:meeting/locked? meeting)))
               review-rights? (and (meeting-model/user-can-review? user meeting)
                                   (not (:meeting/locked? meeting)))]
           [authorization-context/with
            (set/union
              (when edit-rights?
                #{:edit-meeting})
              (when review-rights?
                #{:review-meeting}))
            [context/provide :reviews? (seq (:review/_of meeting))
             [meeting-page-structure e! app project
              [meeting-main-content e! app meeting]
              [meeting-participants e! meeting edit-rights?]]]]))})))
