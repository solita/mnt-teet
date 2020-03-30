(ns teet.task.task-view
  "View for a workflow task"
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.task.task-controller :as task-controller]
            [teet.task.task-style :as task-style]
            [teet.localization :refer [tr tr-enum]]
            [teet.ui.buttons :as buttons]
            [teet.ui.format :as format]
            [teet.ui.material-ui :refer [Link LinearProgress]]
            [teet.ui.icons :as icons]
            [teet.ui.typography :as typography]
            [teet.ui.panels :as panels]
            [teet.ui.url :as url]
            teet.task.task-spec
            [teet.project.task-model :as task-model]
            [teet.file.file-controller :as file-controller]
            [teet.comments.comments-view :as comments-view]
            [teet.ui.form :as form]
            [teet.common.common-controller :as common-controller]
            [teet.ui.file-upload :as file-upload]
            [teet.ui.select :as select]
            [teet.ui.common :as common]
            [teet.comments.comments-controller :as comments-controller]
            [teet.project.project-navigator-view :as project-navigator-view]
            [teet.project.project-style :as project-style]
            [teet.project.project-model :as project-model]
            [teet.user.user-model :as user-model]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.tabs :as tabs]
            [teet.common.common-styles :as common-styles]
            [teet.file.file-view :as file-view]
            [teet.ui.date-picker :as date-picker]
            [teet.project.project-controller :as project-controller]))

(defn task-status [e! {:task/keys [status]}]
  [select/select-enum {:e! e!
                       :on-change (e! task-controller/->UpdateTaskStatus)
                       :value (:db/ident status)
                       :show-label? false
                       :attribute :task/status
                       :values-filter task-model/current-statuses}])

(defn get-latest-modification
  "Takes the document and returns the latest modification time from either files or the doc it self"
  [{:meta/keys     [created-at modified-at]
    :document/keys [files]}]
  (let [latest-change
        (reduce (fn [latest-time {:meta/keys [created-at]}]
                  (if (> latest-time created-at)
                    latest-time
                    created-at))
                (or modified-at created-at)
                files)]
    latest-change))

(defn task-navigation
  [{:task/keys [documents] :as task} {selected-file-id     :file
                                      selected-document-id :document}]
  [:div {:style {:padding "2rem 0 2rem 2rem"}}
   (if (and (nil? selected-file-id) (nil? selected-document-id))
     [:b {:class (<class task-style/study-link-style)}
      (tr [:enum (-> task :task/type :db/ident)])]
     [Link {:class (<class task-style/study-link-style)
            :href  (url/set-query-param :document nil
                                        :file nil)}
      (tr [:enum (-> task :task/type :db/ident)])])
   [:p
    [:b (tr [:task :results])]]
   (doall
     (for [{:document/keys [name status files]
            :as document} documents]
       ^{:key (str (:db/id document))}
       [:div
        [:div
         (if (and (= (str (:db/id document)) selected-document-id) (nil? selected-file-id))
           [:b {:class (<class task-style/result-style)}
            name]
           [Link {:class (<class task-style/result-style)
                  :href  (url/set-query-param :document (:db/id document)
                                              :file nil)}
            name])
         [typography/SmallText {:style {:margin-bottom "0.5rem"}}
          [:span {:style {:font-weight    :bold
                          :text-transform :uppercase}}
           (tr [:enum (:db/ident status)])]
          [:span
           " " (tr [:common :last-modified]) ": " (format/date (get-latest-modification document))]]]
        (for [{:file/keys [name size] :as file
               file-id    :db/id} files]
          ^{:key (str file-id)}
          [:div {:class (<class task-style/file-container-style)}
           (if (= (str file-id) selected-file-id)
             [:b {:class (<class task-style/document-file-name)} name]
             [Link {:class (<class task-style/document-file-name)
                    :href  (url/set-query-param :document (:db/id document)
                                                :file (:db/id file))}
              name])
           [typography/SmallText (format/file-size size)]])]))])



(defn task-basic-info
  [e! {:task/keys [deadline assignee] :as task}]
  [:div {:class [(<class common-styles/flex-row-space-between) (<class common-styles/margin-bottom 1)]}
   [:div
    [typography/BoldGreyText (tr [:common :deadline])]
    [:span (format/date deadline)]]
   [:div
    [typography/BoldGreyText (tr [:fields :task/assignee])]
    [:span (user-model/user-name assignee)]]
   [:div
    [task-status e! task]]])

