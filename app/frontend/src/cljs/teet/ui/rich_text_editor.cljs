(ns teet.ui.rich-text-editor
  (:require [reagent.core :as r]
            [herb.core :refer [<class]]
            ["draft-js" :as draft-js]
            ["draft-js-export-markdown" :as export-markdown]
            ["draft-js-import-markdown" :as import-markdown]
            ["react" :as react]
            [alandipert.storage-atom :refer [local-storage]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.util :as util]
            [teet.ui.buttons :as buttons]
            [teet.ui.material-ui :refer [Divider]])
  (:require-macros [teet.util.js :refer [js>]]))

(def ^:private Editor (r/adapt-react-class draft-js/Editor))

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

(defn type-control-button-style
  [active?]
  {:border :none
   :background-color :none
   :font-weight (if active?
                  :bold
                  :normal)})

(defn type-control-button
  [{:keys [label style]} click active]
  [buttons/button-text {:on-click (fn [e]
                        (.stopPropagation e)
                        (click style))
            :class (<class type-control-button-style active)}
   label])

(defn block-style-controls
  [editorState on-toggle]
  (js>
   (let [selection (.getSelection editorState)
         content (.getCurrentContent editorState)
         block (.getBlockForKey content
                                (.getStartKey selection))
         block-type (.getType block)]
     [:div
      (util/mapc
       (fn [type]
         [type-control-button type on-toggle (= (:style type) block-type)])
       block-types)])))

(defn inline-style-controls
  [editor-state on-toggle]
  (js>
   (let [current-style (.getCurrentInlineStyle editor-state)]
     [:div
      (util/mapc
       (fn [type]
         [type-control-button type on-toggle (.has current-style (:style type))])
       inline-styles)])))

(defn link-comp
  [{:keys [children contentState entityKey]} props]
  (js>
   (let [url (aget (.getData (.getEntity contentState entityKey)) "url")]
     [:a {:href url
          :target "_blank"}
      children])))

(defn findLinkEntities
  [contentBlock callback contentState]
  (js>
   (.findEntityRanges contentBlock
                      (fn [char]
                        (let [entityKey (.getEntity char)]
                          (and (not= entityKey nil)
                               (= (.getType (.getEntity contentState entityKey))
                                  "LINK"))))
                callback)))

(defn focus! [editor-id]
  (.focus (js/document.querySelector (str "#" editor-id " .DraftEditor-editorContainer div:nth-child(1)"))))

(defn editor-state->markdown [^draft-js/EditorState editor-state]
  (js>
   (export-markdown/stateToMarkdown (.getCurrentContent editor-state))))

(def ^:private decorator
  (draft-js/CompositeDecorator. #js [#js {:strategy findLinkEntities
                                          :component (r/reactify-component link-comp)}]))

(defn markdown->editor-state [markdown]
  (.createWithContent draft-js/EditorState (import-markdown/stateFromMarkdown markdown) decorator))


(defn wysiwyg-editor
  "Rich text editor component that keeps its own internal state.

  Options:

  :id         string id to give to the root editor container element (used for some commands)
  :value      current draftjs EditorState object (or nil for empty)
  :on-change  callback to update editor state"

  [{:keys [value on-change id]}]
  (js>
   (let [read-only? (nil? on-change)
         editorState (or value (.createEmpty draft-js/EditorState decorator))
         [linkValue setLinkValue] (react/useState "")

         handle-key-command (fn [command state]
                              (let [new-state (.handleKeyCommand draft-js/RichUtils state command)]
                                (if new-state
                                  (do
                                    (on-change new-state)
                                    "handled")
                                  "not-handled")))

         [editor-ref set-editor-ref!] (react/useState nil)
         focus-editor #(.focus editor-ref)
         inlineToggle (fn [style]
                        (on-change (.toggleInlineStyle draft-js/RichUtils editorState style)))
         blockToggle (fn [type]
                       (on-change (.toggleBlockType draft-js/RichUtils editorState type)))
         set-link (fn []
                    (let [contentState (.getCurrentContent editorState)
                          contentStateWithEntity (.createEntity contentState "LINK" "MUTABLE" #js {:url linkValue})
                          entityKey (.getLastCreatedEntityKey contentStateWithEntity)
                          newEditorState (.set draft-js/EditorState editorState  #js {:currentContent contentStateWithEntity})]
                      (println "entity-key " entityKey)
                      (println "new-editorstate: " newEditorState)
                      (println "contentWithEntity: " contentStateWithEntity)

                      (on-change (.toggleLink draft-js/RichUtils newEditorState
                                              (.getSelection newEditorState)
                                              entityKey))))]
     [:span (when id {:id id})
      (when-not read-only?
        [:span
         [:input {:value linkValue
                  :on-change #(setLinkValue (-> % .-target .-value))}]

         [:div {:on-click focus-editor
                :class (<class editor-style false) }

          [block-style-controls editorState blockToggle]
          [inline-style-controls editorState inlineToggle]
          [:button {:on-click (fn [e]
                                (.stopPropagation e)
                                (set-link))}
           "set link"]
          [Divider {:style {:margin "1rem"}}]]])

      [Editor {:ref set-editor-ref!
               :readOnly (nil? on-change)
               :editorState editorState
               :handleKeyCommand handle-key-command
               :blockRenderMap draft-js/DefaultDraftBlockRenderMap
               :placeholder "Rich text editor"
               :on-change (fn [editorState]
                            (on-change editorState))}]])))

(defn display-markdown
  "Display a markdown that does not change during the component lifecycle.
  Parses and creates an editorstate once at mount."
  [markdown]
  (let [editor-state (when markdown
                       (markdown->editor-state markdown))]
    (fn [_]
      (if editor-state
        [:f> wysiwyg-editor {:value editor-state}]
        [:span]))))

(defn rich-text-field
  "Rich text input that can be used in forms."
  [{:keys [value on-change]}]
  [:f> wysiwyg-editor {:value value
                       :on-change on-change}])
