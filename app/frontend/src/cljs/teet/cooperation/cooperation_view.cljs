(ns teet.cooperation.cooperation-view
  (:require [herb.core :as herb :refer [<class]]
            [taoensso.timbre :as log]
            [reagent.core :as r]
            [teet.common.common-controller :as common-controller]
            [teet.common.common-styles :as common-styles]
            [teet.cooperation.cooperation-controller :as cooperation-controller]
            [teet.cooperation.cooperation-model :as cooperation-model]
            [teet.cooperation.cooperation-style :as cooperation-style]
            [teet.localization :refer [tr tr-enum]]
            [teet.project.project-navigator-view :as project-navigator-view]
            [teet.project.project-view :as project-view]
            [teet.ui.buttons :as buttons]
            [teet.ui.common :as common]
            [teet.ui.date-picker :as date-picker]
            [teet.ui.form :as form]
            [teet.ui.format :as format]
            [teet.ui.icons :as icons]
            [teet.ui.material-ui :refer [Grid Divider]]
            [teet.ui.select :as select]
            [teet.ui.text-field :as text-field]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.typography :as typography]
            [teet.ui.url :as url]
            [teet.user.user-model :as user-model]
            [teet.ui.rich-text-editor :as rich-text-editor]
            [teet.util.collection :as cu]
            [clojure.string :as str]
            [teet.task.task-view :as task-view]
            [teet.link.link-view :as link-view]
            [teet.ui.authorization-context :as authorization-context]
            [teet.authorization.authorization-check :as authorization-check]))


