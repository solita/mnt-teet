(ns teet.project.project-view
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.activity.activity-view :as activity-view]
            [teet.common.common-styles :as common-styles]
            [teet.localization :refer [tr tr-tree]]
            [teet.map.map-view :as map-view]
            [teet.project.project-controller :as project-controller]
            [teet.project.project-model :as project-model]
            [teet.project.project-style :as project-style]
            [teet.project.project-setup-view :as project-setup-view]
            [teet.project.project-layers :as project-layers]
            [teet.project.project-info :as project-info]
            [teet.task.task-controller :as task-controller]
            teet.task.task-spec
            [teet.ui.breadcrumbs :as breadcrumbs]
            [teet.ui.buttons :as buttons]
            [teet.ui.common :as common]
            [teet.ui.form :as form]
            [teet.ui.drawing-indicator :as drawing-indicator]
            [teet.project.search-area-view :as search-area-view]
            [teet.ui.format :as format]
            [teet.ui.icons :as icons]
            [teet.ui.stepper :as stepper]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.material-ui :refer [Paper Fab]]
            [teet.ui.panels :as panels]
            [teet.ui.select :as select]
            [teet.ui.tabs :as tabs]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.typography :refer [Heading1 Heading3] :as typography]
            [teet.ui.url :as url]
            [teet.ui.timeline :as timeline]
            [teet.util.collection :as cu]
            [teet.activity.activity-controller :as activity-controller]
            [teet.authorization.authorization-check :as authorization-check :refer [when-pm-or-owner]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.project.search-area-controller :as search-area-controller]
            [teet.user.user-model :as user-model]))

(defn task-form [_e! {:keys [initialization-fn]}]
  ;;Task definition (under project activity)
  ;; Task type (a predefined list of tasks: topogeodeesia, geoloogia, liiklusuuring, KMH eelhinnang, loomastikuuuring, arheoloogiline uuring, muu)
  ;; Description (short description of the task for clarification, 255char, in case more detailed description is needed, it will be uploaded as a file under the task)
  ;; Responsible person (email)
  (when initialization-fn
    (initialization-fn))
  (fn [e! {:keys [close task save on-change delete]}]
    [form/form {:e! e!
                :value task
                :on-change-event on-change
                :cancel-event close
                :save-event save
                :delete delete
                :spec :task/new-task-form}
     ^{:xs 12 :attribute :task/type}
     [select/select-enum {:e! e! :attribute :task/type}]

     ^{:attribute :task/description}
     [TextField {:full-width true :multiline true :rows 4 :maxrows 4}]

     ^{:attribute :task/assignee}
     [select/select-user {:e! e! :attribute :task/assignee}]]))


(defn project-details
  [_e! {:thk.project/keys [estimated-start-date estimated-end-date road-nr
                           carriageway repair-method procurement-nr id] :as project}]
  (let [project-name (project-model/get-column project :thk.project/project-name)
        [start-km end-km] (project-model/get-column project :thk.project/effective-km-range)]
    [:div
     [typography/Heading2 {:style {:margin-bottom "2rem"}} project-name]
     [:div [:span "THK id: " id]]
     [:div [:span (tr [:project :information :estimated-duration])
            ": "
            (format/date estimated-start-date)] " \u2013 " (format/date estimated-end-date)]
     [:div [:span (tr [:project :information :road-number]) ": " road-nr]]
     [:div [:span (tr [:project :information :km-range]) ": "
            (.toFixed start-km 3) " \u2013 "
            (.toFixed end-km 3)]]
     [:div [:span (tr [:project :information :procurement-number]) ": " procurement-nr]]
     [:div [:span (tr [:project :information :carriageway]) ": " carriageway]]
     (when repair-method
       [:div [:span (tr [:project :information :repair-method]) ": " repair-method]])]))


(defn project-header-style
  []
  {:padding "1.5rem 1.875rem"})

(defn- project-header [project breadcrumbs _activities]
  (let [thk-url (project-info/thk-url project)]
    [:div {:class (<class project-header-style)}
     [:div
      [breadcrumbs/breadcrumbs breadcrumbs]
      [:div {:style {:display :flex
                     :justify-content :space-between}}
       [Heading1 {:style {:margin-bottom 0}}
        (project-model/get-column project :thk.project/project-name)]
       [common/thk-link {:href thk-url
                         :target "_blank"}
        (str "THK" (:thk.project/id project))]]]]))

