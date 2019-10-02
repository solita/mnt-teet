(ns teet.document.document-view-test
  (:require [teet.document.document-view :as document-view]
            [teet.drtest :as drt :include-macros true]
            [tuck.core :as t]
            [teet.document.document-controller :as document-controller]))

(defn test-view [e! app]
  [document-view/comments e! (get-in app [:document "666"])])

(drt/define-drtest comment-form-test
  {:initial-context {:app (drt/atom {:params {:document "666"}
                                     :document {"666" #:document {:comments []}}})}}

  drt/init-step

  (drt/step :tuck-render "Render comments view"
            :component test-view)

  (drt/step :expect "Expect empty comment form"
            :selector "input[placeholder=\"Uus märkus\"]"
            :as :input)

  (drt/step :expect "Save button is found"
            :selector "button"
            :as "save-button")

  (drt/step :click "Click save button before entering text"
            :selector "button")

  (drt/step :no-request "No request should happen")

  (drt/step :type "Type comment text"
            :selector "input[placeholder=\"Uus märkus\"]"
            :text "this is a comment")

  (drt/step :expect-tuck-event "Expect update comment event"
            :predicate #(instance? document-controller/UpdateNewCommentForm %)
            :apply? true)

  (drt/step :click "Click the button"
            :selector "button")

  (drt/step :expect-tuck-event "Expect save comment event"
            :predicate #(= % (document-controller/->Comment))
            :apply? true
            :as "save-event")

  (drt/step :wait-command "Wait for save command"
            :command :document/comment
            :predicate #(= "this is a comment" (get-in % [:payload :comment]))
            :as :req))
