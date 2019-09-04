(ns teet.task.task-view
  "View for a workflow task"
  (:require [teet.task.task-controller :as task-controller]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.file-upload :as file-upload]
            [teet.localization :refer [tr-fixme]]))

(defn task-page [e! {:task/keys [name status documents] :as task}]
  [:<>
   [itemlist/ItemList
    {:title "Task"}
    [:div "Name: " name]
    [:div "Status: " (:db/ident status)]
    [:div "TASKI " (pr-str task)]]

   [itemlist/ItemList
    {:title "Documents"}
    (if (empty? documents)
      [:div "No documents"]
      (doall
       (for [{id :db/id :document/keys [name size type] :as doc} documents]
         ^{:key id}
         [:div [:a {:href "download URL"} name] "(type: " type ", size: " size])))
    [file-upload/FileUploadButton {:id "upload-document-to-task"
                                   :on-drop #(e! (task-controller/->UploadDocuments %))
                                   :drop-message "Drop to upload document to task"}
     (tr-fixme "Click to upload document")]]])

(defn task-page-and-title [e! {params :params :as app}]
  (let [id (params :task)
        task (get-in app [:task id])]
    {:title (:task/name task)
     :page [task-page e! task]}))