;; Form structure in this view:
;; third-party-page
;;   cooperation-page-structure (usage #1)
;;     third-parties
;;       third-party-form (uses form/form)
;;     applications
;;   project-view/project-full-page-structure
;;     third-parties
;;   application-form
;;     
;; 
;; application-page
;;   cooperation-page-structure (usage #2)
;;     third-parties
;;       third-party-form (uses form/form)
;;     application-conclusion
;;       opinion-view
;;     application-response
;;       application-response-form (uses form/form2)
;;   application-people-panel (uses form/form)
;;

(defn- third-party-form [{:keys [e! project-id]} close-event form-atom]
  [form/form {:e! e!
              :value @form-atom
              :on-change-event (form/update-atom-event form-atom merge)
              :cancel-event close-event
              :save-event #(common-controller/->SaveForm
                            :cooperation/create-3rd-party
                            {:thk.project/id project-id
                             :third-party (common-controller/prepare-form-data @form-atom)}
                            (fn [response]
                              (fn [e!]
                                (e! (close-event))
                                (e! (cooperation-controller/->ThirdPartyCreated
                                     (:cooperation.3rd-party/name @form-atom)
                                     response)))))
              :spec ::cooperation-model/third-party-form}
   ^{:attribute :cooperation.3rd-party/name}
   [text-field/TextField {}]

   ^{:attribute :cooperation.3rd-party/id-code}
   [text-field/TextField {}]

   ^{:attribute :cooperation.3rd-party/email}
   [text-field/TextField {}]

   ^{:attribute :cooperation.3rd-party/phone}
   [text-field/TextField {}]])

(defn- third-parties-navigator []
  {:background-color theme-colors/gray
   :margin-left "2rem"
   :padding "1rem"})

(defn third-party-list []
  {:margin-left 0
   :margin-top 0
   :margin-bottom "1.5rem"
   :padding-left 0
   :list-style-type :none})

(defn- application-link [{teet-id :teet/id :cooperation.application/keys [type response-type]}]
  [url/Link {:page :cooperation-application
             :params {:application teet-id}}
   (str
     (tr-enum type) " / " (tr-enum response-type))])

(defn color-and-status
  [color text]
  [:div {:style {:display :flex
                 :align-items :center}}
   [:div {:class (<class common-styles/status-circle-style color)}]
   [:span text]])

(defn response-status
  [response]
  (let [color (case (or (get-in response [:cooperation.response/status :db/ident]) (:cooperation.response/status response))
                :cooperation.response.status/no-objection theme-colors/success
                :cooperation.response.status/no-objection-with-terms theme-colors/warning
                :cooperation.response.status/objection theme-colors/error
                :cooperation.response.status/no-response theme-colors/error
                theme-colors/black-coral-1)]
    [:div
     [color-and-status color (if response
                               (tr-enum (:cooperation.response/status response))
                               (tr [:cooperation :applied]))]]))

(defn- opinion-status [status]
  (let [status (or status :cooperation.opinion.status/unanswered)]
    [:div {:data-cy (name status)}
     [color-and-status
      (cooperation-style/opinion-status-color status)
      (tr-enum status)]]))


(defn- opinion-view
  ([opinion] (opinion-view {} opinion))
  ([{:keys [edit-button delete-button]}
    {:cooperation.application/keys [opinion]}]
   (let [{:meta/keys [creator modifier created-at modified-at]
          comment :cooperation.opinion/comment} opinion]
     [:<>
      [:div {:class (<class common-styles/flex-row)}
       [:div {:class (<class common-styles/flex-table-column-style 20)}
        [:span (tr [:cooperation :competent-authority-name])]]
       [:div {:class (<class common-styles/flex-table-column-style 30)}
        ;; Show last modifier or creator of the opinion if present
        ;; otherwise show creator of the application
        (user-model/user-name (or modifier creator))]
       [:div {:class (<class common-styles/flex-table-column-style 50)}
        [:div {:class (<class common-styles/flex-column-1)}
         [:div {:class (<class common-styles/flex-row)
                :style {:flex 1
                        :justify-content :space-between}}
          (opinion-status (:cooperation.opinion/status opinion))
          edit-button
          delete-button]
         (when-let [date (or modified-at created-at)]
           [:div
            [typography/BoldGreyText {:style {:display :inline-block}}
             (str (tr [:common :date]) ":")]
            (str " " (format/date-time date))])]]]
      (when comment
        [:div [rich-text-editor/display-markdown comment]])])))

(defn cooperation-opinion
  [{:cooperation.application/keys [opinion]}]
  [common/basic-information-row
   {:right-align-last? false
    :key (:db/id opinion)}
   [[(tr [:cooperation :conclusion])
     ;; colored circle based on status
     [opinion-status (:cooperation.opinion/status opinion)]]
    (when-let [date (:meta/created-at opinion)]
      [(tr [:common :date])
       date])]])

(defn- application-information [{:cooperation.application/keys [response] :as application}]
  [:div {:class (<class common-styles/margin-bottom 1)}
   (if response
     (let [{:cooperation.response/keys [date valid-until]} response
           expiration-warning? (cooperation-model/application-expiration-warning? response)]
       [common/basic-information-row
        {:right-align-last? false
         :key (:db/id application)}
        [[(tr [:cooperation :response-of-third-party])
          ;; colored circle based on status
          [response-status response]]
         [(tr [:fields :cooperation.response/date])
          (format/date date)]
         (when valid-until
           [(tr [:fields :cooperation.response/valid-until])
            [:div {:style {:display :flex}}
             (when expiration-warning?
               [common/popper-tooltip
                {:title (tr [:cooperation :application-terms-expire-warning])
                 :variant :warning}
                [icons/alert-warning-outlined {:style {:color theme-colors/dark-tangerine-11
                                                       :margin-right "0.25rem"}}]])
             [:span (format/date valid-until)]]
            {:data-cy "valid-until"}])]])
     (let [{:cooperation.application/keys [date response-deadline]} application]
       [common/basic-information-row
        {:right-align-last? false
         :key (:db/id application)}
        [[(tr [:fields :cooperation.response/status])
          ;; colored circle based on status
          [response-status response]]
         [(tr [:fields :cooperation.application/date])
          (format/date date)]
         [(tr [:fields :cooperation.application/response-deadline])
          (or (format/date response-deadline) (tr [:cooperation :no-deadline]))]]]))])

(defn application-list-opinion
  [{:cooperation.application/keys [activity opinion] :as _application}]
  [:div
   [typography/Text {:class (<class common-styles/margin-bottom 1)}
    (tr [:cooperation :responsible-person]
        {:user-name (user-model/user-name (:activity/manager activity))})]
   [common/basic-information-row
    {:right-align-last? false
     :key (:db/id activity)}
    [[(tr [:cooperation :opinion])
      ;; colored circle based on status
      [opinion-status (:cooperation.opinion/status opinion)]]
     [(tr [:common :date])
      (format/date (:meta/created-at opinion))]]]])

(defn application-list-item
  [{:keys [parent-link-comp]} {:cooperation.application/keys [activity] :as application}]
  [:div {:class (<class cooperation-style/application-list-item-style)}

   (when parent-link-comp
     parent-link-comp)
   (when application
     [Grid {:container true
            :spacing 3}
      [Grid {:item true
             :md 12
             :lg 8}
       [:div {:class [(<class common-styles/flex-row)
                      (<class common-styles/margin-bottom 1)]}
        [application-link application]
        [typography/GreyText {:style {:margin-left "0.5rem"}}
         (tr-enum (:activity/name activity))]]
       [application-information application]]
      [Grid {:item true
             :md 12
             :lg 4}
       [application-list-opinion application]]])])

(defn- third-parties [e! project third-parties selected-third-party-teet-id]
  [:div {:class (<class project-navigator-view/navigator-container-style true)}
   [:div.project-third-parties-list
    {:class (<class third-parties-navigator)}
    [:ul {:class (<class third-party-list)}
     (for [{id :db/id
            teet-id :teet/id
            :cooperation.3rd-party/keys [name]} third-parties]
       (let [currently-selected-third-party? (= teet-id selected-third-party-teet-id)]
         ^{:key (str id)}
         [:li.project-third-party
          [url/Link {:page :cooperation-third-party
                     :class (<class common-styles/white-link-style
                                    currently-selected-third-party?)
                     :params
                     {:third-party teet-id}}
           name]]))]
    [form/form-modal-button
     {:max-width "sm"
      :form-component [third-party-form {:e! e!
                                         :project-id (:thk.project/id project)}]
      :modal-title (tr [:cooperation :new-third-party-title])
      :button-component [buttons/rect-white {:size :small
                                             :start-icon (r/as-element
                                                          [icons/content-add])}
                         (tr [:cooperation :new-third-party])]}]]])

(defn- selected-third-party-teet-id [{:keys [params] :as _app}]
  (some-> params :third-party uuid))

(defn- cooperation-page-structure [e! app project third-parties-list main-content & [right-content]]
  [project-view/project-full-page-structure
   {:e! e!
    :app app
    :project project
    :left-panel [third-parties e! project third-parties-list (selected-third-party-teet-id app)]
    :right-panel right-content
    :main main-content}])

;; entrypoint from route /projects/:project/cooperation route
(defn overview-page [e! app {:keys [project overview]}]

  [:div.cooperation-overview-page {:class (<class common-styles/flex-column-1)}
   [cooperation-page-structure
    e! app project overview
    [:<>
     [:div {:class (<class common-styles/margin-bottom 1.5)}
      [typography/Heading2 (tr [:cooperation :page-title])]
      [typography/Heading3 {:class (<class common-styles/margin-bottom 1)}
       (tr [:cooperation :all-third-parties])]
      [form/form-modal-button
       {:max-width "sm"
        :form-component [third-party-form {:e! e!
                                           :project-id (:thk.project/id project)}]
        :modal-title (tr [:cooperation :new-third-party-title])
        :button-component [buttons/button-primary {:class "new-third-party"}
                           (tr [:cooperation :new-third-party])]}]]
     (for [{teet-id :teet/id :cooperation.3rd-party/keys [name applications]} overview]
       [url/with-navigation-params
        {:third-party (str teet-id)}
        (let [application (first applications)]
          [:div {:data-third-party name}
           [application-list-item
            {:parent-link-comp [url/Link {:style {:font-size "1.5rem"
                                                  :display :block}
                                          :class (<class common-styles/margin-bottom 1.5)
                                          :page :cooperation-third-party
                                          :params {:third-party teet-id}}
                                name]}
            application]])])]]])

(defn- applications [{:cooperation.3rd-party/keys [applications] :as _third-party}]
  [:<>
   (for [{id :db/id :as application} applications]
     ^{:key (str id)}
     [application-list-item {} application])])

(defn- application-form [{:keys [e! project-id third-party-teet-id]} close-event form-atom]
  [form/form {:e! e!
              :value @form-atom
              :on-change-event (form/update-atom-event form-atom merge)
              :cancel-event close-event
              :save-event #(common-controller/->SaveForm
                             :cooperation/create-application
                             {:thk.project/id project-id
                              :third-party-teet-id third-party-teet-id
                              :application (common-controller/prepare-form-data @form-atom)}
                             (fn [response]
                               (fn [e!]
                                 (e! (close-event))
                                 (e! (cooperation-controller/->ApplicationCreated response)))))
              :spec ::cooperation-model/application-form}
   ^{:attribute :cooperation.application/type}
   [select/select-enum {:e! e! :attribute :cooperation.application/type}]

   ^{:attribute :cooperation.application/response-type}
   [select/select-enum {:e! e! :attribute :cooperation.application/response-type}]

   ^{:attribute :cooperation.application/date
     :xs 8}
   [date-picker/date-input {}]

   ^{:attribute :cooperation.application/response-deadline
     :xs 8}
   [date-picker/date-input {}]

   ^{:attribute :cooperation.application/comment}
   [text-field/TextField {:multiline true
                          :rows 5}]])

