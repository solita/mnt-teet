(ns teet.task.task-view
  "View for a workflow task"
  (:require [teet.task.task-controller :as task-controller]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.file-upload :as file-upload]
            [teet.localization :refer [tr-fixme tr]]
            [teet.ui.material-ui :refer [CircularProgress IconButton TextField Grid
                                         Button]]
            [reagent.core :as r]
            [teet.ui.icons :as icons]
            [teet.ui.select :as select]
            [taoensso.timbre :as log]
            [teet.user.user-info :as user-info]
            [teet.ui.form :as form]
            [teet.ui.panels :as panels]
            [teet.document.document-view :as document-view]))

(defn task-form [e! close phase-id task]
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
   [TextField {:full-width true :multiline true :rows 4 :maxrows 4}]

   ^{:attribute :task/assignee}
   [select/select-user {:e! e!}]
   ])

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

(defn comments-view [e! task-id comments]
  (r/with-let [new-comment-text (r/atom "")]
    [:<>
     [itemlist/ItemList
      {:title "Comments"}
      (doall
       (for [{id :db/id :comment/keys [comment timestamp author]} (sort-by :comment/timestamp comments)]
         ^{:key id}
         [:div
          (.toLocaleDateString timestamp) " " (.toLocaleTimeString timestamp) " "
          [:em comment]
          [:span " -- " [user-info/user-name e! (:user/id author)]]
          [:br]]))]

     ;;
     [TextField {:placeholder "New comment..." ;;:multiline true :rowsMax 5
                 :value @new-comment-text
                 :on-change #(reset! new-comment-text (-> % .-target .-value))
                 :on-key-press #(when (= "Enter" (.-key %))
                                  (e! (task-controller/->AddCommentToTask task-id @new-comment-text))
                                  (reset! new-comment-text ""))}]


     ]))

(defn task-page [e! {{:task/keys [documents description type assignee] :as task} :task
                     query :query}]
  [:<>
   (when (:add-document query)
     [panels/modal {:title (tr [:task :new-document])
                    :on-close (r/partial e! (task-controller/->CloseAddDocumentDialog))}
      [document-view/document-form e! (:new-document task)]])
   [itemlist/ItemList
    {:title (tr [:enum (:db/ident type)])}
    [itemlist/Item {:label (tr [:fields :task/type])} (tr [:enum (:db/ident type)])]
    [itemlist/Item {:label (tr [:fields :task/assignee])} [user-info/user-name-and-email e! (:user/id assignee)]]
    [itemlist/Item {:label (tr [:fields :common "description"])} description]]

   [itemlist/ItemList
    {:title "Documents"}
    (if (empty? documents)
      [:div "No documents"]
      (doall
       (for [{id :db/id
              :document/keys [name size type]
              progress :progress
              :as doc} documents]
         ^{:key id}
         [:div
          ;; FIXME: make a nice document UI
          [:br]
          [:div [:a {:href (task-controller/download-document-url doc)
                     :target "_blank"} name]
           " (type: " type ", size: " size ") "
           (when progress
             [CircularProgress])
           ]])))]
   [Button {:on-click #(e! (task-controller/->OpenAddDocumentDialog))}
    [icons/content-add-circle]
    (tr [:task :add-document])]
   #_[file-upload/FileUploadButton {:id "upload-document-to-task"
                                  :on-drop #(e! (task-controller/->UploadDocuments %))
                                  :drop-message "Drop to upload document to task"}
    (tr-fixme "Click to upload document")]])

(defn task-page-and-title [e! {params :params :as app}]
  (let [{:keys [task phase]} params]
    {:title task
     :page [task-page e! {:query (:query app)
                          :task (get-in app [:task task])}]}))
