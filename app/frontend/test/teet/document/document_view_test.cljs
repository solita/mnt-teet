(ns teet.document.document-view-test
  (:require [teet.document.document-view :as document-view]
            [teet.drtest :as drt :include-macros true]
            [tuck.core :as t]
            [teet.comments.comments-controller :as comments-controller]
            [teet.comments.comments-view :as comments-view]))

(defn test-view [e! app]
  (let [document (get-in app [:route :activity-task :task/documents 0])]
    [comments-view/comments {:e!                   e!
                             :new-comment          (:new-comment document)
                             :comments             (:document/comments document)
                             :update-comment-event comments-controller/->UpdateNewCommentForm
                             :save-comment-event   comments-controller/->CommentOnDocument}]))

(drt/define-drtest comment-form-test
                   {:initial-context {:app (drt/atom {:query {:document "666"}
                                                      :route {:activity-task {:task/documents [{:db/id "666"}]}}})}}
  drt/init-step

  (drt/step :tuck-render "Render comments view"
            :component test-view)

  (drt/step :expect "Expect empty comment form"
            :selector "textarea[placeholder=\"Uus märkus\"]"
            :as :input)

  (drt/step :expect "Save button is found"
            :selector "button"
            :as "save-button")

  (drt/step :click "Click save button before entering text"
            :selector "button")

  (drt/step :no-request "No request should happen")

  (drt/step :type "Type comment text"
            :selector "textarea[placeholder=\"Uus märkus\"]"
            :text "this is a comment")

  (drt/step :expect-tuck-event "Expect update comment event"
            :predicate #(instance? comments-controller/UpdateNewCommentForm %)
            :apply? true)

  (drt/step :click "Click the button"
            :selector "button")

  (drt/step :expect-tuck-event "Expect save comment event"
            :predicate #(instance? comments-controller/CommentOnDocument %)
            :apply? true
            :as "save-event")

  (drt/step :wait-command "Wait for save command"
            :command :comment/comment-on-document
            :predicate #(= "this is a comment" (get-in % [:payload :comment]))
            :as :req))