;; entrypoint from route /projects/:project/cooperation/:third-party 
(defn third-party-page [e! {:keys [params] :as app} {:keys [project overview]}]
  (let [third-party-teet-id (uuid (:third-party params))
        third-party (some #(when (= third-party-teet-id
                                    (:teet/id %)) %)
                          overview)]
    [:div.cooperation-third-party-page {:class (<class common-styles/flex-column-1)}
     [cooperation-page-structure
      e! app project overview
      [:<>
       [:div {:class (<class common-styles/margin-bottom 1)}
        [common/header-with-actions
         (:cooperation.3rd-party/name third-party)
         [buttons/button-secondary

          (tr [:buttons :edit])]]
        [typography/Heading3
         {:class (<class common-styles/margin-bottom 2)}
         (tr [:cooperation :applications])]

        [form/form-modal-button
         {:max-width "sm"
          :form-component [application-form {:e! e!
                                             :project-id (:thk.project/id project)
                                             :third-party-teet-id third-party-teet-id}]
          :modal-title (tr [:cooperation :new-application-title])
          :button-component [buttons/button-primary {:class "new-application"}
                             (tr [:cooperation :new-application])]}]]
       [applications third-party]]]]))

(defn application-response-change-fn
  [val change]
  (-> (merge val change)
       (update :cooperation.response/valid-months #(and (or (number? %)
                                                            (not-empty %))
                                                        (js/parseInt %)))
       cu/without-empty-vals
       cooperation-model/with-valid-until))

