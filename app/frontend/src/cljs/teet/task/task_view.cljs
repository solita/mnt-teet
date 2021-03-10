(ns teet.task.task-view
  "View for a workflow task"
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.activity.activity-model :as activity-model]
            [teet.authorization.authorization-check :refer [when-authorized]]
            [teet.common.common-styles :as common-styles]
            [teet.file.file-controller :as file-controller]
            [teet.file.file-view :as file-view]
            [teet.localization :refer [tr tr-enum]]
            [teet.project.project-controller :as project-controller]
            [teet.project.project-model :as project-model]
            [teet.project.project-navigator-view :as project-navigator-view]
            [teet.project.task-model :as task-model]
            [teet.task.task-controller :as task-controller]
            teet.task.task-spec
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.buttons :as buttons]
            [teet.ui.date-picker :as date-picker]
            [teet.ui.file-upload :as file-upload]
            [teet.ui.form :as form]
            [teet.ui.format :as format]
            [teet.ui.icons :as icons]
            [teet.ui.material-ui :refer [Grid LinearProgress]]
            [teet.ui.panels :as panels]
            [teet.ui.select :as select]
            [teet.ui.tabs :as tabs]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.typography :as typography]
            [teet.ui.util :as util :refer [mapc]]
            [teet.user.user-model :as user-model]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]
            [teet.log :as log]
            [teet.ui.drag :as drag]
            [goog.string :as gstr]
            [teet.authorization.authorization-check :as authorization-check]
            [teet.common.common-controller :as common-controller]
            [teet.snackbar.snackbar-controller :as snackbar-controller]
            [teet.ui.rich-text-editor :as rich-text-editor]))


