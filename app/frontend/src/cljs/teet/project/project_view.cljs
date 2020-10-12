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
            [teet.ui.buttons :as buttons]
            [teet.ui.common :as common]
            [teet.ui.form :as form]
            [teet.ui.drawing-indicator :as drawing-indicator]
            [teet.project.search-area-view :as search-area-view]
            [teet.ui.format :as format]
            [teet.ui.icons :as icons]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.material-ui :refer [Paper Link Badge Grid ButtonBase
                                         Popper MenuList MenuItem ClickAwayListener
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
            [teet.project.land-controller :as land-controller]
            [teet.project.project-menu :as project-menu]))

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
  {:padding "0 1.875rem 1.5rem 1.875rem"})

(defn- project-header [project]
  (let [thk-url (project-info/thk-url project)]
    [:div {:class (<class project-header-style)}
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
        (str "THK" (:thk.project/id project))]]]]))

(defn heading-state
  [title select]
  [:div {:class (<class project-style/heading-state-style)}
   [Heading3 title]
   select])

(defn project-page-structure
  [e!
   app
   project
   {:keys [key header body footer map-settings]}]
  (let [related-entity-type (or
                              (project-controller/project-setup-step app)
                              (get-in app [:query :configure]))]
    [:div {:class (<class project-style/project-page-structure)}
     [project-header project]
     [:div {:class (<class project-style/project-map-container)}
                                        ;[project-map-view/project-map e! app project]
      (project-map-view/create-project-map e! app project)
      [Paper {:class (<class project-style/project-content-overlay)}
       header
       [:div {:class (<class project-style/content-overlay-inner)}
        (with-meta
          body
          {:key key})]
       (when footer
         footer)]
      (when (get-in app [:map :search-area :drawing?])
        [drawing-indicator/drawing-indicator
         {:save-disabled? (not (boolean (get-in app [:map :search-area :unsaved-drawing])))
          :cancel-action #(e! (search-area-controller/->StopCustomAreaDraw))
          :save-action #(e! (search-area-controller/->SaveDrawnArea (get-in app [:map :search-area :unsaved-drawing])))}])
      (when (:geometry-range? map-settings)
        [search-area-view/feature-search-area e! app project related-entity-type])]]))

(defmethod project-menu/project-tab-content :activities [_ e! app project]
  [:div.project-activities-tab
   [project-navigator-view/project-task-navigator e! project app false]])

(defn add-user-form
  [e! user project-id reset-form-value-event]
  (let [roles (into []
                    (filter ac/role-can-be-granted?)
                    @ac/all-roles)]
    [:div
     [form/form2 {:e! e!
                  :value @user
                  :on-change-event (form/update-atom-event user merge)
                  :save-event #(e! (project-controller/->SaveProjectPermission project-id @user reset-form-value-event))
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

       (when-not (some? (:project/participant @user))
         [Grid {:item true :xs 6}
          [buttons/button-primary {:on-click #(swap! user merge {:project/participant :new})}
           (tr [:people-tab :invite-user])]])]

      (when (= (:project/participant @user) :new)
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

(defn- unregistered-user-description [user-id]
  (str (tr [:user :unregistered])
       ": "
       user-id))

(defn- user-description [user]
  (or (user-model/user-name user)
      (some-> user :user/person-id unregistered-user-description)
      (tr [:common :unknown])))

(defn people-panel-user-list
  [permissions selected-person-id]
  [:div
   (when permissions
     (let [current-selected-person-id @selected-person-id
           permission-links (map (fn [{:keys [user] :as _}]
                                   (let [user-id (:db/id user)]
                                     {:key user-id
                                      :on-click #(reset! selected-person-id user-id)
                                      :title (user-description user)
                                      :selected? (= user-id current-selected-person-id)}))
                                 permissions)]
       [itemlist/white-link-list permission-links]))
   [buttons/rect-white {:on-click #(reset! selected-person-id nil)}
    [icons/content-add]
    (tr [:project :add-users])]])

(defn people-modal
  [e! {permitted-users :thk.project/permitted-users :as project}]
  (r/with-let [open? (r/atom false)
               open-dialog! #(reset! open? true)
               close-dialog! #(reset! open? false)
               selected-person-id-atom (r/atom nil)
               form-value (r/atom nil)
               reset-form-value-event (form/reset-atom-event form-value nil)]
    [:<>
     (let [person @selected-person-id-atom
           selected-person (when person
                             (->> permitted-users
                                  (filter (fn [{user :user}]
                                            (= (:db/id user) person)))
                                  first))]
       [panels/modal+ {:open-atom open?
                       :title (if person
                                (-> selected-person :user user-description)
                                (tr [:project :add-users]))
                       :on-close close-dialog!
                       :left-panel [people-panel-user-list permitted-users
                                    selected-person-id-atom]
                       :right-panel (if selected-person
                                      [permission-information e! project selected-person]
                                      [add-user-form e! form-value (:db/id project) reset-form-value-event])}])
     [when-authorized :thk.project/update
      project
      [buttons/button-secondary {:on-click open-dialog!
                                 :size :small}
       (tr [:project :add-users])]]]))

(defn information-missing-icon
  []
  [icons/av-new-releases
   {:font-size :small
    :style {:color theme-colors/warning}}])

(defn- assignees-by-activity [assignees]
  [:div {:style {:margin-bottom "1rem"}}
   [:div {:class (<class common-styles/heading-and-action-style)}
    [typography/Heading2 (tr [:people-tab :consultants])]]
   #_(log/debug "assignees:" (pr-str assignees))
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

(defn activities-with-no-manager
  [project]
  (let [acts (mapcat :thk.lifecycle/activities
                     (:thk.project/lifecycles project))]
    (filterv
      (fn [activity]
        (nil? (:activity/manager activity)))
      acts)))

(defn- project-managers-info-missing [project]
  (let [acts (mapcat :thk.lifecycle/activities
                     (:thk.project/lifecycles project))
        activity-managers (mapv :activity/manager acts)
        owner (:thk.project/owner project)]
    #_(log/debug "people info-missing badge tests:" (nil? owner)
               (contains-nils? activity-managers)
               (empty? activity-managers) " - managers:" activity-managers)
    (or
     (nil? owner)
     (contains-nils? activity-managers)
     (empty? activity-managers))))


(defn active-manager [manager name]
  {:primary-text (user-model/user-name manager)
   :secondary-text (tr [:roles :manager])
   :tertiary-text [:span (tr-enum name) ;; activity name
                   [:div.activity-manager-active
                    {:class [(<class common-styles/green-text)
                             (<class common-styles/inline-block)
                             (<class common-styles/margin-left 1)]}
                    (tr [:people-tab :active])]]})

(defn manager-history-display [manager name manager-history]
  (for [{:keys [manager period]} manager-history
        :let [[start end] period
              now (js/Date.)]]
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
                             (and end (format/date end)))])]}))

(defn project-owner-and-managers* [owner lifecycles show-history?]
  (into [{:primary-text (or (user-model/user-name owner) (tr [:common :unassigned]))
          :secondary-text (tr [:roles :owner])}]
        ;; All activity owners
        (mapcat
         (fn [{activities :thk.lifecycle/activities}]
           (if show-history?
             (mapcat (fn [{:activity/keys [manager-history manager name]}]
                       (if (and (empty? manager-history) manager)
                         ;; No manager history, manager has been set
                         ;; when created and has never been changed
                         [(active-manager manager name)]
                         ;; Has histories containing previous and current
                         ;; managers
                         (manager-history-display manager name manager-history)))
                     activities)
             ;; else - show-history? = false
             (for [{:activity/keys [manager name]
                    id :db/id} activities
                   :when manager]
               (with-meta
                 (active-manager manager name)
                 {:key (str id)})))))
        lifecycles))

(defn project-owner-and-managers [owner lifecycles show-history? activities-missing-manager]
  [itemlist/gray-bg-list
   (concat
     (project-owner-and-managers* owner lifecycles show-history?)
     (mapv
       (fn [activity]
         {:id (:db/id activity)
          :primary-text [:span (tr [:common :unassigned])]
          :secondary-text [:span (tr [:roles :manager])]
          :tertiary-text [:span (tr-enum (:activity/name activity))]})
       activities-missing-manager))])

(defmethod project-menu/project-tab-content :people
  [_ e! {query :query :as _app}
   {:thk.project/keys [id owner permitted-users lifecycles] :as project}]
  (r/with-let [show-history? (r/atom false)
               has-history? (some #(> (count (:activity/manager-history %)) 1) ; more than 1 manager period => has history
                                  (mapcat :thk.lifecycle/activities lifecycles))]
    [:div.project-people-tab

     [:div.people-tab-managers
      [typography/Heading2 (tr [:project :management])]
      [project-owner-and-managers owner lifecycles @show-history? (activities-with-no-manager project)]
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
       [people-modal e! project]]

      (if (empty? permitted-users)
        [typography/GreyText (tr [:people-tab :no-other-users])]
        [itemlist/gray-bg-list (for [{:keys [user] :as permission} permitted-users]
                                 {:primary-text (user-description user)
                                  :secondary-text (tr [:roles (:permission/role permission)])
                                  :id (:db/id user)})])]]))

(defmethod project-menu/project-tab-content :details [_ e! _app project]
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

(defmethod project-menu/project-tab-content :restrictions
  [_ e! _ {:thk.project/keys [related-restrictions] :as _project}]
  (e! (project-controller/->FetchRelatedFeatures related-restrictions :restrictions))
  (fn [_ e! {{project-id :project} :params :as _app} {:keys [checked-restrictions] :as project}]
    (js/setTimeout ;; When the restrictions are updated this hack was needed because the component mounts before the refresh call finishes
     #(when (nil? checked-restrictions)
        (e! (project-controller/->FetchRelatedFeatures (:thk.project/related-restrictions project) :restrictions)))
     100)
    [restrictions-list e! checked-restrictions]))

(defmethod project-menu/project-tab-badge :people [_ project]
  (when (project-managers-info-missing project)
    [Badge {:badge-content (r/as-element [information-missing-icon])}]))

(defn edit-project-owner
  [e! on-close form-data]
  [form/form {:e! e!
              :value @form-data
              :on-change-event (form/update-atom-event form-data)
              :save-event #(project-controller/->SaveProjectOwner on-close (:thk.project/owner @form-data))
              :spec :project/edit-owner-form}

   ^{:attribute :thk.project/owner}
   [select/select-user {:e! e! :attribute :thk.project/owner}]])

(defmethod project-menu/project-tab-action :people [_ e! _app project]
  [when-authorized
   :thk.project/update
   project
   [form/form-modal-button {:modal-title (tr [:project :edit-project])
                            :form-component [edit-project-owner e!]
                            :form-value (select-keys project [:thk.project/owner])
                            :button-component [buttons/button-secondary {:size :small}
                                               (tr [:buttons :edit])]}]])

(defmethod project-menu/project-tab-action :activities [_ e! {page :page :as _app} project]

  [project-timeline-view/timeline (not= page :project) project])

(defn edit-project-details
  [e! project close!]
  (when-not (:basic-information-form project)
    (e! (project-controller/->InitializeBasicInformationForm
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
                :save-event (r/partial project-controller/->PostProjectEdit close!)
                :cancel-fn close!
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
       [num-range/num-range {:start-label (tr [:fields :thk.project/start-km])
                             :end-label (tr [:fields :thk.project/end-km])
                             :min-value min-km
                             :max-value max-km
                             :reset-start (partial project-setup-view/reset-range-value e! project :start)
                             :reset-end (partial project-setup-view/reset-range-value e! project :end)}])
     (when (project-setup-view/km-range-changed? project)
       ^{:xs 12 :attribute :thk.project/m-range-change-reason}
       [TextField {:multiline true
                   :rows 3}])]))

(defmethod project-menu/project-tab-action :details [_ e! _app project]
  (r/with-let [open? (r/atom false)
               open-dialog! #(reset! open? true)
               close-dialog! #(reset! open? false)]
    [:<>
     [panels/modal {:open-atom open?
                    :on-close #(reset! open? false)
                    :title (tr [:project :edit-project-details-modal-title])}
      [edit-project-details e! project close-dialog!]]

     [when-authorized
      :thk.project/update
      project
      [buttons/button-secondary {:size :small
                                 :on-click open-dialog!}
       (tr [:buttons :edit])]]]))

(defmethod project-menu/project-tab-action :restrictions
  [_ _e! _app {project-id :thk.project/id :as project}]
  [when-authorized :thk.project/update
   project
   [buttons/button-secondary {:component "a"
                              :href (str "/#/projects/" project-id "?tab=restrictions&configure=restrictions")
                              :size :small}
    (tr [:buttons :edit])]])

(defmethod project-menu/project-tab-content :land [_ e! app project]
  [land-tab/related-cadastral-units-info e! app project])

(defmethod project-menu/project-tab-action :land
  [_  _e! _app project]
  [when-authorized :thk.project/update
   project
   [buttons/button-secondary {:href (url/set-query-param :configure "cadastral-units")}
    (tr [:buttons :edit])]])

(defmethod project-menu/project-tab-content :road [_ e! app project]
  [road-view/road-objects-tab e! app project])

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
  [e! {{configure :configure} :query :as app} project]
  (cond
    (= configure "restrictions")
    ^{:key "Restrictions"}
    [project-page-structure e! app project
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
    [project-page-structure e! app project
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
    (let [{tab-name :name :as tab} (project-menu/active-tab app)]
      ^{:key "project-view"}
      [project-page-structure e! app project
       (merge {:key (name tab-name)
               :header [project-menu/project-menu e! app project false]
               :body [project-menu/project-tab-content tab-name e! app project]
               :map-settings {:layers (or (:layers tab)
                                          #{:thk-project :surveys})}
               :footer [project-menu/project-tab-footer tab-name e! app project]})])))




(defn project-page
  "Shows the normal project view for initialized projects, setup wizard otherwise."
  [e! app project]
  [project-context/provide
   {:db/id (:db/id project)
    :thk.project/id (:thk.project/id project)}
   [:<>
    [project-navigator-view/project-navigator-dialogs {:e! e! :app app :project project}]
    [project-view e! app project]]])
