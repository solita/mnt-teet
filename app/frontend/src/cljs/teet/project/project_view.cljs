(ns teet.project.project-view
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.authorization.authorization-check :as ac :refer [when-authorized]]
            [teet.common.common-styles :as common-styles]
            [teet.localization :refer [tr tr-enum]]
            [teet.project.project-controller :as project-controller]
            [teet.project.project-model :as project-model]
            [teet.project.project-style :as project-style]
            [teet.project.land-view :as land-tab]
            [teet.project.project-setup-view :as project-setup-view]
            [teet.project.project-info :as project-info]
            [teet.project.project-navigator-view :as project-navigator-view]
            [teet.project.project-timeline-view :as project-timeline-view]
            [teet.project.road-view :as road-view]
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
            [teet.ui.material-ui :refer [Paper Link Badge Grid ButtonBase Menu MenuItem ListItemText
                                         IconButton]]
            [teet.ui.panels :as panels]
            [teet.ui.project-context :as project-context]
            [teet.ui.select :as select]
            [teet.ui.tabs :as tabs]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.typography :refer [Heading1 Heading3] :as typography]
            [teet.ui.url :as url]
            [teet.ui.util :refer [mapc] :as util]
            [teet.util.collection :as cu]
            [teet.theme.theme-colors :as theme-colors]
            [teet.project.search-area-controller :as search-area-controller]
            [teet.user.user-model :as user-model]
            [teet.ui.num-range :as num-range]
            [teet.project.project-map-view :as project-map-view]
            [teet.common.common-controller :as common-controller]
            [teet.road.road-model :as road-model]
            [teet.ui.query :as query]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [alandipert.storage-atom :refer [local-storage]]
            [teet.map.map-controller :as map-controller]
            [teet.ui.container :as container]
            [teet.ui.hotkeys :as hotkeys]
            [teet.project.land-controller :as land-controller]))

(defn project-details
  [e! {:thk.project/keys [estimated-start-date estimated-end-date road-nr
                          carriageway repair-method procurement-nr id] :as project}]
  (let [project-name (project-model/get-column project :thk.project/project-name)
        [start-km end-km] (project-model/get-column project :thk.project/effective-km-range)]
    [:div.project-details-tab
     [:div {:class (<class common-styles/heading-and-action-style)}
      [typography/Heading2 project-name]]
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
        (when (project-model/has-related-info? project)
          [Link {:target :_blank
                 :style {:margin-right "1rem"
                         :display :flex
                         :align-items :center}
                 :href (common-controller/query-url :thk.project/download-related-info
                                                    (select-keys project [:thk.project/id]))}
           [:span {:style {:margin-right "0.5rem"}}
            (tr [:project :download-related-info])]
           [icons/file-cloud-download]])
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
                                        ;[project-map-view/project-map e! app project]
      (project-map-view/create-project-map e! app project)
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
  [:div.project-activities-tab
   [project-navigator-view/project-navigator e! project stepper (:params app) false]])

(defn add-user-form
  [e! user project-id]
  (let [roles (into []
                    (filter ac/role-can-be-granted?)
                    @ac/all-roles)]
    [:div
     [form/form2 {:e! e!
                  :value user
                  :on-change-event #(e! (project-controller/->UpdateProjectPermissionForm %))
                  :save-event #(e! (project-controller/->SaveProjectPermission project-id user))
                  :spec :project/add-permission-form}
      [Grid {:container true :spacing 3
             :style {:width "100%"}}                        ;; Because material ui grid spacing causes sidescroll
       [Grid {:item true :xs 6}
        [form/field :project/participant
         [select/select-user {:e! e!}]]]

       [Grid {:item true :xs 6}
        [form/field :permission/role
         [select/form-select {:format-item #(tr [:roles %])
                              :show-empty-selection? true
                              :items roles}]]]

       (when-not (some? (:project/participant user))
         [Grid {:item true :xs 6}
          [buttons/button-primary {:on-click #(e! (project-controller/->UpdateProjectPermissionForm
                                                   {:project/participant :new}))}
           (tr [:people-tab :invite-user])]])]

      (when (= (:project/participant user) :new)
        [form/field :user/person-id
         [TextField {:start-icon
                     (fn [{c :class}]
                       [:div {:class (str c " "
                                          (<class common-styles/input-start-text-adornment))}
                        "EE"])}]])

      [:div {:style {:margin "0 12px"}}                     ;; To align the footer with the form after above fix
       [form/footer2]]]]))

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

