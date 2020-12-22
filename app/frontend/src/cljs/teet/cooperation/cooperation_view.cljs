(ns teet.cooperation.cooperation-view
  (:require [herb.core :as herb :refer [<class]]
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
            [teet.ui.panels :as panels]
            [teet.file.file-controller :as file-controller]
            [teet.file.file-view :as file-view]
            [teet.task.task-view :as task-view]
            [teet.link.link-view :as link-view]
            [teet.ui.authorization-context :as authorization-context]
            [teet.authorization.authorization-check :as authorization-check]))


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

(defn- application-link [{id :db/id :cooperation.application/keys [type response-type]}]
  [url/Link {:page :cooperation-application
             :params {:application (str id)}}
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
    [color-and-status
     (cooperation-style/opinion-status-color status)
     (tr-enum status)]))


(defn- opinion-view
  ([opinion] (opinion-view {} opinion))
  ([{:keys [edit-button]}
    {:cooperation.application/keys [opinion]
     application-creator :meta/creator}]
   (let [{:meta/keys [creator modifier created-at modified-at]
          comment :cooperation.opinion/comment} opinion]
     [:<>
      [:div {:class (<class common-styles/flex-row)}
       [:div {:class (<class common-styles/flex-table-column-style 20)}
        (tr [:cooperation :competent-authority-name])]
       [:div {:class (<class common-styles/flex-table-column-style 30)}
        ;; Show last modifier or creator of the opinion if present
        ;; otherwise show creator of the application
        (user-model/user-name (or modifier creator application-creator))]
       [:div {:class (<class common-styles/flex-table-column-style 50)
              ;; PENDING: somewhat ugly styling
              :style {:flex-direction :column
                      :align-self :start
                      :place-items :flex-start}}
        [:div {:class (<class common-styles/flex-row)
               :style {:width "100%"
                       :justify-content :space-between}}
         (opinion-status (:cooperation.opinion/status opinion))
         edit-button]
        (when-let [date (or modified-at created-at)]
          [:div
           [typography/BoldGreyText {:style {:display :inline-block}}
            (str (tr [:common :date]) ":")]
           (str " " (format/date date))])]]
      (when comment
        [:div [rich-text-editor/display-markdown comment]])])))

(defn cooperation-opinion
  [{:cooperation.application/keys [opinion]}]
  [common/basic-information-row
   {:right-align-last? false}
   [[(tr [:cooperation :conclusion])
     ;; colored circle based on status
     [opinion-status (:cooperation.opinion/status opinion)]]
    (when-let [date (:meta/created-at opinion)]
      [(tr [:common :date])
       date])]])

(defn- application-information [{:cooperation.application/keys [response] :as application}]
  [:div {:class (common-styles/margin-bottom 1)}
   (if response
     (let [{:cooperation.response/keys [date valid-until]} response]
       [common/basic-information-row
        {:right-align-last? false}
        [[(tr [:cooperation :response-of-third-party])
          ;; colored circle based on status
          [response-status response]]
         [(tr [:fields :cooperation.response/date])
          (format/date date)]
         (when valid-until
           [(tr [:fields :cooperation.response/valid-until])
            (format/date valid-until)
            {:data-cy-test "valid-until"}])]])
     (let [{:cooperation.application/keys [date response-deadline]} application]
       [common/basic-information-row
        {:right-align-last? false}
        [[(tr [:fields :cooperation.response/status])
          ;; colored circle based on status
          [response-status response]]
         [(tr [:fields :cooperation.application/date])
          (format/date date)]
         [(tr [:fields :cooperation.application/response-deadline])
          (or (format/date response-deadline) (tr [:cooperation :no-deadline]))]]]))])

(defn application-list-item
  [{:keys [parent-link-comp]} {:cooperation.application/keys [activity] :as application}]
  [:div {:class (<class cooperation-style/application-list-item-style)}

   (when parent-link-comp
     parent-link-comp)
   (when application
     [:div
      [:div {:class [(<class common-styles/flex-row)
                     (<class common-styles/margin-bottom 1)]}
       [application-link application]
       [typography/GreyText {:style {:margin-left "0.5rem"}}
        (tr-enum (:activity/name activity))]]
      [application-information application]
      [typography/Text {:class (<class common-styles/margin-bottom 1)}
       (tr [:cooperation :responsible-person]
           {:user-name (user-model/user-name (:activity/manager activity))})]
      [opinion-view application]])])

