(ns teet.document.document-view
  "Views for document and files"
  (:require [herb.core :refer [<class]]
            [reagent.core :as r]
            [teet.common.common-styles :as common-styles]
            [teet.document.document-controller :as document-controller]
            teet.document.document-spec
            [teet.localization :refer [tr]]
            [teet.project.project-style :as project-style]
            [teet.theme.theme-panels :as theme-panels]
            [teet.ui.breadcrumbs :as breadcrumbs]
            [teet.ui.common :as ui-common]
            [teet.ui.file-upload :as file-upload]
            [teet.ui.form :as form]
            [teet.ui.format :as format]
            [teet.ui.icons :as icons]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.layout :as layout]
            [teet.ui.material-ui :refer [LinearProgress Grid
                                         List ListItem ListItemText ListItemIcon
                                         CircularProgress Divider]]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.select :as select]
            [teet.ui.typography :as typography]
            [teet.user.user-info :as user-info]
            [teet.comments.comments-view :as comments-view]))

(defn document-form [{:keys [e! on-close-event]} {:keys [in-progress?] :as doc}]
  [:<>
   [form/form {:e! e!
               :value doc
               :on-change-event document-controller/->UpdateDocumentForm
               :save-event document-controller/->CreateDocument
               :cancel-event on-close-event
               :in-progress? in-progress?
               :spec :document/new-document-form}

    ^{:attribute :document/category :xs 6}
    [select/select-enum {:e! e! :attribute :document/category}]

    (when-let [category (:document/category doc)]
      ^{:attribute :document/sub-category :xs 6}
      [select/select-enum {:e! e! :attribute :document/sub-category :enum/valid-for category}])

    ^{:attribute :document/name}
    [TextField {:full-width true}]

    ^{:attribute :document/description}
    [TextField {:multiline true :maxrows 4 :rows 4
                :full-width true
                :required true}]

    ^{:attribute :document/files}
    [file-upload/files-field {}]

    ^{:attribute :document/status}
    [select/select-enum {:e! e! :attribute :document/status}]

    ^{:attribute :document/author}
    [select/select-user {:e! e!}]]

   (when in-progress?
     [LinearProgress {:variant "determinate"
                      :value in-progress?}])])

(defn comments [e! {:keys [new-comment] :as document}]
  [comments-view/comments {:e! e!
                           :new-comment new-comment
                           :comments (:document/comments document)
                           :update-comment-event document-controller/->UpdateNewCommentForm
                           :save-comment-event document-controller/->Comment}])

(defn status
  [e! document]
  [select/status {:e! e!
                     :on-change (e! document-controller/->UpdateDocumentStatus)
                     :status (get-in document [:document/status :db/ident])
                     :attribute :document/status
                     :modified (:document/modified document)}])

