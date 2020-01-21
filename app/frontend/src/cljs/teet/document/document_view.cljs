(ns teet.document.document-view
  "Views for document and files"
  (:require [herb.core :refer [<class]]
            [teet.document.document-controller :as document-controller]
            teet.document.document-spec
            [teet.ui.file-upload :as file-upload]
            [teet.ui.form :as form]
            [teet.ui.material-ui :refer [LinearProgress]]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.select :as select]
            [teet.comments.comments-view :as comments-view]))

(defn document-form [_ {:keys [initialization-fn]} _]
  (when initialization-fn
    (initialization-fn))
  (fn [e! {:keys [on-close-event delete on-change save editing?]} {:keys [in-progress?] :as doc}]
    [:<>
     [form/form {:e!              e!
                 :value           doc
                 :delete          delete
                 :on-change-event on-change
                 :save-event      save
                 :cancel-event    on-close-event
                 :in-progress?    in-progress?
                 :spec            :document/new-document-form}

      ^{:attribute :document/category :xs 6}
      [select/select-enum {:e! e! :attribute :document/category}]

      (when-let [category (:document/category doc)]
        ^{:attribute :document/sub-category :xs 6}
        [select/select-enum {:e! e! :attribute :document/sub-category :enum/valid-for category}])

      ^{:attribute :document/name}
      [TextField {:full-width true}]

      ^{:attribute :document/description}
      [TextField {:multiline  true :maxrows 4 :rows 4
                  :full-width true}]

      (when-not editing?
        ^{:attribute :document/files}
        [file-upload/files-field {}])

      ^{:attribute :document/status}
      [select/select-enum {:e! e! :attribute :document/status}]

      ^{:attribute :document/author}
      [select/select-user {:e! e!}]]

     (when in-progress?
       [LinearProgress {:variant "determinate"
                        :value   in-progress?}])]))

(defn comments [e! {:keys [new-comment] :as document}]
  [comments-view/comments {:e! e!
                           :new-comment new-comment
                           :comments (:document/comments document)
                           :update-comment-event document-controller/->UpdateNewCommentForm
                           :save-comment-event document-controller/->Comment}])


