(ns teet.document.document-view
  "Views for document and files"
  (:require [reagent.core :as r]
            [teet.theme.theme-panels :as theme-panels]
            [teet.ui.form :as form]
            [teet.ui.select :as select]
            [teet.document.document-controller :as document-controller]
            [teet.ui.material-ui :refer [TextField LinearProgress Grid Button]]
            [teet.ui.typography :as typography]
            [teet.ui.file-upload :as file-upload]
            [taoensso.timbre :as log]
            [herb.core :refer [<class]]
            [teet.localization :refer [tr]]
            teet.document.document-spec))


(defn document-form [e! {:keys [in-progress? progress] :as doc}]
  [:<>
   [form/form {:e! e!
               :value doc
               :on-change-event document-controller/->UpdateDocumentForm
               :save-event document-controller/->CreateDocument
               :cancel-event document-controller/->CancelDocument
               :in-progress? in-progress?
               :spec :document/new-document-form}
    ^{:attribute :document/status}
    [select/select-enum {:e! e! :attribute :document/status}]

    ^{:attribute :document/description}
    [TextField {:multiline true :maxrows 4 :rows 4
                :variant "outlined" :full-width true
                :required true}]

    ^{:attribute :document/files}
    [file-upload/files-field {}]]

   (when in-progress?
     [LinearProgress {:variant "determinate"
                      :value in-progress?}])])

(defn document-page [e! {:keys [document]}]
  (r/with-let [new-comment (r/atom "")]
    [Grid {:container true}
     [Grid {:item true :xs 6}
      [:div "DOC: " (pr-str document)]]
     [Grid {:item true :xs 6 :classes {:item (<class theme-panels/side-panel)}}
      [typography/SectionHeading (tr [:document :comments])]
      [TextField {:value @new-comment
                  :on-change #(reset! new-comment (-> % .-target .-value))
                  :rows 4 :maxrows 4 :multiline true :full-width true
                  :placeholder (tr [:document :new-comment])}]
      [Button {:on-click #(do
                            (reset! new-comment "")
                            (e! (document-controller/->Comment @new-comment)))}
       (tr [:buttons :save])]]]))

(defn document-page-and-title [e! app]
  (let [doc-id (get-in app [:params :document])
        doc (get-in app [:document doc-id])]
    ;; document should have title?
    {:title (get-in app [:document doc-id :document/description])
     :page [document-page e! {:document doc}]}))