(defn- assignees-by-activity [assignees]
  [:div {:style {:margin-bottom "1rem"}}
   [:div {:class (<class common-styles/heading-and-action-style)}
    [typography/Heading2 (tr [:people-tab :consultants])]]
   (mapc (fn [[activity assignees]]
           [common/hierarchical-container
            {:heading-color theme-colors/gray
             :heading-content [:div {:style {:padding "0.75rem 1rem"}}
                               [typography/Heading3 {:style {:color theme-colors/white}}
                                (tr-enum activity)]]
             :children [^{:key "assignees"}
                        [:div.people-tab-assignees-for-activity
                         [itemlist/gray-bg-list
                          {:class (<class common-styles/no-margin)}
                          (mapv (fn [{id :db/id permissions :permissions :as user}]
                                  {:primary-text (user-model/user-name user)
                                   :secondary-text (str/join ", " (map #(tr [:roles %]) permissions))
                                   :id (str id)})
                                assignees)]]]}])
         assignees)])

(defn- contains-nils? [seq]
  ;; (contains? doesn't work for vecs)
  (not-every? some? seq))

(defn- project-managers-info-missing [project]
  (let [acts (mapcat :thk.lifecycle/activities
                     (:thk.project/lifecycles project))
        activity-managers (mapv :activity/manager acts)
        owner (:thk.project/owner project)]
    (log/debug "people info-missing badge tests:" (nil? owner)
               (contains-nils? activity-managers)
               (empty? activity-managers) " - managers:" activity-managers)
    (or
     (nil? owner)
     (contains-nils? activity-managers)
     (empty? activity-managers))))

(defn- project-owner-and-managers [owner lifecycles show-history?]
  (let [now (js/Date.)
        active-manager (fn [manager name]
                          {:primary-text (user-model/user-name manager)
                           :secondary-text (tr [:roles :manager])
                           :tertiary-text [:span (tr-enum name)
                                           [:div.activity-manager-active
                                            {:class [(<class common-styles/green-text)
                                                     (<class common-styles/inline-block)
                                                     (<class common-styles/margin-left 1)]}
                                            (tr [:people-tab :active])]]})]
    [itemlist/gray-bg-list
     (util/with-keys
       (into [{:primary-text (str (:user/given-name owner) " " (:user/family-name owner))
               :secondary-text (tr [:roles :owner])}]
             ;; All activity owners
             (mapcat (fn [{activities :thk.lifecycle/activities}]
                       (if show-history?
                         (mapcat (fn [{:activity/keys [manager-history manager name]}]
                                   (if (and (empty? manager-history) manager)
                                     ;; No manager history, manager has been set when created
                                     ;; and has never been changed
                                     [(active-manager manager name)]

                                     ;; Has histories containing previous and current managers
                                     (for [{:keys [manager period]} manager-history
                                           :let [[start end] period]]
                                       {:primary-text (user-model/user-name manager)
                                        :secondary-text (tr [:roles :manager])
                                        :tertiary-text [:span (tr-enum name)
                                                        (if (and (or (nil? start) (<= start now))
                                                                 (or (nil? end) (>= end now)))
                                                          [:div.activity-manager-active
                                                           {:class [(<class common-styles/green-text)
                                                                    (<class common-styles/inline-block)
                                                                    (<class common-styles/margin-left 1)]}
                                                           (tr [:people-tab :active])]
                                                          [:div.activity-manager-inactive
                                                           {:class [(<class common-styles/gray-text)
                                                                    (<class common-styles/inline-block)
                                                                    (<class common-styles/margin-left 1)]}
                                                           (str (and start (format/date start))
                                                                "\u2013"
                                                                (and end (format/date end)))])]})))
                                 activities)
                         ;; else - show-history? = false
                         (for [{:activity/keys [manager name]
                                id :db/id} activities
                               :when manager]
                           (with-meta
                             (active-manager manager name)
                             {:key (str id)})))))
             lifecycles))]))

(defn people-tab [e! {query :query :as _app}
                  {:thk.project/keys [id owner permitted-users lifecycles] :as project}]
  (r/with-let [show-history? (r/atom false)
               has-history? (some #(> (count (:activity/manager-history %)) 1) ; more than 1 manager period => has history
                                  (mapcat :thk.lifecycle/activities lifecycles))]
    [:div.project-people-tab
     [people-modal e! project query]
     [:div.people-tab-managers
      [project-owner-and-managers owner lifecycles @show-history?]
      (when has-history?
        [Link {:on-click #(swap! show-history? not)
               :style {:cursor :pointer}}
         (tr [:people-tab (if @show-history?
                            :hide-history
                            :show-history)])])]

     [:div.people-tab-assignees-by-activity
      [query/query {:e! e!
                    :query :thk.project/assignees-by-activity
                    :args {:thk.project/id id}
                    :simple-view [assignees-by-activity]}]]

     [:div
      [:div {:class (<class common-styles/heading-and-action-style)}
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
                                  :id (:db/id user)})])]]))

