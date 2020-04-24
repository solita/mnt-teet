(ns teet.project.project-view
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.authorization.authorization-check :as ac :refer [when-authorized]]
            [teet.common.common-styles :as common-styles]
            [teet.localization :refer [tr]]
            [teet.project.project-controller :as project-controller]
            [teet.project.project-model :as project-model]
            [teet.project.project-style :as project-style]
            [teet.project.land-view :as land-tab]
            [teet.project.project-setup-view :as project-setup-view]
            [teet.project.project-info :as project-info]
            [teet.project.project-navigator-view :as project-navigator-view]
            [teet.project.project-timeline-view :as project-timeline-view]
            teet.task.task-spec
            [teet.ui.breadcrumbs :as breadcrumbs]
            [teet.ui.buttons :as buttons]
            [teet.ui.common :as common]
            [teet.ui.form :as form]
            [teet.ui.drawing-indicator :as drawing-indicator]
            [teet.project.search-area-view :as search-area-view]
            [teet.ui.format :as format]
            [teet.ui.icons :as icons]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.material-ui :refer [Paper Link Badge]]
            [teet.ui.panels :as panels]
            [teet.ui.project-context :as project-context]
            [teet.ui.select :as select]
            [teet.ui.tabs :as tabs]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.typography :refer [Heading1 Heading3] :as typography]
            [teet.ui.url :as url]
            [teet.util.collection :as cu]
            [teet.theme.theme-colors :as theme-colors]
            [teet.project.search-area-controller :as search-area-controller]
            [teet.user.user-model :as user-model]
            [teet.ui.num-range :as num-range]
            [teet.project.project-map-view :as project-map-view]
            [teet.common.common-controller :as common-controller]
            [teet.road.road-model :as road-model]))



(defn project-details
  [e! {:thk.project/keys [estimated-start-date estimated-end-date road-nr
                          carriageway repair-method procurement-nr id] :as project}]
  (let [project-name (project-model/get-column project :thk.project/project-name)
        [start-km end-km] (project-model/get-column project :thk.project/effective-km-range)]
    [:div
     [:div {:class (<class common-styles/heading-and-button-style)}
      [typography/Heading2 project-name]
      [buttons/button-secondary {:size :small
                                 :on-click (e! project-controller/->OpenEditDetailsDialog)}
       (tr [:buttons :edit])]]
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
       [:div {:style {:display :flex
                      :align-items :center}}
        [Link {:target :_blank
               :style {:margin-right "1rem"
                       :display :flex
                       :align-items :center}
               :href (common-controller/query-url :thk.project/download-related-info
                                                  (select-keys project [:thk.project/id]))}
         [:span {:style {:margin-right "0.5rem"}}
          (tr [:project :download-related-info])]
         [icons/file-cloud-download]]
        [common/thk-link {:href thk-url
                          :target "_blank"}
         (str "THK" (:thk.project/id project))]]]]]))

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
      [project-map-view/project-map e! app project]
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
  [e! {:keys [stepper] :as app} project]
  [:<>
   [project-navigator-view/project-navigator-dialogs {:e! e! :app app :project project}]
   [project-navigator-view/project-navigator e! project stepper (:params app) false]])

(defn add-user-form
  [e! user project-id]
  (let [roles (into []
                    (filter ac/role-can-be-granted?)
                    @ac/all-roles)]
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
  [e! project permission]
  [:div
   [:div {:class (<class project-style/permission-container)}
    [:p (tr [:user :role]) ": " (tr [:roles (:permission/role permission)])]
    [:p (tr [:common :added-on]) ": " (format/date-time (:meta/created-at permission))]]
   [:div {:style {:display :flex
                  :justify-content :flex-end}}
    [when-authorized :thk.project/revoke-permission
     project
     [buttons/delete-button-with-confirm {:action #(e! (project-controller/->RevokeProjectPermission (:db/id permission)))}
      (tr [:project :remove-from-project])]]]])

(defn people-panel-user-list
  [permissions selected-person]
  [:div
   (when permissions
     (let [permission-links (map (fn [{:keys [user] :as _}]
                                   (let [user-id (:db/id user)]
                                     {:key user-id
                                      :href (url/set-query-param :person user-id)
                                      :title (or (user-model/user-name user)
                                                 (tr [:common :unknown]))
                                      :selected? (= (str user-id) selected-person)}))
                                 permissions)]
       [itemlist/white-link-list permission-links]))
   [buttons/rect-white {:href (url/remove-query-param :person)}
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
                                   [permission-information e! project selected-person]
                                   [add-user-form e! add-participant (:db/id project)])}]))

