(ns teet.document.document-view
  "Views for document and files"
  (:require [reagent.core :as r]

            [teet.ui.form :as form]
            [teet.ui.select :as select]
            [teet.document.document-controller :as document-controller]
            [teet.ui.material-ui :refer [TextField]]
            [teet.ui.file-upload :as file-upload]
            [taoensso.timbre :as log]))


(defn document-form [e! doc]
  (log/info "RENDER DOC " doc)
  [form/form {:e! e!
              :value doc
              :on-change-event document-controller/->UpdateDocumentForm
              :save-event document-controller/->CreateDocument
              :cancel-event document-controller/->CancelDocument}
   ^{:attribute :document/status}
   [select/select-enum {:e! e! :attribute :document/status}]

   ^{:attribute :document/description}
   [TextField {:multiline true :maxrows 4 :rows 4
               :variant "outlined" :full-width true}]

   ^{:attribute :document/files}
   [file-upload/files-field {}]
   ])
