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
            [teet.ui.layout :as layout]
            [teet.project.project-style :as project-style]
            [teet.ui.breadcrumbs :as breadcrumbs]))

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

(defn comments [e! new-comment document]
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
               :value new-comment
               :on-change-event document-controller/->UpdateNewCommentForm
               :save-event document-controller/->Comment
               :spec :document/new-comment-form}
    ^{:attribute :comment/comment}
    [TextField {:rows 4 :multiline true :full-width true
                :placeholder (tr [:document :new-comment])
                :variant "outlined"}]]])

(defn document-page [e! {new-comment :new-comment} document breadcrumbs]
  [Grid {:container true}
   [Grid {:item true :xs 6}
    [:div {:class (<class project-style/gray-bg-content)}
     [breadcrumbs/breadcrumbs breadcrumbs]
     [:div {:class (<class project-style/project-info)}
      [typography/Heading1 (:document/name document)]
      [typography/Paragraph (:document/description document)]]]
    [:div {:style {:display :flex
                   :align-items :center}}
     [:div {:style {:width "50%"
                    :margin "1rem 1rem 1rem 0"}}
      [select/select-enum {:e! e! :attribute :document/status
                           :on-change (e! document-controller/->UpdateDocumentStatus)
                           :value (get-in document [:document/status :db/ident])}]]
     (when-let [modified (:document/modified document)]
       [:span (format/date-time modified)])]
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
                                       [:span {:style {:display :block}} (some-> timestamp format/date-time) " "
                                        (when author
                                          [user-info/user-name e! author])]
                                       (format/file-size size)])}]]))]
    [file-upload/FileUploadButton {:id "upload-files-to-document"
                                   :on-drop (e! document-controller/->UploadFilesToDocument)}
     (tr [:common :select-files])]]
   [Grid {:item true :xs 6 :classes {:item (<class theme-panels/side-panel)}}
    [comments e! new-comment document]]])