(defn heading-state
  [title select]
  [:div {:class (<class project-style/heading-state-style)}
   [Heading3 title]
   select])

(defn project-activity
  [e!
   {thk-id :thk.project/id}
   {:activity/keys [name tasks estimated-end-date estimated-start-date] :as _activity}]
  [:div {:class (<class project-style/project-activity-style)}
   [:div {:style {:display :flex
                  :justify-content :space-between
                  :align-items :center}}
    [:h2 (tr [:enum (:db/ident name)])]
    [buttons/button-secondary {:on-click (e! project-controller/->OpenEditActivityDialog)} (tr [:buttons :edit])]]
   [:span (format/date estimated-start-date) " – " (format/date estimated-end-date)]

   (if (seq tasks)
     (doall
       (for [{:task/keys [status type] :as t} tasks]
         ^{:key (:db/id t)}
         [common/list-button-link (merge {:link (str "#/projects/" thk-id "/" (:db/id t))
                                          :label (tr [:enum (:db/ident type)])
                                          :icon icons/file-folder-open}
                                         (when status
                                           {:end-text (tr [:enum (:db/ident status)])}))]))
     [:div {:class (<class project-style/top-margin)}
      [:em
       (tr [:project :activity :no-tasks])]])
   [buttons/button-primary
    {:on-click (e! project-controller/->OpenTaskDialog)}
    (tr [:project :add-task])]])


(defn map-style
  []
  {:flex 1})

(defn project-map [e! {:keys [map page] :as app} project]
  (r/with-let [overlays (r/atom [])
               fitted-atom (atom false)
               set-overlays! (fn [new-overlays]
                               (when (not= new-overlays @overlays)
                                 ;; Only set if actually changed (to avoid rerender loop)
                                 (reset! overlays new-overlays)))
               map-object-padding (if (= page :project)
                                    [25 25 25 (+ 100 (* 1.05 (project-style/project-panel-width)))]
                                    [25 25 25 25])]
    [:div {:style {:flex 1
                   :display :flex
                   :flex-direction :column}}
     [map-view/map-view e!
      {:class (<class map-style)
       :config (:config app)
       :layers (let [opts {:e! e!
                           :app app
                           :project project
                           :set-overlays! set-overlays!}]
                 (reduce (fn [layers layer-fn]
                           (merge layers (layer-fn opts)))
                         {}
                         [#_project-layers/surveys-layer
                          (partial project-layers/project-road-geometry-layer map-object-padding fitted-atom)
                          project-layers/setup-restriction-candidates
                          project-layers/project-drawn-area-layer
                          project-layers/setup-cadastral-unit-candidates
                          project-layers/ags-surveys
                          project-layers/related-restrictions
                          project-layers/related-cadastral-units
                          project-layers/selected-cadastral-units
                          project-layers/selected-restrictions]))
       :overlays (into []
                       (concat
                         (for [[_ {:keys [coordinate content-data]}] (:overlays project)]
                           {:coordinate coordinate
                            :content [map-view/overlay {:single-line? false
                                                        :width 200
                                                        :height nil
                                                        :arrow-direction :top}
                                      [itemlist/ItemList {}
                                       (for [[k v] content-data]
                                         [itemlist/Item {:label k} v])]]})
                         @overlays))}
      map]]))


