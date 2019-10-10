(ns teet.task.task-view
  "View for a workflow task"
  (:require [teet.task.task-controller :as task-controller]
            [teet.ui.itemlist :as itemlist]
            [teet.localization :refer [tr-fixme tr]]
            [teet.ui.material-ui :refer [TextField List ListItem ListItemText
                                         ListItemIcon Button]]
            [teet.ui.icons :as icons]
            [teet.ui.layout :as layout]
            [teet.ui.select :as select]
            [teet.user.user-info :as user-info]
            [teet.ui.form :as form]
            [teet.ui.panels :as panels]
            [teet.document.document-view :as document-view]
            [teet.project.project-info :as project-info]))

(defn task-form [e! close _phase-id task]
  ;;Task definition (under project phase)
  ;; Task type (a predefined list of tasks: topogeodeesia, geoloogia, liiklusuuring, KMH eelhinnang, loomastikuuuring, arheoloogiline uuring, muu)
  ;; Description (short description of the task for clarification, 255char, in case more detailed description is needed, it will be uploaded as a file under the task)
  ;; Responsible person (email)
  [form/form {:e! e!
              :value task
              :on-change-event task-controller/->UpdateTaskForm
              :cancel-event close
              :save-event task-controller/->CreateTask}
   ^{:xs 12 :attribute :task/type}
   [select/select-enum {:e! e! :attribute :task/type}]

   ^{:attribute :task/description}
   [TextField {:full-width true :multiline true :rows 4 :maxrows 4
               :variant :outlined}]

   ^{:attribute :task/assignee}
   [select/select-user {:e! e!}]])

(defn change-task-status [e! task done-fn]
  [select/select-with-action {:items [:task.status/not-started
                                      :task.status/in-progress
                                      :task.status/completed
                                      :task.status/accepted
                                      :task.status/rejected]
                              :item-label name
                              :placeholder (tr-fixme "New status")
                              :action-icon [icons/action-done]
                              :on-select #(do
                                            (done-fn)
                                            (e! (task-controller/->UpdateTask
                                                 task
                                                 {:task/status (task-controller/new-status %)})))}])

(defn task-page [e! {query :query :as app}
                 {:task/keys [documents description type assignee] :as task}]
  [layout/section
   (when (:add-document query)
     [panels/modal {:title (tr [:task :new-document])
                    :on-close (e! task-controller/->CloseAddDocumentDialog)}
      [document-view/document-form {:e! e!
                                    :on-close-event task-controller/->CloseAddDocumentDialog}
       (:new-document task)]])
   [itemlist/ItemList
    {:title (tr [:enum (:db/ident type)])}
    [itemlist/Item {:label (tr [:fields :task/type])} (tr [:enum (:db/ident type)])]
    [itemlist/Item {:label (tr [:fields :task/assignee])} [user-info/user-name-and-email e! (:user/id assignee)]]
    [itemlist/Item {:label (tr [:fields :common "description"])} description]]

   [itemlist/ItemList
    {:title (tr [:task :documents])}
    (if (empty? documents)
      [:div (tr [:task :no-documents])]
      [List {:dense true}
       (doall
        (for [{id :db/id
               :document/keys [name description]
               :as doc} documents]
          ^{:key id}
          [ListItem {:button true :component "a"
                     :href (task-controller/document-page-url app doc)}
           [ListItemIcon [icons/file-folder] ]
           [ListItemText {:primary (or name description)}]]))])]
   [Button {:on-click #(e! (task-controller/->OpenAddDocumentDialog))}
    [icons/content-add-circle]
    (tr [:task :add-document])]])