(defn- application-response-form [{:keys [e! project-id application-id]} close-event form-atom]
  (let [max-months 1200
        min-months 0]
    [form/form2 {:e! e!
                 :value @form-atom
                 :on-change-event (form/update-atom-event form-atom application-response-change-fn)
                 :cancel-event close-event
                 :save-event #(common-controller/->SaveForm
                                :cooperation/save-application-response
                                {:thk.project/id project-id
                                 :application-id application-id
                                 :form-data (common-controller/prepare-form-data
                                              (rich-text-editor/form-data-with-rich-text
                                                :cooperation.response/content
                                                @form-atom))}
                                (fn [response]
                                  (fn [e!]
                                    (e! (close-event))
                                    (e! (cooperation-controller/->ResponseCreated response (boolean (:db/id @form-atom)))))))
                 :spec ::cooperation-model/response-form}
     [:div
      [:div {:class (<class common-styles/gray-container-style)}
       [Grid {:container true :spacing 3}
        [Grid {:item true :xs 12}
         [form/field :cooperation.response/status
          [select/select-enum {:e! e!
                               :attribute :cooperation.response/status}]]]

        [Grid {:item true :xs 12}
         [form/field :cooperation.response/date
          [date-picker/date-input {}]]]

        [Grid {:item true :xs 6
               :style {:display :flex
                       :align-items :flex-end}}
         [form/field {:attribute :cooperation.response/valid-months
                      :validate (fn [val]
                                  (when (and (some? val) (not (str/blank? val)))
                                    (let [val (js/parseInt val)]
                                      (when (or (js/isNaN val) (>= val max-months) (>= min-months val))
                                        true))))}
          [text-field/TextField {:type :number
                                 :max max-months
                                 :min min-months}]]]

        [Grid {:item true :xs 6
               :style {:display :flex
                       :align-items :flex-end}}
         (when (:cooperation.response/valid-until @form-atom)
           [form/field :cooperation.response/valid-until
            [date-picker/date-input {:read-only? true}]])]
        [Grid {:item true :xs 12}
         [form/field :cooperation.response/content
          [rich-text-editor/rich-text-field {}]]]]]
      [form/footer2]]]))