(defn- third-parties [e! project third-parties selected-third-party-name]
  [:div {:class (<class project-navigator-view/navigator-container-style true)}
   [:div.project-third-parties-list
    {:class (<class third-parties-navigator)}
    [:ul {:class (<class third-party-list)}
     (for [{id :db/id
            :cooperation.3rd-party/keys [name]} third-parties]
       (let [currently-selected-third-party? (= name selected-third-party-name)]
         ^{:key (str id)}
         [:li.project-third-party
          [url/Link {:page :cooperation-third-party
                     :class (<class common-styles/white-link-style
                                    currently-selected-third-party?)
                     :params
                     {:third-party (js/encodeURIComponent name)}}
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

(defn- selected-third-party-name [{:keys [params] :as _app}]
  (-> params :third-party js/decodeURIComponent))

(defn- cooperation-page-structure [e! app project third-parties-list main-content]
  [project-view/project-full-page-structure
   {:e! e!
    :app app
    :project project
    :left-panel [third-parties e! project third-parties-list (selected-third-party-name app)]
    :main main-content}])

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
     (for [{:cooperation.3rd-party/keys [name applications]} overview]
       [url/with-navigation-params
        {:third-party (js/encodeURIComponent name)}
        (let [application (first applications)]
          [:div {:data-third-party name}
           [application-list-item
            {:parent-link-comp [url/Link {:style {:font-size "1.5rem"
                                                  :display :block}
                                          :class (<class common-styles/margin-bottom 1.5)
                                          :page :cooperation-third-party
                                          :params {:third-party (js/encodeURIComponent name)}}
                                name]}
            application]])])]]])

(defn- applications [{:cooperation.3rd-party/keys [applications] :as _third-party}]
  [:<>
   (for [{id :db/id :as application} applications]
     ^{:key (str id)}
     [application-list-item {} application])])

(defn- application-form [{:keys [e! project-id third-party]} close-event form-atom]
  [form/form {:e! e!
              :value @form-atom
              :on-change-event (form/update-atom-event form-atom merge)
              :cancel-event close-event
              :save-event #(common-controller/->SaveForm
                            :cooperation/create-application
                            {:thk.project/id project-id
                             :cooperation.3rd-party/name third-party
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

(defn third-party-page [e! {:keys [params] :as app} {:keys [project overview]}]
  (let [third-party-name (js/decodeURIComponent (:third-party params))
        third-party (some #(when (= third-party-name
                                    (:cooperation.3rd-party/name %)) %)
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
                                             :third-party third-party-name}]
          :modal-title (tr [:cooperation :new-application-title])
          :button-component [buttons/button-primary {:class :new-application}
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
                                              (-> @form-atom
                                                  (update :cooperation.response/content
                                                          (fn [editor-state]
                                                            (if (or (string? editor-state) (nil? editor-state))
                                                              editor-state
                                                              (rich-text-editor/editor-state->markdown editor-state))))))}
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
      [:div
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
           :button-component [buttons/button-secondary {:class :edit-response
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
                                          (:edit-application authz)))]
            (if (empty? linked-files)
              [buttons/button-primary {:size :small
                                       :on-click #(upload! {})
                                       :disabled (not can-upload?)
                                       :end-icon (r/as-element [icons/content-add])}
               (tr [:cooperation :add-files])]
              [:div
               [:div {:class (<class common-styles/header-with-actions)}
                [typography/Heading2 (tr [:common :files])]
                [buttons/button-primary {:size :small
                                         :on-click #(upload! {})
                                         :disabled (not can-upload?)
                                         :end-icon (r/as-element [icons/content-add])}
                 (tr [:cooperation :add-files])]]
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
  [form/form
   {:e! e!
    :value @form-atom
    :on-change-event (form/update-atom-event form-atom merge)
    :cancel-event close-event
    :save-event #(cooperation-controller/save-opinion-event
                  application @form-atom close-event)}
   ^{:attribute :cooperation.opinion/status :xs 10}
   [select/select-enum {:e! e!
                        :attribute :cooperation.opinion/status}]


   ^{:attribute :cooperation.opinion/comment}
   [rich-text-editor/rich-text-field {}]])

(defn- edit-opinion [e! application button-component]
  [authorization-context/when-authorized :save-opinion
   [form/form-modal-button
    {:max-width "sm"
     :modal-title (tr [:cooperation :create-opinion-title])
     :form-component [opinion-form {:e! e! :application application}]
     :form-value (:cooperation.application/opinion application {})
     :button-component button-component}]])

(defn- application-conclusion [e! {:cooperation.application/keys [opinion] :as application}]
  [:div.application-conclusion {:style (common-styles/margin-bottom 1)}
   [typography/Heading2 (tr [:cooperation :opinion-title])]
   [opinion-view {:edit-button [edit-opinion e! application
                                [buttons/button-secondary {:size "small"
                                                           :disabled (boolean (not opinion))}
                                 (tr [:buttons :edit])]]} application]
   (when (not opinion)
     [edit-opinion e! application
      [buttons/button-primary {:class :create-opinion}
       (tr [:cooperation :create-opinion-button])]])])

(defn application-page [e! app {:keys [project overview third-party related-task files-form]}]
  (let [application (get-in third-party [:cooperation.3rd-party/applications 0])]
    [authorization-context/with
     {:edit-application (and (authorization-check/authorized?
                              {:functionality :cooperation/edit-application
                               :entity application})
                             (cooperation-model/editable? application))
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
             :button-component [buttons/button-primary {:class :enter-response}
                                (tr [:cooperation :enter-response])]}]])]]]]))