(defn details-tab [e! _app project]
  [:<>
   [project-details e! project]])

(defn restriction-listing-class
  [zoomed?]
  ^{:pseudo {:hover {:background-color (if zoomed?
                                         theme-colors/blue-lighter
                                         theme-colors/gray-lighter)}}}
  {:transition "background-color 0.2s ease-in-out"
   :padding "1rem"
   :margin-bottom "0.25rem"
   :background-color (if zoomed?
                       theme-colors/blue-lightest
                       theme-colors/gray-lightest)})

(defn restrictions-list
  [_e! _restrictions]
  (let [opened-groups (local-storage (r/atom #{}) "opened-restriction-groups")
        toggle-groups (fn [group]
                        (if (@opened-groups group)
                          (swap! opened-groups disj group)
                          (swap! opened-groups conj group)))
        zoomed-id (r/atom "")]
    (fn [e! restrictions]
      (let [grouped-restrictions (group-by :VOOND restrictions)
            toggle-zoom (fn [restriction]
                          (if (= @zoomed-id (:teet-id restriction))
                            (do
                              (reset! zoomed-id "")
                              (map-controller/zoom-on-layer "geojson_entities"))
                            (do
                              (reset! zoomed-id (:teet-id restriction))
                              (map-controller/zoom-on-feature "geojson_features_by_id" restriction))))]
        (if (not-empty restrictions)
          [:div
           (mapc
             (fn [[group restrictions]]
               ^{:key group}
               [container/collapsible-container {:on-toggle #(toggle-groups group)
                                                 :open? (boolean (@opened-groups group))}
                (str group " " (count restrictions))
                (when (not-empty restrictions)
                  [:div {:style {:display :flex
                                 :flex-direction :column}}
                   (mapc
                     (fn [restriction]
                       ^{:key (:teet-id restriction)}
                       [ButtonBase {:class (<class restriction-listing-class (= (:teet-id restriction) @zoomed-id))
                                    :on-mouse-enter (e! project-controller/->FeatureMouseOvers "geojson_features_by_id" true restriction)
                                    :on-mouse-leave (e! project-controller/->FeatureMouseOvers "geojson_features_by_id" false restriction)
                                    :on-click #(toggle-zoom restriction)}
                        [:span (:VOOND restriction)]])
                     restrictions)])])
             grouped-restrictions)]
          [typography/GreyText (tr [:project :no-selected-restrictions])])))))

(defn restriction-tab
  [e! _ {:thk.project/keys [related-restrictions] :as _project}]
  (e! (project-controller/->FetchRelatedFeatures related-restrictions :restrictions))
  (fn [_e! {{project-id :project} :params :as _app} {:keys [checked-restrictions] :as project}]
    (js/setTimeout                                          ;; WHen the restrictions are updated this hack was needed because the component mounts before the refresh call finishes
     #(when (nil? checked-restrictions)
        (e! (project-controller/->FetchRelatedFeatures (:thk.project/related-restrictions project) :restrictions)))
     100)
    [restrictions-list e! checked-restrictions]))

(defn activities-tab-footer [_e! _app project]
  [:div {:class (<class project-style/activities-tab-footer)}
   [project-timeline-view/timeline project]])

(def project-tabs-layout
  [{:label [:project :tabs :activities]
    :value "activities"
    :component activities-tab
    :layers #{:thk-project :related-cadastral-units :related-restrictions}
    :footer activities-tab-footer
    :hotkey "1"}
   {:label [:project :tabs :people]
    :value "people"
    :component people-tab
    :badge (fn [project]
             (when (project-managers-info-missing project)
               [Badge {:badge-content (r/as-element [information-missing-icon])}]))
    :action-component-fn (fn [e! _app project]
                           [when-authorized :thk.project/update
                            project
                            [buttons/button-secondary {:on-click (e! project-controller/->OpenEditProjectDialog)
                                                       :size :small}
                (tr [:buttons :edit])]])
    :layers #{:thk-project}
    :hotkey "2"}
   {:label [:project :tabs :details]
    :value "details"
    :component details-tab
    :layers #{:thk-project}
    :hotkey "3"
    :action-component-fn (fn [e! app project]
                           [when-authorized
                            :thk.project/update
                            project
                            [buttons/button-secondary {:size :small
                                                       :on-click (e! project-controller/->OpenEditDetailsDialog)}
                             (tr [:buttons :edit])]])}
   {:label [:project :tabs :restrictions]
    :value "restrictions"
    :component restriction-tab
    :layers #{:thk-project}
    :hotkey "4"
    :action-component-fn (fn [_e! _app {project-id :thk.project/id}]
                           [buttons/button-secondary {:component "a"
                                                      :href (str "/#/projects/" project-id "?tab=restrictions&configure=restrictions")
                                                      :size :small}
                            (tr [:buttons :edit])])}
   {:label [:project :tabs :land]
    :value "land"
    :component land-tab/related-cadastral-units-info
    :hotkey "5"
    :action-component-fn (fn [e! _app project]
                           [buttons/button-secondary {:href (url/set-query-param :configure "cadastral-units")}
                            (tr [:buttons :edit])])}
   {:label [:project :tabs :road-objects]
    :value "road"
    :component road-view/road-objects-tab
    :hotkey "6"}])

(defn selected-project-tab [{{:keys [tab]} :query :as _app}]
  (if tab
    (cu/find-first #(= tab (:value %)) project-tabs-layout)
    (first project-tabs-layout)))

(defn- project-tabs-item [e! close-menu! _selected-tab {:keys [value hotkey] :as _tab}]
  (let [activate! #(do
                     (e! (common-controller/->SetQueryParam :tab value))
                     (close-menu!))]
    (common/component
     (hotkeys/hotkey hotkey activate!)
     (fn [_ _ selected-tab {:keys [value label badge hotkey]}]
       [MenuItem {:on-click activate!
                  :selected (= value (:value selected-tab))
                  :classes {:root (<class project-style/project-view-selection-item)}}
        [:div {:class (<class project-style/project-view-selection-item-label)} (tr label)]
        [:div {:class (<class project-style/project-view-selection-item-hotkey)} (tr [:common :hotkey] {:key hotkey})]]))))

(defn- project-tabs [_ _ _]
  (let [open? (r/atom false)
        anchor-el (atom nil)
        toggle-open! #(do
                        (swap! open? not)
                        (.blur @anchor-el))
        set-anchor! #(reset! anchor-el %)]
    (common/component
     (hotkeys/hotkey "§" toggle-open!)
     (fn [e! app project]

       (let [{action :action-component-fn :as selected} (selected-project-tab app)]
         [:div {:class (<class project-style/project-tab-container)}
          [:div {:class (<class common-styles/space-between-center)}
           [:div {:class (<class common-styles/flex-align-center)}
            [IconButton {:on-click toggle-open!
                         :ref set-anchor!}
             [icons/navigation-apps]]
            [typography/Heading1 {:class [(<class common-styles/inline-block)
                                          (<class common-styles/no-margin)]} (tr (:label (selected-project-tab app)))]]
           (when action
             (action e! app project))]
          [Menu {:open @open?
                 :anchor-el @anchor-el
                 :classes {:paper (<class project-style/project-view-selection-menu)}}
           (doall
            (for [tab project-tabs-layout]
              ^{:key (str (:value tab))}
              [project-tabs-item e! toggle-open! selected tab]))]])))))

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
  (fn [e! {form :basic-information-form :as _project}]
    [form/form {:e! e!
                :value form
                :on-change-event project-controller/->UpdateBasicInformationForm
                :save-event project-controller/->PostProjectEdit
                :spec :project/edit-form}

     ^{:attribute :thk.project/owner}
     [select/select-user {:e! e! :attribute :thk.project/owner}]]))

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
    [project-navigator-view/project-navigator-dialogs {:e! e! :app app :project project}]
    [project-page-modals e! app project]
    [project-view e! app project breadcrumbs]]])