(defn application-response
  [e! new-document files-form application project related-task]
  (r/with-let [{:keys [upload!] :as controls} (task-view/file-upload-controls e!)]
    (let [response (:cooperation.application/response application)
          linked-files (:link/_from response)]
      [:div {:class (<class common-styles/margin-bottom 3)}
       [Divider {:style {:margin "1.5rem 0"}}]
       [:div {:class [(<class common-styles/flex-row-space-between)
                      (<class common-styles/margin-bottom 1)]}
        [typography/Heading2 (tr [:cooperation :response])]
        [authorization-context/when-authorized :edit-application
         [form/form-modal-button
          {:max-width "sm"
           :form-value response
           :modal-title (tr [:cooperation :edit-response-title])
           :form-component [application-response-form {:e! e!
                                                       :project-id (:thk.project/id project)
                                                       :application-id (:db/id application)}]
           :button-component [buttons/button-secondary {:class "edit-response"
                                                        :size :small}
                              (tr [:buttons :edit])]}]]]
       [:div {:class (<class common-styles/margin-bottom 1)}
        [rich-text-editor/display-markdown
         (:cooperation.response/content response)]]
       [task-view/task-file-upload {:e! e!
                                    :controls controls
                                    :task related-task
                                    :linked-from [:cooperation.response (:db/id response)]
                                    :activity (:cooperation.application/activity application)
                                    :project-id (:thk.project/id project)
                                    :drag-container-id "drag-container-id"
                                    :new-document new-document
                                    :files-form files-form}]
       (authorization-context/consume
        (fn [authz]
          (let [can-upload? (boolean (and (some? related-task)
                                          (:application-editable authz)))
                error-msg (cond
                            (nil? related-task)
                            {:title (tr [:cooperation :error :upload-not-allowed])
                             :body (tr [:cooperation :error :coordination-task-missing])}

                            (not (:application-editable authz))
                            {:title (tr [:cooperation :error :upload-not-allowed])
                             :body (tr [:cooperation :error :response-not-editable])})]
            (if (empty? linked-files)
              [common/popper-tooltip error-msg
               [buttons/button-primary {:size :small
                                        :on-click #(upload! {})
                                        :disabled (not can-upload?)
                                        :end-icon (r/as-element [icons/content-add])}
                (tr [:cooperation :add-files])]]
              [:div
               [:div {:class (<class common-styles/header-with-actions)}
                [typography/Heading2 (tr [:common :files])]
                [common/popper-tooltip error-msg
                 [buttons/button-primary {:size :small
                                          :on-click #(upload! {})
                                          :disabled (not can-upload?)
                                          :end-icon (r/as-element [icons/content-add])}
                  (tr [:cooperation :add-files])]]]
               [link-view/links
                {:e! e!
                 :links linked-files
                 :editable? false
                 :link-entity-opts {:file
                                    {:column-widths [3 1]
                                     :allow-replacement-opts
                                     {:e! e!
                                      :task related-task
                                      :small true}}}}]]))))])))


(defn- opinion-form [{:keys [e! application]} close-event form-atom]
  (let [form-value @form-atom
        opinion-eid (:db/id form-value)
        application-eid (:db/id application)]
    (log/debug "db/id of opinion-form form-value: " opinion-eid)
    [form/form
     {:e! e!
      :value form-value
      :on-change-event (form/update-atom-event form-atom merge)
      :cancel-event close-event
      :save-event #(cooperation-controller/save-opinion-event
                    application
                    (rich-text-editor/form-data-with-rich-text :cooperation.opinion/comment @form-atom)
                    close-event)
      :delete  (fn []
                 (log/debug "calling SaveForm delete from opinion-form")
                 (common-controller/->SaveForm
                  :cooperation/delete-contact-info ;; command here
                  {:application-id application-eid}
                  (fn [_response]
                    ;; (reset! edit-contact? false) 
                    (reset! form-atom {}) 
                    common-controller/refresh-fx)))
      ;; :delete #(log/debug "delete callback" %)
      :delete-link? false
      :spec ::cooperation-model/opinion-form}
     ^{:attribute :cooperation.opinion/status :xs 10}
     [select/select-enum {:e! e!
                          :attribute :cooperation.opinion/status}]


     ^{:attribute :cooperation.opinion/comment
       :validate rich-text-editor/validate-rich-text-form-field-not-empty}
     [rich-text-editor/rich-text-field {}]]))