(defn project-page-structure
  [e!
   app
   project
   breadcrumbs
   {:keys [header body footer map-settings]}]
  (let [related-entity-type (or
                              (project-controller/project-setup-step app)
                              (get-in app [:query :configure]))]
    [:div {:class (<class project-style/project-page-structure)}
     [project-header project breadcrumbs]
     [:div {:class (<class project-style/project-map-container)}
      [project-map e! app project]
      [Paper {:class (<class project-style/project-content-overlay)}
       header
       [:div {:class (<class project-style/content-overlay-inner)}
        body]
       (when footer
         footer)]
      (when (get-in app [:map :search-area :drawing?])
        [drawing-indicator/drawing-indicator
         {:save-disabled? (not (boolean (get-in app [:map :search-area :unsaved-drawing])))
          :cancel-action #(e! (search-area-controller/->StopCustomAreaDraw))
          :save-action #(e! (search-area-controller/->SaveDrawnArea (get-in app [:map :search-area :unsaved-drawing])))}])
      (when (:geometry-range? map-settings)
        [search-area-view/feature-search-area e! app project related-entity-type])]]))

(defn activities-tab
  [e! {:keys [stepper] :as _app} project]
  [stepper/vertical-stepper e! project stepper])

(defn add-user-form
  [e! user project-id]
  (let [roles (into []
                    (filter authorization-check/role-can-be-granted?)
                    @authorization-check/all-roles)]
    [:div
     [form/form {:e! e!
                 :value user
                 :on-change-event #(e! (project-controller/->UpdateProjectPermissionForm %))
                 :save-event #(e! (project-controller/->SaveProjectPermission project-id user))
                 :spec :project/add-permission-form}
      ^{:attribute :project/participant :xs 6}
      [select/select-user {:e! e! :attribute :project/participant
                           :extra-selection :new
                           :extra-selection-label (tr [:people-tab :invite-user])}]

      ^{:attribute :permission/role
        :xs 6}
      [select/form-select {:format-item #(tr [:roles %])
                           :show-empty-selection? true
                           :items roles}]

      (when (= (:project/participant user) :new)
        ^{:attribute :user/person-id
          :xs 6}
        [TextField {:start-icon
                    (fn [{c :class}]
                      [:div {:class (str c " "
                                         (<class common-styles/input-start-text-adornment))}
                       "EE"])}])]]))

(defn permission-information
  [e! permission]
  [:div
   [:div {:class (<class project-style/permission-container)}
    [:p (tr [:user :role]) ": " (tr [:roles (:permission/role permission)])]
    [:p (tr [:common :added-on]) ": " (format/date-time (:meta/created-at permission))]]
   [:div {:style {:display :flex
                  :justify-content :flex-end}}
    [buttons/delete-button-with-confirm {:action #(e! (project-controller/->RevokeProjectPermission (:db/id permission)))}
     (tr [:project :remove-from-project])]]])

(defn people-panel-user-list
  [permissions selected-person]
  [:div
   (when permissions
     (let [permission-links (map (fn [{:keys [user] :as _}]
                                   (let [user-id (:db/id user)]
                                     {:key user-id
                                      :href (url/set-params :person user-id)
                                      :title (or (user-model/user-name user)
                                                 (tr [:common :unknown]))
                                      :selected? (= (str user-id) selected-person)}))
                                 permissions)]
       [itemlist/white-link-list permission-links]))
   [buttons/rect-white {:href (url/remove-param :person)}
    [icons/content-add]
    (tr [:project :add-users])]])

(defn people-modal
  [e! {:keys [add-participant]
       permitted-users :thk.project/permitted-users :as project} {:keys [modal person] :as _query}]
  (let [open? (= modal "people")
        selected-person (when person
                          (->> permitted-users
                               (filter (fn [{user :user}]
                                         (= (str (:db/id user)) person)))
                               first))]
    [panels/modal+ {:open-atom (r/wrap open? :_)
                    :title (if person
                             (str (get-in selected-person [:user :user/given-name]) " " (get-in selected-person [:user :user/family-name]))
                             (tr [:project :add-users]))
                    :on-close (e! project-controller/->CloseDialog)
                    :left-panel [people-panel-user-list permitted-users person]
                    :right-panel (if selected-person
                                   [permission-information e! selected-person]
                                   [add-user-form e! add-participant (:db/id project)])}]))


(defn people-tab [e! {query :query :as _app} {:thk.project/keys [manager owner permitted-users] :as project}]
  [:div
   [people-modal e! project query]
   [:div
    [:div {:class (<class project-style/heading-and-button-style)}
     [typography/Heading2 (tr [:people-tab :managers])]
     (when-pm-or-owner
       project
       [buttons/button-secondary {:on-click (e! project-controller/->OpenEditProjectDialog)
                                  :size :small}
        (tr [:buttons :edit])])]
    [itemlist/gray-bg-list [{:primary-text (str (:user/given-name manager) " " (:user/family-name manager))
                             :secondary-text (tr [:roles :manager])}
                            {:primary-text (str (:user/given-name owner) " " (:user/family-name owner))
                             :secondary-text (tr [:roles :owner])}]]]
   [:div
    [:div {:class (<class project-style/heading-and-button-style)}
     [typography/Heading2 (tr [:people-tab :other-users])]
     (when-pm-or-owner
       project
       [buttons/button-secondary {:on-click (e! project-controller/->OpenPeopleModal)
                                  :size :small}
        (tr [:buttons :edit])])]
    (if (empty? permitted-users)
      [typography/GreyText (tr [:people-tab :no-other-users])]
      [itemlist/gray-bg-list (for [{:keys [user] :as permission} permitted-users]
                               {:primary-text (str (:user/given-name user) " " (:user/family-name user))
                                :secondary-text (tr [:roles (:permission/role permission)])
                                :id (:db/id user)})])]])

(defn- project-timeline [{:thk.project/keys [estimated-start-date estimated-end-date lifecycles]
                          :as project}]
  (r/with-let [show-in-modal? (r/atom false)]
    (when (and estimated-start-date estimated-end-date)
      (let [tr* (tr-tree [:enum])
            project-name (project-model/get-column project :thk.project/project-name)
            timeline-component [timeline/timeline {:start-date estimated-start-date
                             :end-date   estimated-end-date}
          (concat
           [{:label      project-name
             :start-date estimated-start-date
             :end-date   estimated-end-date
             :fill       "black"
             :hover      [:div project-name]}]
           (mapcat (fn [{:thk.lifecycle/keys [type estimated-start-date estimated-end-date
                                              activities]}]
                     (concat
                      [{:label      (-> type :db/ident tr*)
                        :start-date estimated-start-date
                        :end-date   estimated-end-date
                        :fill       "magenta"
                        :hover      [:div (tr* (:db/ident type))]}]
                      (for [{:activity/keys [name status estimated-start-date estimated-end-date]
                             :as activity} activities
                            :let [label (-> name :db/ident tr*)]]
                        {:label label
                         :start-date estimated-start-date
                         :end-date estimated-end-date
                         :fill "cyan"
                         :hover [:div
                                 [:div [:b (tr [:fields :activity/name]) ": "] label]
                                 [:div [:b (tr [:fields :activity/status]) ": "] (tr* (:db/ident status))]]})))
                   (sort-by :thk.lifecycle/estimated-start-date lifecycles)))]]

        [:<>
         [Fab {:on-click #(reset! show-in-modal? true)} [icons/action-zoom-in]]
         (if @show-in-modal?
           [panels/modal {:title "Timeline"
                          :max-width "lg"
                          :on-close #(reset! show-in-modal? false)}
            timeline-component]
           timeline-component)]))))

(defn details-tab [e! _app project]
  [:<>
   [project-details e! project]
   [project-timeline project]])

(defn edit-activity-form
  [_ _ _lifecycle-type initialization-fn]
  (initialization-fn)
  (fn [e! app lifecycle-type _]
    (when-let [activity-data (:edit-activity-data app)]     ;;Otherwise the form renderer can't format dates properly
      [activity-view/activity-form e! {:on-change activity-controller/->UpdateEditActivityForm
                                       :save activity-controller/->SaveEditActivityForm
                                       :close project-controller/->CloseDialog
                                       :activity (:edit-activity-data app)
                                       :lifecycle-type lifecycle-type
                                       :delete (project-controller/->DeleteActivity (str (:db/id activity-data)))}])))

(defn data-tab
  [_e! {{project-id :project} :params :as _app} project]
  [:div
   [:div {:class (<class project-style/heading-and-button-style)}
    [typography/Heading2 "Restrictions"]
    [buttons/button-secondary {:component "a"
                               :href (str "/#/projects/" project-id "?tab=data&configure=restrictions")
                               :size :small}
     (tr [:buttons :edit])]]
   [itemlist/gray-bg-list [{:secondary-text (tr [:data-tab :restriction-count]
                                                {:count (count (:thk.project/related-restrictions project))})}]]

   [:div {:class (<class project-style/heading-and-button-style)}
    [typography/Heading2 "Cadastral units"]
    [buttons/button-secondary {:component "a"
                               :href (str "/#/projects/" project-id "?tab=data&configure=cadastral-units")
                               :size :small}
     (tr [:buttons :edit])]]
   [itemlist/gray-bg-list [{:secondary-text (tr [:data-tab :cadastral-unit-count]
                                                {:count (count (:thk.project/related-cadastral-units project))})}]]])

(def project-tabs-layout
  ;; FIXME: Labels with TR paths instead of text
  [{:label "Activities"
    :value "activities"
    :component activities-tab
    :layers #{:thk-project :related-cadastral-units :related-restrictions}}
   {:label "People"                                         ;; HIDDEN UNTIL something is built for this tab
    :value "people"
    :component people-tab
    :layers #{:thk-project}}
   {:label "Details"
    :value "details"
    :component details-tab
    :layers #{:thk-project}}
   {:label "Data"
    :value "data"
    :component data-tab
    :layers #{:thk-project}}])

(defn selected-project-tab [{{:keys [tab]} :query :as _app}]
  (if tab
    (cu/find-first #(= tab (:value %)) project-tabs-layout)
    (first project-tabs-layout)))

(defn- project-tabs [e! app]
  [tabs/tabs {:e! e!
              :selected-tab (:value (selected-project-tab app))}
   project-tabs-layout])

(defn- project-tab [e! app project]
  [(:component (selected-project-tab app)) e! app project])

(defn edit-project-basic-information
  [e! project]
  (when-not (:basic-information-form project)
    (e! (project-controller/->UpdateBasicInformationForm
          (cu/without-nils {:thk.project/project-name (or (:thk.project/project-name project) (:thk.project/name project))
                            :thk.project/owner (:thk.project/owner project)
                            :thk.project/manager (:thk.project/manager project)}))))
  (fn [e! {form :basic-information-form :as project}]
    [form/form {:e! e!
                :value form
                :on-change-event project-controller/->UpdateBasicInformationForm
                :save-event project-controller/->PostProjectEdit
                :spec :project/edit-form}

     ^{:attribute :thk.project/project-name
       :adornment [project-setup-view/original-name-adornment e! project]}
     [TextField {:full-width true :variant :outlined}]

     ^{:attribute :thk.project/owner}
     [select/select-user {:e! e! :attribute :thk.project/owner}]

     ^{:attribute :thk.project/manager}
     [select/select-user {:e! e! :attribute :thk.project/manager}]]))

(defn project-page-modals
  [e! {{:keys [add edit lifecycle]} :query :as app} project]
  (let [lifecycle-type (some->> project
                                :thk.project/lifecycles
                                (filter #(= lifecycle (str (:db/id %))))
                                first
                                :thk.lifecycle/type
                                :db/ident)
        [modal modal-label]
        (cond
          add
          (case add
            "task"
            [[task-form e!
              {:close project-controller/->CloseDialog
               :task (:new-task project)
               :save task-controller/->CreateTask
               :on-change task-controller/->UpdateTaskForm}]
             (tr [:project :add-task])]
            "activity"
            [[activity-view/activity-form e! {:close project-controller/->CloseDialog
                                              :activity (get-in app [:project (:thk.project/id project) :new-activity])
                                              :on-change activity-controller/->UpdateActivityForm
                                              :save activity-controller/->CreateActivity
                                              :lifecycle-type lifecycle-type}]
             (tr [:project :add-activity lifecycle-type])])
          edit
          (case edit
            "activity"
            [[edit-activity-form e! app lifecycle-type (e! project-controller/->InitializeActivityEditForm)]
             (tr [:project :edit-activity])]
            "project"
            [[edit-project-basic-information e! project]
             (tr [:project :edit-project])])
          :else nil)]
    [panels/modal {:open-atom (r/wrap (boolean modal) :_)
                   :title (or modal-label "")
                   :on-close (e! project-controller/->CloseDialog)}

     modal]))

(defn- setup-incomplete-footer
  [e! project]
  [:div {:class (<class project-style/wizard-footer)}
   [:div
    [:p {:style {:color theme-colors/gray}}
     (tr [:project :wizard :project-setup])]
    [:span {:class (<class common-styles/warning-text)}
     (tr [:project :wizard :setup-incomplete])]]

   [buttons/button-primary {:type :submit
                            :on-click #(e! (project-controller/->ContinueProjectSetup (:thk.project/id project)))}
    (tr [:buttons :continue])]])

(defn change-restrictions-view
  [e! app project]
  (let [buffer-m (get-in app [:map :road-buffer-meters])
        {:thk.project/keys [related-restrictions]} project]
    (e! (project-controller/->FetchRelatedCandidates buffer-m "restrictions"))
    (e! (project-controller/->FetchRelatedFeatures related-restrictions :restrictions)))
  (fn [e! app {:keys [open-types checked-restrictions feature-candidates] :or {open-types #{}} :as _project}]
    (let [buffer-m (get-in app [:map :road-buffer-meters])
          {:keys [loading? restriction-candidates]} feature-candidates]
      [project-setup-view/restrictions-listing e!
       open-types
       buffer-m
       {:restrictions restriction-candidates
        :loading? loading?
        :checked-restrictions (or checked-restrictions #{})
        :toggle-restriction (e! project-controller/->ToggleRestriction)
        :on-mouse-enter (e! project-controller/->FeatureMouseOvers "related-restriction-candidates" true)
        :on-mouse-leave (e! project-controller/->FeatureMouseOvers "related-restriction-candidates" false)}])))

(defn change-cadastral-units-view
  [e! app project]
  (let [buffer-m (get-in app [:map :road-buffer-meters])
        {:thk.project/keys [related-cadastral-units]} project]
    (e! (project-controller/->FetchRelatedCandidates buffer-m "cadastral-units"))
    (e! (project-controller/->FetchRelatedFeatures related-cadastral-units :cadastral-units)))
  (fn [e! app {:keys [feature-candidates checked-cadastral-units] :as _project}]
    (let [buffer-m (get-in app [:map :road-buffer-meters])
          {:keys [loading? cadastral-candidates]} feature-candidates]
      [project-setup-view/cadastral-units-listing
       e!
       buffer-m
       {:cadastral-units cadastral-candidates
        :loading? loading?
        :checked-cadastral-units (or checked-cadastral-units #{})
        :toggle-cadastral-unit (e! project-controller/->ToggleCadastralUnit)
        :on-mouse-enter (e! project-controller/->FeatureMouseOvers "related-cadastral-unit-candidates" true)
        :on-mouse-leave (e! project-controller/->FeatureMouseOvers "related-cadastral-unit-candidates" false)}])))

(defn- initialized-project-view
  "The project view shown for initialized projects."
  [e! {{configure :configure} :query :as app} project breadcrumbs]
  (cond
    (= configure "restrictions")
    [project-page-structure e! app project breadcrumbs
     {:header [:div {:class (<class project-style/project-view-header)}
               [typography/Heading1 {:style {:margin-bottom 0}} "Select relevant restrictions"]]
      :body [change-restrictions-view e! app project]
      :map-settings {:geometry-range? true
                     :layers #{:thk-project :thk-project-buffer :related-restrictions}}
      :footer [:div {:class (<class project-style/wizard-footer)}
               [buttons/button-warning {:component "a"
                                        :href (url/remove-param :configure)}
                "cancel"]
               [buttons/button-primary
                {:on-click (e! project-controller/->UpdateProjectRestrictions
                               (:checked-restrictions project)
                               (:thk.project/id project))}
                "save"]]}]
    (= configure "cadastral-units")
    [project-page-structure e! app project breadcrumbs
     {:header [:div {:class (<class project-style/project-view-header)}
               [typography/Heading1 {:style {:margin-bottom 0}} "Select relevant Cadastral units"]]
      :body [change-cadastral-units-view e! app project]
      :map-settings {:geometry-range? true
                     :layers #{:thk-project :thk-project-buffer :related-cadastral-units}}
      :footer [:div {:class (<class project-style/wizard-footer)}
               [buttons/button-warning {:component "a"
                                        :href (url/remove-param :configure)}
                (tr [:buttons :cancel])]
               [buttons/button-primary
                {:on-click (e! project-controller/->UpdateProjectCadastralUnits
                               (:checked-cadastral-units project)
                               (:thk.project/id project))}
                (tr [:buttons :save])]]}]
    :else
    [project-page-structure e! app project breadcrumbs
     (merge {:header [project-tabs e! app]
             :body [project-tab e! app project]
             :map-settings {:layers #{:thk-project :surveys}}}
            (when (:thk.project/setup-skipped? project)
              {:footer [setup-incomplete-footer e! project]}))]))

(defn- project-setup-view
  "The project setup wizard that is shown for uninitialized projects."
  [e! app project breadcrumbs]
  [project-page-structure e! app project breadcrumbs
   (project-setup-view/view-settings e! app project)])

(defn project-page
  "Shows the normal project view for initialized projects, setup wizard otherwise."
  [e! app project breadcrumbs]
  [:<>
   [project-page-modals e! app project]
   (if (or (:thk.project/setup-skipped? project) (project-model/initialized? project))
     [initialized-project-view e! app project breadcrumbs]
     [project-setup-view e! app project breadcrumbs])])
