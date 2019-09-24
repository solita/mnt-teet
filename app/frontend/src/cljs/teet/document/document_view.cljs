(ns teet.document.document-view
  "Views for document and files"
  (:require [reagent.core :as r]
            [teet.ui.form :as form]
            [teet.ui.select :as select]
            [teet.document.document-controller :as document-controller]
            [teet.ui.material-ui :refer [TextField LinearProgress]]
            [teet.ui.file-upload :as file-upload]
            [taoensso.timbre :as log]
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
  [:div "DOC: " (pr-str document)])

(defn document-page-and-title [e! app]
  (let [doc-id (get-in app [:params :document])
        doc (get-in app [:document doc-id])]
    ;; document should have title?
    {:title (get-in app [:document doc-id :document/description])
     :page [document-page e! {:document doc}]}))