(defn task-details
  [e! params {:task/keys [description files] :as task}]
  [:div
   (when description
     [typography/Paragraph description])
   [task-basic-info e! task]
   [file-view/file-table files]])

(defn- task-header
  [e! task]
  [:div {:class (<class common-styles/heading-and-button-style)}
   [typography/Heading1 (tr-enum (:task/type task))]
   [buttons/button-secondary {:on-click #(e! (project-controller/->OpenEditTaskDialog (:db/id task)))}
    (tr [:buttons :edit])]])

(defn task-page-content
  [e! app task]
  [:div
   [task-header e! task]
   [tabs/details-and-comments-tabs
    {:e! e!
     :app app
     :type :task-comment
     :comment-command :comment/comment-on-task
     :entity-type :task
     :entity-id (:db/id task)}
    [task-details e! (:params app) task]]])

(defn- add-files-form [e! upload-progress]
  (r/with-let [form (r/atom {})]
    [:<>
     [form/form {:e!              e!
                 :value           @form
                 :on-change-event (form/update-atom-event form merge)
                 :save-event      (partial file-controller/->AddFilesToTask (:task/files @form))
                 :cancel-event    #(common-controller/->SetQueryParam :add-files nil)
                 :in-progress?    upload-progress
                 :spec :task/add-files}
      ^{:attribute :task/files}
      [file-upload/files-field {}]]
     (when upload-progress
       [LinearProgress {:variant "determinate"
                        :value   upload-progress}])]))


(defn task-form [_e! {:keys [initialization-fn]}]
  ;;Task definition (under project activity)
  ;; Task type (a predefined list of tasks: topogeodeesia, geoloogia, liiklusuuring, KMH eelhinnang, loomastikuuuring, arheoloogiline uuring, muu)
  ;; Description (short description of the task for clarification, 255char, in case more detailed description is needed, it will be uploaded as a file under the task)
  ;; Responsible person (email)
  (when initialization-fn
    (initialization-fn))
  (fn [e! {id :db/id :as task}]
    [form/form {:e! e!
                :value task
                :on-change-event task-controller/->UpdateEditTaskForm
                :cancel-event project-controller/->CloseDialog
                :save-event task-controller/->SaveTaskForm
                :delete (when id (task-controller/->DeleteTask id))
                :spec :task/new-task-form}
     ^{:xs 6 :attribute :task/group}
     [select/select-enum {:e! e! :attribute :task/group}]
     ^{:xs 6 :attribute :task/type}
     [select/select-enum {:e! e! :attribute :task/type
                          :enum/valid-for (:task/group task)}]

     ^{:attribute :task/description}
     [TextField {:full-width true :multiline true :rows 4 :maxrows 4}]

     ^{:attribute [:task/estimated-start-date :task/estimated-end-date] :xs 12}
     [date-picker/date-range-input {:start-label (tr [:fields :task/estimated-start-date])
                                    :end-label (tr [:fields :task/estimated-end-date])}]

     ^{:attribute [:task/actual-start-date :task/actual-end-date] :xs 12}
     [date-picker/date-range-input {:start-label (tr [:fields :task/actual-start-date])
                                    :end-label (tr [:fields :task/actual-end-date])}]


     ^{:attribute :task/assignee}
     [select/select-user {:e! e! :attribute :task/assignee}]]))

(defmethod project-navigator-view/project-navigator-dialog :add-task
  [{:keys [e! app]} _dialog]
  [task-form e! (:edit-task-data app)])

(defmethod project-navigator-view/project-navigator-dialog :edit-task
  [{:keys [e! app]}  _dialog]
  [task-form e! (:edit-task-data app)])


(defn task-page [e! {{:keys [add-document] :as query} :query
                     {task-id :task :as params} :params
                     new-document :new-document :as app}
                 project
                 breadcrumbs]
  [:div {:class (<class project-style/page-container)}
   [panels/modal {:open-atom (r/wrap (boolean add-document) :_)
                  :title     (tr [:task :add-document])
                  :on-close  (e! task-controller/->CloseAddDocumentDialog)}
    [add-files-form e! (:in-progress? new-document)]]

   [project-navigator-view/project-navigator-with-content
    {:e! e!
     :project project
     :app app
     :breadcrumbs breadcrumbs}

    [task-page-content e! app
     (project-model/task-by-id project task-id)]]])
