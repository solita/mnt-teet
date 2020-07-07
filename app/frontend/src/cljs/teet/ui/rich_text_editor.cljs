(ns teet.ui.rich-text-editor
  (:require [reagent.core :as r]
            [herb.core :refer [<class]]
            draft-js
            draft-js-export-markdown
            draft-js-import-markdown
            ["react" :as react]
            [alandipert.storage-atom :refer [local-storage]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.util :as util]))

(def Editor (r/adapt-react-class (aget draft-js "Editor")))
(def EditorState (aget draft-js "EditorState"))
(def RichUtils (aget draft-js "RichUtils"))
(def convertToRaw (aget draft-js "convertToRaw"))
(def convertFromRaw (aget draft-js "convertFromRaw"))
(def CompositeDecorator (aget draft-js "CompositeDecorator"))
(def DefaultDraftBlockRenderMap (aget draft-js "DefaultDraftBlockRenderMap"))
(def toggleInlineStyle (aget RichUtils "toggleInlineStyle"))
(def toggleBlockType (aget RichUtils "toggleBlockType"))
(def Entity (aget RichUtils "Entity"))
(def stateToMarkdown (aget draft-js-export-markdown "stateToMarkdown"))
(def stateFromMarkdown (aget draft-js-import-markdown "stateFromMarkdown"))

(defonce wysiwyg-data
         (local-storage (atom nil) "editor-state"))

(def character-limit 4000)

(defn editor-style
  [error]
  {:padding "1rem"
   :border (str "1px solid " (if error
                               theme-colors/error
                               theme-colors/gray-lighter))})


(def block-types
  [{:label "H1"
    :style "header-one"}
   {:label "H2"
    :style "header-two"}])

(def inline-styles
  [{:label "Bold"
    :style "BOLD"}
   {:label "Italic"
    :style "ITALIC"}
   {:label "Underline"
    :style "UNDERLINE"}])

(defn type-control-button
  [{:keys [label style]} click active]
  [:button {:on-click (fn [e]
                        (.stopPropagation e)
                        (click style))
            :style {:background-color (if active
                                        "green"
                                        "blue")}}
   label])

(defn block-style-controls
  [editorState on-toggle]
  (let [selection (.getSelection editorState)
        block-type (-> editorState
                       (.getCurrentContent)
                       (.getBlockForKey (.getStartKey selection))
                       (.getType))]
    [:div
     (util/mapc
       (fn [type]
         [type-control-button type on-toggle (= (:style type) block-type)])
       block-types)]))

(defn inline-style-controls
  [editor-state on-toggle]
  (let [current-style (.getCurrentInlineStyle editor-state)]
    [:div
     (util/mapc
       (fn [type]
         [type-control-button type on-toggle (.has current-style (:style type))])
       inline-styles)]))

(defn link-comp
  [{:keys [children contentState entityKey]} props]
  (let [url (aget (.getData (.getEntity contentState entityKey)) "url")]
    [:a {:href url
         :target "_blank"}
     children]))

(defn findLinkEntities
  [contentBlock callback contentState]
  (.findEntityRanges contentBlock
                     (fn [char]
                       (let [entityKey (.getEntity char)]
                         (and (not= entityKey nil)
                              (=
                                (.getType (.getEntity contentState entityKey))
                                "LINK"))))
                     callback))

(defn wysiwyg-editor
  []
  (let [decorator (new CompositeDecorator #js [#js {:strategy findLinkEntities
                                                    :component (r/reactify-component link-comp)}])
        [editorState setEditorState] (react/useState (.createEmpty EditorState decorator) #_(if (not-empty @wysiwyg-data)
                                                       #(.createWithContent EditorState (stateFromMarkdown @wysiwyg-data))
                                                       (.createEmpty EditorState decorator)))
        [linkValue setLinkValue] (react/useState "")

        handle-key-command (fn [command state]
                             (let [new-state (. RichUtils handleKeyCommand state command)]
                               (if new-state
                                 (do
                                   (setEditorState new-state)
                                   "handled")
                                 "not-handled")))

        editor-ref (atom nil)
        focus-editor #(.focus @editor-ref)
        inlineToggle (fn [style]
                       (setEditorState (toggleInlineStyle editorState style)))
        blockToggle (fn [type]
                      (setEditorState (toggleBlockType editorState type)))
        set-link (fn []
                   (let [contentState (.getCurrentContent editorState)
                         contentStateWithEntity (.createEntity contentState "LINK" "MUTABLE" #js {:url linkValue})
                         entityKey (.getLastCreatedEntityKey contentStateWithEntity)
                         newEditorState (.set EditorState editorState  #js {:currentContent contentStateWithEntity})]
                     (println "entity-key " entityKey)
                     (println "new-editorstate: " newEditorState)
                     (println "contentWithEntity: " contentStateWithEntity)

                     (setEditorState (.toggleLink RichUtils
                                                  newEditorState
                                                  (.getSelection newEditorState)
                                                  entityKey))))]
    [:<>
     [:div {:style {:background-color theme-colors/gray-light
                    :margin-bottom "2rem"}}
      [:span "result: "]
      [Editor {:editorState editorState
               :read-only true}]]

     [:input {:value linkValue
              :on-change #(setLinkValue (-> % .-target .-value))}]

     [:div {:on-click focus-editor
            :class (<class editor-style (< 4096 (count (stateToMarkdown (.getCurrentContent editorState))))) }

      [block-style-controls editorState blockToggle]
      [inline-style-controls editorState inlineToggle]
      [:button {:on-click (fn [e]
                            (.stopPropagation e)
                            (set-link))}
       "set link"]

      [Editor {:ref #(reset! editor-ref %)
               :editorState editorState
               :handleKeyCommand handle-key-command
               :blockRenderMap DefaultDraftBlockRenderMap
               :placeholder "Rich text editor"
               :on-change (fn [editorState]
                            (println (count (stateToMarkdown (.getCurrentContent editorState))))
                            (reset! wysiwyg-data (stateToMarkdown (.getCurrentContent editorState)))
                            (setEditorState editorState))}]]]))