(defn- task-groups-for-activity [activity-name task-groups]
  (filter (comp (get activity-model/activity-name->task-groups activity-name #{})
                :db/ident)
          (sort-by (comp task-model/task-group-order :db/ident)
                   task-groups)))

(defn- task-selection [{:keys [e! on-change-selected on-change-sent existing selected sent-to-thk activity-name]
                        :or {existing #{}}}
                       task-groups task-types]
  [:div {:style {:max-height "70vh" :overflow-y :scroll}}
   (mapc (fn [g]
           [:div
            [typography/Heading2 {:style {:font-variant :all-small-caps
                                          :font-weight :bold}}
             (tr-enum g)]
            [:ul
             (mapc (fn [{id :db/ident :as t}]
                     [:div {:class (<class common-styles/flex-row)}
                      [:div {:class (herb/join (<class common-styles/flex-table-column-style 50)
                                               (<class common-styles/no-border))}
                       [select/checkbox {:label (tr-enum t)
                                         :disabled (existing id)
                                         :value (boolean (or (existing id)
                                                             (selected [(:db/ident g) id])))
                                         :on-change (when-not (existing id)
                                                      #(on-change-selected
                                                        (cu/toggle selected [(:db/ident g) id])))}]]
                      [:div {:class (herb/join (<class common-styles/flex-table-column-style 50)
                                               (<class common-styles/no-border))}
                       (when (and (:thk/task-type t)
                                  (selected [(:db/ident g) id]))
                         [select/checkbox {:label (tr [:fields :task/send-to-thk?])
                                           :value (boolean (sent-to-thk [(:db/ident g) id]))
                                           :on-change #(on-change-sent
                                                             (cu/toggle sent-to-thk [(:db/ident g) id]))}])]])
                   (filter #(= (:db/ident g) (:enum/valid-for %)) task-types))]])
         (task-groups-for-activity activity-name task-groups))])

(defn task-groups-and-tasks [{e! :e! :as opts} task-groups]
  [select/with-enum-values {:e! e! :attribute :task/type}
   [task-selection opts task-groups]])

(defn existing-uncompleted-tasks [activity]
  (or (->> activity
           :activity/tasks
           (remove task-model/completed?)
           (map (comp :db/ident :task/type))
           set)
      #{}))

(defn add-tasks-form [e! tasks activity {:keys [max-date min-date]}]
  (let [activity-name (-> activity :activity/name :db/ident)]
    [form/form2 {:e! e!
                 :value tasks
                 :on-change-event task-controller/->UpdateAddTasksForm
                 :cancel-event task-controller/->CloseAddTasksDialog
                 :save-event (partial task-controller/->SaveAddTasksForm
                                      activity-name)
                 :spec :activity/add-tasks
                 :in-progress? (:in-progress? tasks)}
     [Grid {:container true :style {:height "90%"} :spacing 3}
      [Grid {:item true :xs 4}
       [form/field {:attribute [:task/estimated-start-date :task/estimated-end-date]}
        [date-picker/date-range-input {:row? false
                                       :max-date max-date
                                       :min-date min-date
                                       :start-label (tr [:fields :task/estimated-start-date])
                                       :end-label (tr [:fields :task/estimated-end-date])}]]]

      [Grid {:item true :xs 8}
       [select/with-enum-values {:e! e!
                                 :attribute :task/group}
        [task-groups-and-tasks {:e! e!
                                :on-change-selected #(e! (task-controller/->UpdateAddTasksForm
                                                          {:activity/tasks-to-add %}))
                                :on-change-sent #(e! (task-controller/->UpdateAddTasksForm
                                                      {:sent-tasks %}))
                                :activity-name activity-name
                                :existing (existing-uncompleted-tasks activity)
                                :selected (or (:activity/tasks-to-add tasks) #{})
                                :sent-to-thk (or (:sent-tasks tasks) #{})}]]]

      [Grid {:item true :xs 12}
       [:div {:style {:display :flex :justify-content :flex-end}}
        [form/footer2]]]]]))

(defn task-basic-info
  [{:task/keys [estimated-end-date assignee actual-end-date status] :as _task}]
  [:div.task-basic-info {:class [(<class common-styles/flex-row-space-between) (<class common-styles/margin-bottom 1)]}
   [:div.task-basic-info-end-date
    [typography/BoldGreyText (tr [:common :deadline])]
    [:span.task-basic-info-value (format/date estimated-end-date)]]
   (when actual-end-date
     [:div.task-basic-info-end-date
      [typography/BoldGreyText (tr [:fields :task/actual-end-date])]
      [:span.task-basic-info-value (format/date actual-end-date)]])
   [:div.task-basic-info-assignee
    [typography/BoldGreyText (tr [:fields :task/assignee])]
    [:span.task-basic-info-value (user-model/user-name assignee)]]
   [:div.task-basic-info-status
    (tr-enum status)]])

(defn submit-results-button [e! task]
  (r/with-let [clicked? (r/atom false)]
    [:<>
     [buttons/button-primary {:on-click #(reset! clicked? true)
                              :style {:float :right}
                              :start-icon (r/as-element [icons/action-check-circle])}
      (tr [:task :submit-results])]
     (when @clicked?
       [panels/modal {:title (str (tr [:task :submit-results]) "?")
                      :on-close #(reset! clicked? false)}
        [:<>
         [:div {:style {:padding "1rem"
                        :margin-bottom "2rem"
                        :background-color theme-colors/gray-lightest}}
          (tr [:task :submit-results-confirm]
              {:task (tr-enum (:task/type task))})]
         [:div {:style {:display :flex
                        :justify-content :flex-end}}
          [buttons/button-secondary {:on-click #(reset! clicked? false)}
           (tr [:buttons :cancel])]
          [buttons/button-primary {:on-click (e! task-controller/->SubmitResults)
                                   :style {:margin-left "1rem"}}
           (tr [:buttons :confirm])]]]])]))

(defn- add-files-form [e! project-id activity task files-form upload-progress close! linked-from] ;;TODO make map
  (r/with-let [reinstate-drop-zones! (drag/suppress-drop-zones!)]
    [:<>
     [form/form
      {:e!              e!
       :value           files-form
       :on-change-event file-controller/->UpdateFilesForm
       :save-event      #(file-controller/->AddFilesToTask files-form task close! linked-from)
       :cancel-fn close!
       :in-progress?    upload-progress
       :spec :task/add-files}
      (when (seq (:file.part/_task task))
        ^{:attribute :file/part}
        [select/form-select {:items (:file.part/_task task)
                             :label (tr [:file-upload :select-part-to-upload])
                             :show-empty-selection? true
                             :empty-selection-label (tr [:file-upload :general-part])
                             :format-item (fn [{:file.part/keys [name number]}]
                                            (gstr/format "%s #%02d" name number))}])
      ^{:attribute :task/files
        :validate (fn [files]
                    (some some? (map (partial file-upload/validate-file e! project-id task) files)))}
      [file-upload/files-field {:e! e!
                                :project-id project-id
                                :activity activity
                                :task task}]]
     (when upload-progress
       [LinearProgress {:variant "determinate"
                        :value   upload-progress}])]
    (finally
      (reinstate-drop-zones!))))

(defn file-part-form
  [e! task-id close-event form-data]
  [:div
   [form/form {:e! e!
               :value @form-data
               :on-change-event (form/update-atom-event form-data merge)
               :cancel-event close-event
               :spec :task/create-part
               :save-event #(task-controller/->SavePartForm close-event task-id @form-data)
               :delete (when-let [part-id (:db/id @form-data)]
                         (task-controller/->DeleteFilePart close-event part-id))}
    ^{:attribute :file.part/name
      :validate (fn [name]
                  (when (> (count name) 99)
                    (tr [:fields :validation-error :file.part/name])))}
    [TextField {}]]])

(defn file-section-view
  [{:keys [e! upload! sort-by-value allow-replacement-opts
           land-acquisition?]} task file-part files]
  [:div
   [file-view/file-part-heading {:heading (:file.part/name file-part)
                                 :number (:file.part/number file-part)}
    {:action (when (task-model/can-submit? task)
               [when-authorized
                :task/create-part task
                [:div {:class (<class common-styles/flex-row)}
                 [form/form-modal-button
                  {:form-component [file-part-form e! (:db/id task)]
                   :form-value file-part
                   :modal-title [:div {:style {:display :flex}}
                                 [:p {:class (<class common-styles/margin-right 0.5)}
                                  (tr [:task :edit-part-modal-title])]
                                 [typography/GreyText (gstr/format "#%02d" (:file.part/number file-part))]]
                   :button-component
                   [buttons/button-secondary
                    {:size :small
                     :style {:margin-right "0.5rem"}}
                    (tr [:buttons :edit])]}]
                 [buttons/button-primary {:size :small
                                          :start-icon (r/as-element [icons/content-add])
                                          :on-click #(upload! {:file/part file-part})}
                  (tr [:buttons :upload])]]])}]
   (if (seq files)
     [file-view/file-list2 {:e! e!
                            :allow-replacement-opts allow-replacement-opts
                            :sort-by-value sort-by-value
                            :download? true
                            :land-acquisition? land-acquisition?} files]
     [file-view/no-files])])

(defn task-file-heading
  [task upload! {:keys [sort-by-atom
                        items]}]
  [:div {:class [(<class common-styles/space-between-center)
                 (<class common-styles/margin-bottom 1)]}

   [:div {:style {:margin-right "0.5rem"
                  :display :flex
                  :align-items :flex-end}}
    [typography/Heading2 {:style {:margin-right "0.5rem"
                                  :display :inline-block}}
     (tr [:common :files])]
    [file-view/file-sorter sort-by-atom items]]
   (when (task-model/can-submit? task)
     [when-authorized :file/upload
      task
      [:div
       [buttons/button-primary {:start-icon (r/as-element [icons/content-add])
                                :on-click #(upload! {})
                                :data-cy "task-file-upload"}
        (tr [:buttons :upload])]]])])

(defn- file-content-view
  [e! upload! activity task files-form project-id files parts selected-part]
  (r/with-let [items-for-sort-select (file-view/sort-items)
               sort-by-atom (r/atom (first items-for-sort-select))]
    (let [allow-replacement-opts (when (task-model/can-submit? task)
                                   {:e! e!
                                    :task task
                                    :project-id project-id
                                    :replace-form files-form})
          land-acquisition? (du/enum= :activity.name/land-acquisition
                                      (:activity/name activity))]
      [:<>
       [task-file-heading task upload! {:sort-by-atom sort-by-atom
                                        :items items-for-sort-select}]
       (when (or (nil? selected-part) (zero? (:file.part/number selected-part))) ;; if some other part is selected hide this
         (let [general-files (remove #(contains? % :file/part) files)]
           [file-view/file-list2 {:e! e!
                                  :allow-replacement-opts allow-replacement-opts
                                  :sort-by-value @sort-by-atom
                                  :download? true
                                  :land-acquisition? land-acquisition?
                                  :data-cy "task-file-list"}
            general-files]))
       [:div
        (when (not (zero? (:file.part/number selected-part)))
          (mapc
            (fn [part]
              [file-section-view {:e! e!
                                  :sort-by-value @sort-by-atom
                                  :allow-replacement-opts allow-replacement-opts
                                  :upload! upload!
                                  :land-acquisition? land-acquisition?}
               task part
               (filterv
                 (fn [file]
                   (= (:db/id part) (get-in file [:file/part :db/id])))
                 files)])
            (if (nil? selected-part)
              parts
              [selected-part])))]])))

(defn- task-file-view
  [e! activity task upload! files-form project-id]
  (let [parts (:file.part/_task task)
        files (:task/files task)]
    [:div
     [file-view/file-search files parts
      [file-content-view e! upload! activity task files-form project-id]]
     (when (task-model/can-submit? task)
       [when-authorized :task/create-part
        task
        [form/form-modal-button
         {:form-component [file-part-form e! (:db/id task)]
          :modal-title (tr [:task :add-part-modal-title])
          :button-component
          [buttons/button-secondary
           {:start-icon (r/as-element
                          [icons/content-add])}
           (tr [:task :add-part])]}]])]))

(defn file-upload-controls
  [e!]
  (let [file-upload-open? (r/atom false)]
    {:file-upload-open? file-upload-open?
     :upload! #(do (e! (file-controller/->UpdateFilesForm %))
                   (reset! file-upload-open? true))
     :close! #(do (reset! file-upload-open? false)
                  (e! (file-controller/->AfterUploadRefresh)))}))

(defn task-file-upload
  [{:keys [e! task activity project-id controls linked-from
           drag-container-id new-document files-form]}]
  (r/with-let [{:keys [file-upload-open? upload! close!]}
               (or controls (file-upload-controls e!))]
    (when (task-model/can-submit? task)
      [:<>
       [file-upload/FileUpload {:drag-container-id drag-container-id
                                :drop-message (tr [:drag :drop-to-task])
                                :on-drop #(upload! {:task/files %})}]
       [panels/modal {:open-atom file-upload-open?
                      :max-width "lg"
                      :title [:<>
                              (tr [:task :upload-files])
                              [typography/GreyText {:style {:display :inline
                                                            :margin-left "1rem"}}
                               (str (tr-enum (:activity/name activity))
                                    " / "
                                    (tr-enum (:task/type task)))]]
                      :on-close close!}
        [add-files-form e! project-id activity task files-form
         (:in-progress? new-document)
         close! linked-from]]])))

(defn task-details
  [e! new-document project-id activity {:task/keys [description files] :as task} files-form]
  (r/with-let [upload-controls (file-upload-controls e!)]
    ^{:key (str "task-content-" (:db/id task))}
    [:div#task-details-drop.task-details
     (when description
       [typography/Paragraph
        [rich-text-editor/display-markdown description]])
     [task-basic-info task]
     [task-file-view e! activity task (:upload! upload-controls) files-form project-id]
     [task-file-upload {:e! e!
                        :task task
                        :activity activity
                        :project-id project-id
                        :drag-container-id "task-details-drop"
                        :files-form files-form
                        :controls upload-controls
                        :new-document new-document}]
     (when (and (task-model/can-submit? task)
                (seq files))
       [when-authorized :task/submit task
        [submit-results-button e! task]])
     (when (task-model/reviewing? task)
       [when-authorized :task/review task
        [:div.task-review-buttons {:style {:display :flex :justify-content :space-between}}
         [buttons/button-warning {:on-click (e! task-controller/->Review :reject)}
          (tr [:task :reject-review])]
         [buttons/button-primary {:on-click (e! task-controller/->Review :accept)}
          (tr [:task :accept-review])]]])]))

(defn- edit-task-form [e! {:keys [max-date min-date allow-delete?]}
                       close-event form-atom ]
  (let [{id :db/id send-to-thk? :task/send-to-thk? :as task} @form-atom]
    [:div.edit-task-form
     [form/form {:e! e!
                 :value task
                 :on-change-event (form/update-atom-event form-atom merge)
                 :cancel-event close-event
                 :save-event #(common-controller/->SaveForm
                               :task/update
                               (form/to-value (select-keys @form-atom task-model/edit-form-keys))
                               (fn [_response]
                                 (fn [e!]
                                   (e! (close-event))
                                   (e! (snackbar-controller/->OpenSnackBar
                                        (tr [:notifications :task-updated]) :success))
                                   (e! (common-controller/->Refresh)))))
                 :delete (when allow-delete?
                           (task-controller/->DeleteTask id))
                 :delete-message (when send-to-thk?
                                   (tr [:task :confirm-delete-task-sent-to-thk]))
                 :delete-confirm-button-text (tr [:task :confirm-delete-confirm])
                 :delete-cancel-button-text (tr [:task :confirm-delete-cancel])
                 :delete-disabled-error-text (when (seq (:task/files task))
                                               (tr [:task :delete-task-has-files]))
                 :spec :task/edit-task-form}

      ^{:attribute :task/description}
      [rich-text-editor/rich-text-field {}]

      ^{:attribute [:task/estimated-start-date :task/estimated-end-date] :xs 12}
      [date-picker/date-range-input {:start-label (tr [:fields :task/estimated-start-date])
                                     :min-date min-date
                                     :max-date max-date
                                     :end-label (tr [:fields :task/estimated-end-date])}]

      (when (not send-to-thk?)
        ^{:attribute [:task/actual-start-date :task/actual-end-date] :xs 12}
        [date-picker/date-range-input {:start-label (tr [:fields :task/actual-start-date])
                                       :min-date min-date
                                       :max-date max-date
                                       :end-label (tr [:fields :task/actual-end-date])}])
      ^{:attribute :task/assignee}
      [select/select-user {:e! e! :attribute :task/assignee}]]]))

(defn- edit-task-form-button [e! activity task allow-delete?]
  [form/form-modal-button
   {:e! e!
    :button-component [buttons/button-secondary {}
                       (tr [:buttons :edit])]
    :modal-title (tr [:project :edit-task])
    :form-component [edit-task-form e!
                     {:max-date (:activity/estimated-end-date activity)
                      :min-date (:activity/estimated-start-date activity)
                      :allow-delete? allow-delete?}]
    :form-value task}])

(defn- task-header
  [e! activity task]
  [:div.task-header {:class (<class common-styles/heading-and-action-style)}
   [typography/Heading1 (tr-enum (:task/type task))]
   [when-authorized :task/update
    task
    [authorization-check/with-authorization-check
     (if (:task/send-to-thk? task)
       :task/delete-task-sent-to-thk
       :task/delete-task)
     task

     [edit-task-form-button e! activity task]]
    ]])

(defn- start-review [e!]
  (e! (task-controller/->StartReview))
  (fn [_]
    [:span]))

(defn- task-page-content
  [e! app activity {status :task/status :as task} pm? files-form]
  [:div.task-page
   (when (and pm? (du/enum= status :task.status/waiting-for-review))
     [when-authorized :task/start-review task
      [start-review e!]])
   [task-header e! activity task]
   [tabs/details-and-comments-tabs
    {:e! e!
     :app app
     :type :task-comment
     :entity-type :task
     :entity-id (:db/id task)}
    [task-details e! (:new-document app)
     (get-in app [:params :project])
     activity task files-form]]])




(defmethod project-navigator-view/project-navigator-dialog :add-tasks
  [{:keys [e! app project]} _dialog]
  (let [activity-id (get-in app [:add-tasks-data :db/id])
        activity (project-model/activity-by-id project activity-id)]
    [add-tasks-form e!
     (:add-tasks-data app)
     activity
     {:max-date (:activity/estimated-end-date activity)
      :min-date (:activity/estimated-start-date activity)}]))

(defn- export-task-files [e! {task-id :db/id files :task/files}]
  (when (seq files)
    [{:id "export-task-files"
      :label #(tr [:file :export-files-zip :task-button])
      :icon [icons/file-file-download-outlined {:style {:color theme-colors/primary}}]
      :on-click #(e! (task-controller/->ExportFiles task-id))}]))

(defn task-page [e! {{task-id :task :as _params} :params user :user :as app}
                 project]
  (let [{activity-manager :activity/manager
         :as activity}
        (cu/find-> project
                   :thk.project/lifecycles some?
                   :thk.lifecycle/activities (fn [{:activity/keys [tasks]}]
                                               (du/find-by-id task-id tasks)))
        task (project-model/task-by-id project task-id)]
    [project-navigator-view/project-navigator-with-content
     {:e! e!
      :project project
      :app app
      :export-menu-items (export-task-files e! task)}

     [task-page-content
      e!
      app
      activity
      task
      (= (:db/id user)
         (:db/id activity-manager))
      (:files-form project)]]))