(defn- edit-opinion [e! application button-component]
  [authorization-context/when-authorized :save-opinion
   [form/form-modal-button
    {:max-width "sm"
     :modal-title (tr [:cooperation :create-opinion-title])
     :form-component [opinion-form {:e! e! :application application}]
     :form-value (:cooperation.application/opinion application {})
     :button-component button-component}]])

(defn- delete-opinion [e! application button-component]
  ;; todo: identical with edit-opinion? just change the name and pass different button comps?
  [authorization-context/when-authorized :save-opinion
   [form/form-modal-button
    {:max-width "sm"
     :modal-title (tr [:cooperation :delete-opinion-title])
     :form-component [opinion-form {:e! e! :application application}]
     :form-value (:cooperation.application/opinion application {})
     :button-component button-component}]])

(defn- application-conclusion [e! {:cooperation.application/keys [opinion] :as application}]
  [:<>
   [:div.application-conclusion {:class (<class common-styles/margin-bottom 1)}
    [typography/Heading2 {:class (<class common-styles/margin-bottom 1)}
     (tr [:cooperation :opinion-title])]
    (log/debug "opinion data:" (pr-str opinion))
    [opinion-view {:edit-button [edit-opinion e! application
                                 [buttons/button-secondary {:size "small"
                                                            :class "edit-opinion"
                                                            :disabled (boolean (not opinion))}
                                  (tr [:buttons :edit])]]
                   ;; :delete-button [buttons/delete-button-with-confirm {:id (str "opinion-delete-button-" (:db/id opinion))
                   ;;                                                     :clear? true
                   ;;                                                     ;; :action #(e! (cooperation-controller/->DeleteOpinion opinion))
                   ;;                                                     :action #(cooperation-controller/delete-opinion-action opinion)
                   ;;                                                     }]
                   :delete-button [delete-opinion e! application
                                   [buttons/button-secondary {:size "small"
                                                              :class "delete-opinion"
                                                              :disabled (boolean (not opinion))}
                                    (tr [:buttons :delete])]]
                   }
     application]]
   (when (not opinion)
     [edit-opinion e! application
      [buttons/button-primary {:class "create-opinion"}
       (tr [:cooperation :create-opinion-button])]])])

(defn application-people-panel [e! {id :db/id :cooperation.application/keys [activity contact]}]
  (r/with-let [edit-contact? (r/atom false)
               edit-contact! #(reset! edit-contact? true)
               contact-form (r/atom contact)]
    [:div.application-people {:class (<class common-styles/flex-column-1)}
     [typography/Heading2 {:class (<class common-styles/margin-bottom 1)}
      (tr [:project :tabs :people])]
     [:div {:class (<class common-styles/margin-bottom 1)}
      (if-let [manager (:activity/manager activity)]
        [:div {:class (<class common-styles/flex-row)}
         [:div.activity-manager-name {:class (<class common-styles/flex-table-column-style 45)}
          [user-model/user-name manager]]
         [:div.activity-manager-role {:class (<class common-styles/flex-table-column-style 55 :space-between)}
          (tr [:fields :activity/manager])]]
        [:div
         [typography/GreyText (tr [:cooperation :no-activity-manager])]])]

     (if @edit-contact?
       [form/form {:e! e!
                   :class "edit-contract-form"              ;; Give a simple class to override the form/form default
                   :on-change-event (form/update-atom-event contact-form merge)
                   :spec ::cooperation-model/contact-form
                   :save-event #(common-controller/->SaveForm
                                 :cooperation/save-contact-info
                                 {:application-id id
                                  :contact-form (merge (select-keys contact [:db/id])
                                                       @contact-form)}
                                 (fn [_response]
                                   (reset! edit-contact? false)
                                   common-controller/refresh-fx))
                   :cancel-event (form/update-atom-event edit-contact? (constantly false))
                   :delete (when (:db/id contact)
                             (common-controller/->SaveForm
                              :cooperation/delete-contact-info
                              {:application-id id}
                              (fn [_response]
                                (reset! edit-contact? false)
                                (reset! contact-form {})
                                common-controller/refresh-fx)))
                   :delete-link? true
                   :value @contact-form}
        ^{:attribute :cooperation.contact/name}
        [text-field/TextField {}]

        ^{:attribute :cooperation.contact/company}
        [text-field/TextField {}]

        ^{:attribute :cooperation.contact/id-code}
        [text-field/TextField {}]

        ^{:attribute :cooperation.contact/email}
        [text-field/TextField {}]

        ^{:attribute :cooperation.contact/phone}
        [text-field/TextField {}]]

       [:<>
        ;; Show contact info (if any)
        (when (seq contact)
          [:div.application-contact-info
           [typography/Heading3 {:class (<class common-styles/margin-bottom 1)}
            (tr [:cooperation :application-contact-person])]
           (doall
            (for [k [:cooperation.contact/name
                     :cooperation.contact/company
                     :cooperation.contact/id-code
                     :cooperation.contact/email
                     :cooperation.contact/phone]
                  :let [v (get contact k)]
                  :when v]
              ^{:key (str k)}
              [:div {:class (<class common-styles/flex-row)}
               [:div {:class (<class common-styles/flex-table-column-style 45)}
                (tr [:fields k])]
               [:div {:class (<class common-styles/flex-table-column-style 55)}
                v]]))])
        ;; "Add contact" or "edit" button
        [:div {:style {:align-self :flex-end}}
         (if contact
           [buttons/button-secondary {:on-click edit-contact!
                                      :data-cy "edit-contact"}
            (tr [:buttons :edit])]
           [buttons/button-primary {:on-click edit-contact!
                                    :size "small"
                                    :end-icon (r/as-element
                                               [icons/content-add])
                                    :data-cy "add-contact"}
            (tr [:cooperation :add-application-contact])])]])]))