(defn information-missing-icon
  []
  [icons/av-new-releases
   {:font-size :small
    :style {:color theme-colors/warning}}])


(defn people-tab [e! {query :query :as _app} {:thk.project/keys [manager owner permitted-users] :as project}]
  [:div
   [people-modal e! project query]
   [:div
    [:div {:class (<class common-styles/heading-and-button-style)}
     [typography/Heading2 (tr [:people-tab :managers])]
     [when-authorized :thk.project/update
      project
      [buttons/button-secondary {:on-click (e! project-controller/->OpenEditProjectDialog)
                                 :size :small}
       (tr [:buttons :edit])]]]
    [itemlist/gray-bg-list [{:primary-text (if manager
                                             (str (:user/given-name manager) " " (:user/family-name manager))
                                             [information-missing-icon])
                             :secondary-text (tr [:roles :manager])}
                            {:primary-text (str (:user/given-name owner) " " (:user/family-name owner))
                             :secondary-text (tr [:roles :owner])}]]]
   [:div
    [:div {:class (<class common-styles/heading-and-button-style)}
     [typography/Heading2 (tr [:people-tab :other-users])]
     [when-authorized :thk.project/add-permission
      project
      [buttons/button-secondary {:on-click (e! project-controller/->OpenPeopleModal)
                                 :size :small}
       (tr [:buttons :edit])]]]
    (if (empty? permitted-users)
      [typography/GreyText (tr [:people-tab :no-other-users])]
      [itemlist/gray-bg-list (for [{:keys [user] :as permission} permitted-users]
                               {:primary-text (str (:user/given-name user) " " (:user/family-name user))
                                :secondary-text (tr [:roles (:permission/role permission)])
                                :id (:db/id user)})])]])

(defn details-tab [e! _app project]
  [:<>
   [project-details e! project]])

(defn restriction-tab
  [_e! {{project-id :project} :params :as _app} project]
  [:div
   [:div {:class (<class common-styles/heading-and-button-style)}
    [typography/Heading2 "Restrictions"]
    [buttons/button-secondary {:component "a"
                               :href (str "/#/projects/" project-id "?tab=restrictions&configure=restrictions")
                               :size :small}
     (tr [:buttons :edit])]]
   [itemlist/gray-bg-list [{:secondary-text (tr [:data-tab :restriction-count]
                                                {:count (count (:thk.project/related-restrictions project))})}]]])

(defn activities-tab-footer [_e! _app project]
  [:div {:class (<class project-style/activities-tab-footer)}
   [project-timeline-view/timeline project]])

