(ns teet.ui.rich-text-editor
  (:require [reagent.core :as r]
            draft-js
            ["react" :as react]
            [alandipert.storage-atom :refer [local-storage]]
            [teet.theme.theme-colors :as theme-colors]))

(def Editor (r/adapt-react-class (aget draft-js "Editor")))
(def EditorState (aget draft-js "EditorState"))
(def RichUtils (aget draft-js "RichUtils"))
(def convertToRaw (aget draft-js "convertToRaw"))
(def convertFromRaw (aget draft-js "convertFromRaw"))
(def DefaultDraftBlockRenderMap (aget draft-js "DefaultDraftBlockRenderMap"))
(def toggleInlineStyle (aget RichUtils "toggleInlineStyle"))

(defonce wysiwyg-data
         (local-storage (atom nil) "editor-state"))

(defn wysiwyg-editor
  []
  (let [[editorState setEditorState] (react/useState (if (not-empty @wysiwyg-data)
                                                       #(.createWithContent EditorState (convertFromRaw (js/JSON.parse @wysiwyg-data)))
                                                       #(.createEmpty EditorState)))

        handle-key-command (fn [command state]
                             (let [new-state (. RichUtils handleKeyCommand state command)]
                               (if new-state
                                 (do
                                   (setEditorState new-state)
                                   "handled")
                                 "not-handled")))

        editor-ref (atom nil)
        focus-editor #(.focus @editor-ref)
        block-style-fn (fn [content-block]
                         (let [type (.getType content-block)]
                           #_(.log js/console type)))]

    [:<>
     [:div {:style {:background-color theme-colors/gray-light
                    :margin-bottom "2rem"}}
      [:span "result: "]
      [Editor {:editorState editorState
               :read-only true}]]

     [:div {:on-click focus-editor
            :style {:padding "1rem"
                    :border (str "1px solid " theme-colors/gray-lighter)}}
      [:button {:on-click (fn [e]
                            (let [new-editor-state (toggleInlineStyle
                                                     editorState "BOLD")]
                              (.stopPropagation e)
                              (.log js/console (convertToRaw
                                                 (.getCurrentContent
                                                   new-editor-state)))
                              (setEditorState new-editor-state)))}
       "bold"]
      [Editor {:ref #(reset! editor-ref %)
               :editorState editorState
               :handleKeyCommand handle-key-command
               :blockStyleFn block-style-fn
               :blockRenderMap DefaultDraftBlockRenderMap
               :placeholder "Rich text editor"
               :on-change (fn [editorState]
                            (reset! wysiwyg-data (js/JSON.stringify (convertToRaw (.getCurrentContent editorState))))
                            (setEditorState editorState))}]]]))
