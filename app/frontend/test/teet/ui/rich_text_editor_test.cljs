(ns teet.ui.rich-text-editor-test
  (:require [teet.drtest :refer [step] :as drt :include-macros true]
            [tuck.core :as tuck]
            [teet.ui.rich-text-editor :as rte]
            [reagent.core :as r]
            [clojure.string :as str]
            [cljs.test :as t]))

(defrecord SetEditorState [new-editor-state]
  tuck/Event
  (process-event [{s :new-editor-state} app]
    (assoc app :editor-state s)))

(defn rich-text-view [e! app]
  [:f> rte/wysiwyg-editor {:id "rte-test"
                           :value (:editor-state app)
                           :on-change (e! ->SetEditorState)}])

(drt/define-drtest basic-typing-test
  {:initial-context {:app (r/atom {:editor-state nil})}}

  (step :tuck-render "Render rich text view"
        :component rich-text-view)

  (step :draftjs-type "Type greeting"
        :id "rte-test"
        :text "Hello Rich Text World!\n")

  (fn [{app :app}]
    (let [md (rte/editor-state->markdown (:editor-state @app))]
      (str/includes? md "Hello Rich Text World!"))))

(drt/define-drtest markdown-roundtrip-test
  {:initial-context {:app (r/atom {:editor-state (rte/markdown->editor-state "Hello **cruel** world!")})}}

  (step :tuck-render "Render rich text view with initial markdown"
        :component rich-text-view)

  (step :expect "Expect it to be rendered"
        :selector "#rte-test .DraftEditor-editorContainer"
        :text "Hello cruel world!")

  ^{:drtest.step/label "Exported markdown has the same format as initial"}
  (fn [{app :app}]
    (let [md (rte/editor-state->markdown (:editor-state @app))]
      (str/includes? md "Hello **cruel** world!"))))