(def project-tabs-layout
  ;; FIXME: Labels with TR paths instead of text
  [{:label [:project :tabs :activities]
    :value "activities"
    :component activities-tab
    :layers #{:thk-project :related-cadastral-units :related-restrictions}
    :footer activities-tab-footer}
   {:label [:project :tabs :people]
    :value "people"
    :component people-tab
    :badge (fn [project]
             (when-not (:thk.project/manager project)
               [Badge {:badge-content (r/as-element [information-missing-icon])}]))
    :layers #{:thk-project}}
   {:label [:project :tabs :details]
    :value "details"
    :component details-tab
    :layers #{:thk-project}}
   {:label [:project :tabs :restrictions]
    :value "restrictions"
    :component restriction-tab
    :layers #{:thk-project}}
   {:label [:project :tabs :land]
    :value "land"
    :component land-tab/related-cadastral-units-info}])

(defn selected-project-tab [{{:keys [tab]} :query :as _app}]
  (if tab
    (cu/find-first #(= tab (:value %)) project-tabs-layout)
    (first project-tabs-layout)))

(defn- project-tabs [e! app project]
  [tabs/tabs {:e! e!
              :selected-tab (:value (selected-project-tab app))}
   (mapv (fn [{badge :badge :as tab}]
           (if badge
             (assoc tab :badge (badge project))
             tab))
         project-tabs-layout)])

(defn- project-tab [e! app project]
  (let [selected-tab (selected-project-tab app)]
    ^{:key (:value selected-tab)}
    [(:component selected-tab) e! app project]))

(defn edit-project-management
  [e! project]
  (when-not (:basic-information-form project)
    (e! (project-controller/->UpdateBasicInformationForm
          (cu/without-nils {:thk.project/project-name (or (:thk.project/project-name project) (:thk.project/name project))
                            :thk.project/km-range (-> project
                                                      (project-model/get-column :thk.project/effective-km-range)
                                                      project-setup-view/format-range)
                            :thk.project/owner (:thk.project/owner project)
                            :thk.project/manager (:thk.project/manager project)}))))
  (fn [e! {form :basic-information-form :as project}]
    [form/form {:e! e!
                :value form
                :on-change-event project-controller/->UpdateBasicInformationForm
                :save-event project-controller/->PostProjectEdit
                :spec :project/edit-form}

     ^{:attribute :thk.project/owner}
     [select/select-user {:e! e! :attribute :thk.project/owner}]

     ^{:attribute :thk.project/manager}
     [select/select-user {:e! e! :attribute :thk.project/manager}]]))




(defn change-restrictions-view
  [e! app project]
  (let [buffer-m (get-in app [:map :road-buffer-meters])
        {:thk.project/keys [related-restrictions]} project]
    (e! (project-controller/->FetchRelatedCandidates buffer-m "restrictions"))
    (e! (project-controller/->FetchRelatedFeatures related-restrictions :restrictions)))
  (fn [e! app {:keys [open-types checked-restrictions feature-candidates draw-selection-features] :or {open-types #{}} :as _project}]
    (let [buffer-m (get-in app [:map :road-buffer-meters])
          search-type (get-in app [:map :search-area :tab])
          {:keys [loading? restriction-candidates]} feature-candidates]
      [project-setup-view/restrictions-listing e!
       open-types
       buffer-m
       {:restrictions restriction-candidates
        :draw-selection-features draw-selection-features
        :search-type search-type
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
  (fn [e! app {:keys [feature-candidates checked-cadastral-units draw-selection-features] :as _project}]
    (let [buffer-m (get-in app [:map :road-buffer-meters])
          search-type (get-in app [:map :search-area :tab])
          {:keys [loading? cadastral-candidates]} feature-candidates]
      [project-setup-view/cadastral-units-listing
       e!
       buffer-m
       {:cadastral-units cadastral-candidates
        :draw-selection-features draw-selection-features
        :loading? loading?
        :search-type search-type
        :checked-cadastral-units (or checked-cadastral-units #{})
        :toggle-cadastral-unit (e! project-controller/->ToggleCadastralUnit)
        :on-mouse-enter (e! project-controller/->FeatureMouseOvers "related-cadastral-unit-candidates" true)
        :on-mouse-leave (e! project-controller/->FeatureMouseOvers "related-cadastral-unit-candidates" false)}])))

(defn- project-view
  "The project view shown for initialized projects."
  [e! {{configure :configure} :query :as app} project breadcrumbs]
  (cond
    (= configure "restrictions")
    ^{:key "Restrictions"}
    [project-page-structure e! app project breadcrumbs
     {:header [:div {:class (<class project-style/project-view-header)}
               [typography/Heading1 {:style {:margin-bottom 0}} (tr [:search-area :select-relevant-restrictions])]]
      :body [change-restrictions-view e! app project]
      :map-settings {:geometry-range? true
                     :layers #{:thk-project :thk-project-buffer :related-restrictions}}
      :footer [:div {:class (<class project-style/wizard-footer)}
               [buttons/button-warning {:component "a"
                                        :href (url/remove-query-param :configure)}
                (tr [:buttons :cancel])]
               [buttons/button-primary
                {:on-click (e! project-controller/->UpdateProjectRestrictions
                               (:checked-restrictions project)
                               (:thk.project/id project))}
                (tr [:buttons :save])]]}]
    (= configure "cadastral-units")
    ^{:key "Cadastral units"}
    [project-page-structure e! app project breadcrumbs
     {:header [:div {:class (<class project-style/project-view-header)}
               [typography/Heading1 {:style {:margin-bottom 0}} (tr [:search-area :select-relevant-cadastral-units])]]
      :body [change-cadastral-units-view e! app project]
      :map-settings {:geometry-range? true
                     :layers #{:thk-project :thk-project-buffer :related-cadastral-units}}
      :footer [:div {:class (<class project-style/wizard-footer)}
               [buttons/button-warning {:component "a"
                                        :href (url/remove-query-param :configure)}
                (tr [:buttons :cancel])]
               [buttons/button-primary
                {:on-click (e! project-controller/->UpdateProjectCadastralUnits
                               (:checked-cadastral-units project)
                               (:thk.project/id project))}
                (tr [:buttons :save])]]}]
    :else
    ^{:key "Project view "}
    [project-page-structure e! app project breadcrumbs
     (merge {:header [project-tabs e! app project]
             :body [project-tab e! app project]
             :map-settings {:layers #{:thk-project :surveys}}}
            (when-let [tab-footer (:footer (selected-project-tab app))]
              {:footer [tab-footer e! app project]}))]))

(defn edit-project-details
  [e! project]
  (when-not (:basic-information-form project)
    (e! (project-controller/->InitializeBasicInformationForm
          (cu/without-nils {:thk.project/project-name (:thk.project/name project)
                            :thk.project/km-range (-> project
                                                      (project-model/get-column :thk.project/effective-km-range)
                                                      project-setup-view/format-range)
                            :thk.project/owner (:thk.project/owner project)
                            :thk.project/manager (:thk.project/manager project)}))))
  (fn [e! {form :basic-information-form :as project}]
    [form/form {:e! e!
                :value form
                :on-change-event project-controller/->UpdateBasicInformationForm
                :save-event project-controller/->PostProjectEdit
                :cancel-event project-controller/->CancelUpdateProjectInformation
                :spec :project/edit-details-form}

     ^{:attribute :thk.project/project-name
       :adornment [project-setup-view/original-name-adornment e! project]}
     [TextField {:full-width true :variant :outlined}]
     (let [min-km (some-> project :road-info :start-m road-model/m->km)
           max-km (some-> project :road-info :end-m road-model/m->km)]
       ^{:xs 12 :attribute :thk.project/km-range
         :validate (fn [[start end :as value]]
                     (when (or (num-range/num-range-error nil value start min-km max-km)
                               (num-range/num-range-error nil value end min-km max-km))
                       (str "Valid km range: " min-km "km - " max-km "km")))}
       [num-range/num-range {:start-label "Start km"
                             :end-label "End km"
                             :min-value min-km
                             :max-value max-km
                             :reset-start (partial project-setup-view/reset-range-value e! project :start)
                             :reset-end (partial project-setup-view/reset-range-value e! project :end)}])
     (when (project-setup-view/km-range-changed? project)
       ^{:xs 12 :attribute :thk.project/m-range-change-reason}
       [TextField {:multiline true
                   :rows 3}])]))


(defn project-page-modals
  [e! {{:keys [edit]} :query} project]
  (let [[modal modal-label]
        (cond
          edit
          (case edit
            "project"
            [[edit-project-management e! project]
             (tr [:project :edit-project])]
            "details"
            [[edit-project-details e! project]
             "Edit project details"])
          :else nil)]
    [panels/modal {:open-atom (r/wrap (boolean modal) :_)
                   :title (or modal-label "")
                   :on-close (e! project-controller/->CloseDialog)}

     modal]))

(defn project-page
  "Shows the normal project view for initialized projects, setup wizard otherwise."
  [e! app project breadcrumbs]
  [project-context/provide
   {:project-id (:db/id project)}
   [:<>
    [project-page-modals e! app project]
    [project-view e! app project breadcrumbs]]])
