(ns teet.task.task-view
  "View for a workflow task"
  (:require [teet.task.task-controller :as task-controller]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.file-upload :as file-upload]
            [teet.localization :refer [tr-fixme]]
            [teet.ui.material-ui :refer [CircularProgress IconButton]]
            [reagent.core :as r]
            [teet.ui.icons :as icons]
            [teet.ui.select :as select]))

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

(defn task-page [e! _]
  (let [modify-field (r/atom nil)]
    (fn [e! {:task/keys [name status documents] :as task}]
      (let [current-modify-field @modify-field]
        [:<>
         [itemlist/ItemList
          {:title "Task"}
          [:div "Name: " name]
          [:div "Status: "

           [:div {:style {:display "inline-block"}}
            (-> status :task.status/status :db/ident)
            (when-not (= :status current-modify-field)
              [IconButton {:on-click #(reset! modify-field :status)}
               [icons/image-edit]])]

           (when (= :status current-modify-field)
             [change-task-status e! task #(reset! modify-field nil)])]

          [:div {:style {:font-size "75%"}}  "TASKI " (pr-str task)]]

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
                [:div [:a {:href (task-controller/download-document-url doc)} name]
                 " (type: " type ", size: " size ") "
                 (when progress
                   [CircularProgress])
                 ]])))]
         [file-upload/FileUploadButton {:id "upload-document-to-task"
                                        :on-drop #(e! (task-controller/->UploadDocuments %))
                                        :drop-message "Drop to upload document to task"}
          (tr-fixme "Click to upload document")]]))))

(defn task-page-and-title [e! {params :params :as app}]
  (let [id (params :task)
        task (get-in app [:task id])]
    {:title (:task/name task)
     :page [task-page e! task]}))
