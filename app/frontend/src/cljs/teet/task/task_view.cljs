(ns teet.task.task-view
  "View for a workflow task"
  (:require [herb.core :as herb :refer [<class]]
            [teet.task.task-controller :as task-controller]
            [teet.ui.itemlist :as itemlist]
            [teet.localization :refer [tr-fixme tr]]
            [teet.ui.material-ui :refer [TextField List ListItem ListItemText
                                         ListItemIcon Button Grid]]
            [teet.ui.icons :as icons]
            [teet.ui.typography :refer [Heading1 Heading2 Heading3 SectionHeading DataLabel]]
            [teet.ui.layout :as layout]
            [teet.project.project-style :as project-style]
            [teet.ui.select :as select]
            [teet.user.user-info :as user-info]
            [teet.project.project-view :as project-view]
            [teet.ui.panels :as panels]
            [teet.document.document-view :as document-view]
            [teet.ui.format :as format]
            [teet.ui.breadcrumbs :as breadcrumbs]
            [teet.common.common-styles :as common-styles]))

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

(defn task-page [e! {{:keys [project]} :params
                     query :query
                     new-document :new-document :as app}
                 {:task/keys [documents description type assignee status modified] :as _task}
                 breadcrumbs]
  [:<>
   (when (:add-document query)
     [panels/modal {:title (tr [:task :add-document])
                    :on-close (e! task-controller/->CloseAddDocumentDialog)}
      [document-view/document-form {:e! e!
                                    :on-close-event task-controller/->CloseAddDocumentDialog}
       new-document]])
   [Grid {:container true}
    [Grid {:item true :xs 6}
     [:div {:class (<class common-styles/gray-bg-content)}
      [breadcrumbs/breadcrumbs breadcrumbs]
      [:div {:class (<class project-style/project-info)}
       [Heading1
        (tr [:enum (:db/ident type)])]
       [itemlist/ItemList
        {}
        [itemlist/Item {:label (tr [:fields :task/type])} (tr [:enum (:db/ident type)])]
        [itemlist/Item {:label (tr [:fields :task/assignee])} [user-info/user-name-and-email e! (:user/id assignee)]]
        [itemlist/Item {:label (tr [:fields :common "description"])} description]]]]
     [layout/section
      [:div {:style {:display :flex
                     :align-items :center}}
       [:div {:style {:width "50%"
                      :margin "1rem 1rem 1rem 0"}}
        [select/select-enum {:e! e!
                             :on-change (e! task-controller/->UpdateTaskStatus)
                             :value (:db/ident status)
                             :attribute :task/status}]]
       (when modified
         [:span (format/date-time modified)])]

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
               [ListItemIcon [icons/file-folder]]
               [ListItemText {:primary (or name description)}]]))])]
      [Button {:on-click #(e! (task-controller/->OpenAddDocumentDialog))}
       [icons/content-add-circle]
       (tr [:task :add-document])]]]
    [Grid {:item true :xs 6}
     [project-view/project-map e! (get-in app [:config :api-url]) project (get-in app [:query :tab])]]]])
