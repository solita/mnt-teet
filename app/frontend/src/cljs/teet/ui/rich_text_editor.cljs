(ns teet.ui.rich-text-editor
  (:require ["draft-js" :as draft-js]
            ["markdown-draft-js" :as mkdraft-js]
            ["react" :as react]
            [alandipert.storage-atom :refer [local-storage]]
            [herb.core :refer [<class]]
            [reagent.core :as r]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.buttons :as buttons]
            [teet.ui.common :as common]
            [teet.ui.material-ui :refer [Divider]]
            [teet.ui.util :as util]
            [teet.util.string :as string]
            [clojure.string :as str]
            [teet.common.common-styles :as common-styles]
            [teet.ui.typography :as typography]
            [teet.ui.form :as form])
  (:require-macros [teet.util.js :refer [js>]]))

(def ^:private Editor (r/adapt-react-class draft-js/Editor))

(defonce wysiwyg-data
         (local-storage (atom nil) "editor-state"))

(def character-limit 4000)

(declare editor-state->markdown markdown->editor-state)

(defrecord RichTextFieldValue [editor-state]
  form/ToValue
  (to-value [_]
    (editor-state->markdown editor-state)))

(defn- ->editor-state [value]
  (cond
    (nil? value)
    (markdown->editor-state "")

    (string? value)
    (markdown->editor-state value)

    (instance? RichTextFieldValue value)
    (:editor-state value)

    :else (throw (ex-info "Unrecognized rich text form value"
                          {:unrecognized-value value}))))

(defn editor-style
  [error]
  {:padding "1rem"
   :background-color theme-colors/white
   :border (str "1px solid " (if error
                               theme-colors/error
                               theme-colors/black-coral-1))
   :border-radius "3px"})


(def block-types
  [{:label "H1"
    :style "header-one"}
   {:label "H2"
    :style "header-two"}
   {:label "OL"
    :style "ordered-list-item"}
   {:label "UL"
    :style "unordered-list-item"}])

(def inline-styles
  [{:label "Bold"
    :style "BOLD"}
   {:label "Italic"
    :style "ITALIC"}
   {:label "Underline"
    :style "UNDERLINE"}])

(defn type-control-button-style
  [active?]
  ^{:pseudo {:focus theme-colors/button-focus-style}}
  {:border :none
   :padding 0
   :font-weight (if active?
                  :bold
                  :normal)})

(defn type-control-button
  [{:keys [label style]} click active]
  [buttons/button-text {:on-mouse-down (fn [e]
                                         (.preventDefault e)
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
  [{:keys [children decoratedText]}]
  (js>
    [common/Link {:href decoratedText
                  :target "_blank"
                  :rel "noopener noreferrer"}
     children]))

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


(def url-regex #"https?://(www\.)?[-a-zA-Z0-9@:%._\+~#=]{2,256}\.[a-z]{2,4}\b([-a-zA-Z0-9@:%_\+.~#?&//=]*)")

(defn re-pos
  "Returns index where the match starts and the matched string"
  [re s]
  (let [re (js/RegExp. (.-source re) "g")]
    (loop [res {}]
      (if-let [m (.exec re s)]
        (recur (assoc res (.-index m) (first m)))
        res))))

(defn findLinkWithRegex
  [contentBlock callback _]
  (js>
    (let [text (.getText contentBlock)]
      (when-let [matches (re-pos url-regex text)]
        (doall
          (for [[index match] matches]
            (callback index (+ index (count match)))))))))

(defn focus! [editor-id]
  (.focus (js/document.querySelector (str "#" editor-id " .DraftEditor-editorContainer div:nth-child(1)"))))

;; example of how to extend draft-to-markdown from: https://github.com/Rosey/markdown-draft-js/issues/94
(def style-items #js {:UNDERLINE #js {"open" (fn []
                                               "++")
                                      "close" (fn []
                                                "++")}})

(defn editor-state->markdown [^draft-js/EditorState editor-state]
  (js>
    (mkdraft-js/draftToMarkdown
      (draft-js/convertToRaw
        (.getCurrentContent editor-state))
      #js {:preserveNewlines true
           :styleItems style-items})))

(defn validate-rich-text-form-field-not-empty
  [value]
  (let [value (if (instance? RichTextFieldValue value)
                (editor-state->markdown (:editor-state value))
                value)]
    (when (or
           (nil? value)
           (every? empty?
                   (string/words (str/replace value #"\u200b" ""))))
      "Rich text editor can't be empty")))

(def ^:private decorator
  (draft-js/CompositeDecorator. #js [#js {:strategy findLinkWithRegex
                                          :component (r/reactify-component link-comp)}]))

(defn markdown->editor-state [markdown]
  (.createWithContent draft-js/EditorState
                      (draft-js/convertFromRaw
                        (mkdraft-js/markdownToDraft markdown #js {:preserveNewlines true
                                                                  :blockStyles #js {"ins_open" "UNDERLINE"}
                                                                  :remarkableOptions #js {:enable #js {:inline "ins"}}}))
                      decorator))


(defn wysiwyg-editor
  "Rich text editor component that keeps its own internal state.

  Options:

  :id         string id to give to the root editor container element (used for some commands)
  :value      current draftjs EditorState object (or nil for empty)
  :on-change  callback to update editor state"

  [{:keys [value on-change id label required error dark-theme?]
    :or {dark-theme? false}}]
  (js>
   (let [read-only? (nil? on-change)
         editorState (or value (.createEmpty draft-js/EditorState decorator))

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
                        (on-change (.toggleInlineStyle draft-js/RichUtils editorState style))
                        (r/after-render focus-editor))
         blockToggle (fn [type]
                       (on-change (.toggleBlockType draft-js/RichUtils editorState type))
                       (r/after-render focus-editor))]
     [:div (when id {:id id})
      [:div
       (when label
         [:label {:class (<class common-styles/input-label-style read-only? dark-theme?)}
          [typography/Text2Bold
           label (when required
                   [common/required-astrix])]])
       [:div (merge {:on-click focus-editor}
                    (when-not read-only? {:class (<class editor-style error)}))
        (when-not read-only?
          [:<>
           [block-style-controls editorState blockToggle]
           [inline-style-controls editorState inlineToggle]
           [Divider {:style {:margin "1rem 0"}}]])
        [Editor {:ref set-editor-ref!
                 :readOnly (nil? on-change)
                 :editorState editorState
                 :handleKeyCommand handle-key-command
                 :blockRenderMap draft-js/DefaultDraftBlockRenderMap
                 :on-change (fn [editorState]
                              (when on-change
                                (on-change editorState)))}]]]])))

(defn display-markdown
  "Display a markdown that does not change during the component lifecycle.
  Parses and creates an editorstate once at mount."
  [markdown]
  (if-let [editor-state (when markdown
                          (markdown->editor-state markdown))]
    [:f> wysiwyg-editor {:value editor-state}]
    [:span]))

(defn rich-text-field
  "Rich text input that can be used in forms."
  [{:keys [value on-change label required error read-only?]}]

  [:f> wysiwyg-editor {:value (->editor-state value)
                       :read-only? read-only?
                       :label label
                       :required required
                       :error error
                       :on-change (when-not read-only?
                                    #(on-change
                                      (->RichTextFieldValue %)))}])
