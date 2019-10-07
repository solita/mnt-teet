(ns teet.document.document-view
  "Views for document and files"
  (:require [reagent.core :as r]
            [teet.theme.theme-panels :as theme-panels]
            [teet.ui.form :as form]
            [teet.ui.select :as select]
            [teet.document.document-controller :as document-controller]
            [teet.ui.material-ui :refer [TextField LinearProgress Grid
                                         List ListItem ListItemText ListItemIcon
                                         CircularProgress Divider]]
            [teet.ui.typography :as typography]
            [teet.ui.file-upload :as file-upload]
            [herb.core :refer [<class]]
            [teet.localization :refer [tr]]
            teet.document.document-spec
            [teet.ui.itemlist :as itemlist]
            [teet.user.user-info :as user-info]
            [teet.ui.format :as format]
            [teet.ui.icons :as icons]
            [teet.project.project-info :as project-info]))


(defn document-form [{:keys [e! on-close-event]} {:keys [in-progress?] :as doc}]
  [:<>
   [form/form {:e! e!
               :value doc
               :on-change-event document-controller/->UpdateDocumentForm
               :save-event document-controller/->CreateDocument
               :cancel-event on-close-event
               :in-progress? in-progress?
               :spec :document/new-document-form}

    ^{:attribute :document/name}
    [TextField {:variant "outlined"
                :full-width true}]

    ^{:attribute :document/description}
    [TextField {:multiline true :maxrows 4 :rows 4
                :variant "outlined" :full-width true
                :required true}]

    ^{:attribute :document/files}
    [file-upload/files-field {}]

    ^{:attribute :document/status}
    [select/select-enum {:e! e! :attribute :document/status}]]

   (when in-progress?
     [LinearProgress {:variant "determinate"
                      :value in-progress?}])])

(defn comments [e! document]
  [:<>
   [itemlist/ItemList {:title (tr [:document :comments])}
    (doall
     (for [{id :db/id
            :comment/keys [author comment timestamp]} (:document/comments document)]
       ^{:key id}
       [:div
        [typography/SectionHeading
         [user-info/user-name e! (:user/id author)]]
        [:div (.toLocaleString timestamp)]
        [typography/Paragraph comment]]))]

   [Divider {:variant "middle"}]

   [form/form {:e! e!
               :value (:new-comment document)
               :on-change-event document-controller/->UpdateNewCommentForm
               :save-event document-controller/->Comment
               :spec :document/new-comment-form}
    ^{:attribute :comment/comment}
    [TextField {:rows 4 :maxrows 4 :multiline true :full-width true
                :placeholder (tr [:document :new-comment])
                :variant "outlined"}]]])

(defn document-page [e! {:keys [document] :as _app}]
  [Grid {:container true}
   [Grid {:item true :xs 6}
    [typography/SectionHeading (:document/name document)
     [typography/DataLabel " " (count (:document/files document)) " " (tr [:common :files])]]
    [typography/Paragraph (:document/description document)]
    [List {:dense true}
     (doall
      (for [{id :db/id
             :file/keys [name size author timestamp]
             in-progress? :in-progress?} (:document/files document)]
        ^{:key id}
        [ListItem {:button true :component "a"
                   :href (document-controller/download-url id)
                   :target "_blank"}
         (if in-progress?
           [ListItemIcon [CircularProgress {:size 20}]]
           [ListItemIcon [icons/file-attachment]])
         [ListItemText {:primary name
                        :secondary (r/as-element
                                    [:<>
                                     [:div (some-> timestamp format/date-time) " "
                                      (when author
                                        [user-info/user-name e! author])]
                                     (format/file-size size)])}]]))]
    [file-upload/FileUploadButton {:id "upload-files-to-document"
                                   :on-drop (e! document-controller/->UploadFilesToDocument)}
     (tr [:common :select-files])]]
   [Grid {:item true :xs 6 :classes {:item (<class theme-panels/side-panel)}}
    [comments e! document]]])

(defn document-page-and-title [e! {params :params :as app}]
  (let [doc-id (get-in app [:params :document])
        doc (get-in app [:document doc-id])]
    ;; document should have title?
    {:title (get-in app [:document doc-id :document/name])
     :breadcrumbs [{:page :projects
                    :title (tr [:projects :title])}
                   {:page :project
                    :params {:project (:project params)}
                    :title [project-info/project-name app (:project params)]}
                   {:page :phase-task
                    :params {:project (:project params)
                             :phase (:phase params)
                             :task (:task params)}
                    :title (get-in doc [:task/_documents 0 :task/type :db/ident])}
                   {:title (:document/name doc)}]
     :page [document-page e! (merge (select-keys app [:params :config :user])
                                    {:document doc})]}))