;; entrypoint from route /projects/:project/cooperation/:third-party/:application
(defn application-page [e! app {:keys [project overview third-party related-task files-form]}]
  (let [application (get-in third-party [:cooperation.3rd-party/applications 0])]
    [authorization-context/with
     {:edit-application (and (authorization-check/authorized?
                               {:functionality :cooperation/edit-application
                                :entity application})
                             (cooperation-model/editable? application))
      :edit-application-right (authorization-check/authorized?
                                {:functionality :cooperation/edit-application
                                 :entity application})
      :application-editable (cooperation-model/editable? application)
      :save-opinion (authorization-check/authorized?
                      {:functionality :cooperation/application-approval
                       :entity application})}
     [:div.cooperation-application-page {:class (<class common-styles/flex-column-1)}
      [cooperation-page-structure
       e! app project overview
       [:<>
        [:div {:class (<class common-styles/margin-bottom 1)}
         [common/header-with-actions (:cooperation.3rd-party/name third-party)]
         [typography/Heading2
          (tr-enum (:cooperation.application/type application)) " / "
          (tr-enum (:cooperation.application/response-type application))]
         [typography/SectionHeading {:style {:text-transform :uppercase}}
          (tr-enum (get-in application [:cooperation.application/activity :activity/name]))]]
        [application-information application]
        (if (:cooperation.application/response application)
          [:<>
           [application-response e! (:new-document app) files-form application project related-task]
           [application-conclusion e! application project]]
          [authorization-context/when-authorized :edit-application
           [form/form-modal-button
            {:max-width "sm"
             :modal-title (tr [:cooperation :add-application-response])
             :form-component [application-response-form {:e! e!
                                                         :project-id (:thk.project/id project)
                                                         :application-id (:db/id application)}]
             :button-component [buttons/button-primary {:class "enter-response"}
                                (tr [:cooperation :enter-response])]}]])
        ;; wrong place for delete button? should it be in opinion-form?
        #_(when (= (:cooperation.application/type application) :cooperation.application.response-type/opinion)
          [authorization-context/when-authorized :save-opinion
           [form/form-modal-button
            {:max-width "sm"
             :modal-title (tr [:cooperation :add-application-response])
             :form-component [application-response-form {:e! e!
                                                         :project-id (:thk.project/id project)
                                                         :application-id (:db/id application)}]
             :button-component [buttons/button-primary {:class "enter-response"}
                                (tr [:cooperation :enter-response])]}]])]

       ;; The people panel
       [application-people-panel e! application]]]]))
