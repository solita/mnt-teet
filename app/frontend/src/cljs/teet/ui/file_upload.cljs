(ns teet.ui.file-upload
  (:require [herb.core :refer [<class]]
            [reagent.core :as r]
            [teet.ui.material-ui :refer [Button IconButton List ListItem
                                         ListItemText ListItemSecondaryAction]]
            [teet.ui.text-field :refer [TextField]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.icons :as icons]
            [teet.localization :refer [tr]]
            [teet.ui.typography :refer [Heading1 SectionHeading]]
            [teet.ui.format :as format]
            [teet.file.file-model :as file-model]
            teet.file.file-spec
            [teet.ui.buttons :as buttons]))

(defn overlay-style
  "Overlay style without position coordinates"
  []
  {:position :fixed
   :z-index 1500

   ;; Opaque background
   :background-color (theme-colors/primary-alpha 0.5)

   ;; Border
   :border-style :dashed
   :border-width "5px"
   :border-radius "10px"
   :border-color theme-colors/secondary

   ;; Text
   :text-align :center})

(defn- page-overlay []
  (merge (overlay-style)
         {;; Cover the whole page
          :height "100%"
          :width "100%"
          :left 0
          :top 0}))


(defn- page-overlay-message []
  {:color theme-colors/white
   :text-shadow "0px 0px 8px #333333"})

(defn ->vector [file-list]
  (mapv (fn [i]
          {:file-object (.item file-list i)})
        (range (.-length file-list))))

(defn- file-vector [e]
  (-> e .-target .-files ->vector))

(defn file-from-drop [e]
  (-> e
      .-dataTransfer
      .-files
      ->vector))

(defn- on-drag-over [event]
  (.stopPropagation event)
  (.preventDefault event))

(defn- on-drop-f [state f]
  (fn [event]
    (.stopPropagation event)
    (.preventDefault event)
    (swap! state assoc :overlay 0)
    (f (file-from-drop event))))

(defn- on
  "Adds an event listener function `f` for `event` on HTML
  `element`. Returns a function that when called removes the said
  event listener."
  [element event f]
  (.addEventListener element event f)
  (fn []
    (.removeEventListener element event f)))

(defn- set-overlay [state shown?]
  (fn [_]
    (swap! state update :overlay (if shown? inc dec))))

(defn FileUpload
  "Note! Use one of the predefined file upload components, such as
  FileUploadButton instead of using this directly."
  [{drag-container-id :drag-container-id} & _]
  (let [state (r/atom {:overlay 0
                       :events-to-remove []
                       :enable-pointer-events nil})
        window? (nil? drag-container-id)
        drag-container #(if drag-container-id
                          (js/document.getElementById drag-container-id)
                          js/window)]
    (r/create-class
     {:display-name "file-upload"
      :component-did-mount
      (fn [_]
        (swap! state assoc :events-to-remove
               (->> [["dragenter" (set-overlay state true)]
                     ["dragleave" (set-overlay state false)]]
                    (mapv (partial apply on (drag-container))))))

      :component-will-unmount
      (fn [_]
        (swap! state update :events-to-remove
               (fn [events-to-remove]
                 (doseq [remove-event events-to-remove]
                   (remove-event))
                 [])))
      :reagent-render
      (fn [{:keys [id on-drop drop-message multiple?]}
           & children]
        (into [:label
               {:htmlFor id}
               (when (pos? (:overlay @state))
                 [:div.page-overlay
                  (merge
                   {:droppable "true"
                    :on-drop (on-drop-f state on-drop)
                    :on-drag-over on-drag-over}
                   (if window?
                     {:class (<class page-overlay)}
                     (let [rect (-> (drag-container)
                                    .getClientRects
                                    (aget 0))]
                       {:class (<class overlay-style)
                        :style {:top (aget rect "top")
                                :left (aget rect "left")
                                :width (aget rect "width")
                                :height (aget rect "height")}})))
                  [Heading1 {:classes {:h1 (<class page-overlay-message)}}
                   drop-message]])
               [:input {:style {:display "none"}
                        :id id
                        :multiple multiple?
                        :droppable "true"
                        :type "file"
                        :on-change #(on-drop (file-vector %))}]]
              children))})))

(defn FileUploadButton [{:keys [id on-drop drop-message multiple? button-attributes
                                drag-container-id]
                         :or {multiple? true}} & children]
  [FileUpload {:id id
               :drag-container-id drag-container-id
               :on-drop on-drop
               :drop-message drop-message
               :multiple? multiple?}
   (into [buttons/link-button (merge {:component :span
                                      :style {:cursor :pointer}}
                                     button-attributes)]
         children)])

(defn- files-field-style [error]
  (merge {:min-height    "300px"

          ;; FIXME: approximate "outlined" input like material UI
          ;; should use material ui classes directly?
          :border        "solid 1px #e0e0e0"
          :border-radius "3px"
          :padding       "1rem"}
         (when error
           {:border (str "solid 1px " theme-colors/error)})))                 ;;This should also use material ui theme.error

(defn file-info
  [{:file/keys [name type size] :as _file} invalid-file-type? file-too-large?]
  [:<>
   (into [:span (merge {} (when invalid-file-type?
                            {:style {:color theme-colors/error}}))
          (str (or (not-empty type)
                   (file-model/filename->suffix name)))]
         (when invalid-file-type?
           [" "
            [:a {:target "_blank"
                 :href "https://confluence.mkm.ee/pages/viewpage.action?spaceKey=TEET&title=TEET+File+format+list"}
             (tr [:document :invalid-file-type])]]))
   [:span {:style (merge {:display :block}
                         (when file-too-large?
                           {:color theme-colors/error}))}
    (str (format/file-size size))
    (when file-too-large?
      (str " " (tr [:document :file-too-large])))]])

(defn files-field-entry [file-entry]
  (-> file-entry
      :file-object
      file-model/file-info
      file-model/type-by-suffix))

(defn files-field [{:keys [value on-change error]}]
  [:div {:class (<class files-field-style error)
         :id "files-field-drag-container"}
   [SectionHeading (tr [:common :files])]
   [List {:dense true}
    (doall
     (map-indexed
      (fn [i ^js/File file]
        ^{:key i}
        [ListItem {}
         (let [{:file/keys [type name size] :as file}
               (files-field-entry file)
               invalid-file-type? (not (file-model/upload-allowed-file-types type))
               file-too-large? (> size file-model/upload-max-file-size)]
           [ListItemText (merge
                          {:primary name
                           :secondary (r/as-element [file-info file invalid-file-type? file-too-large?])})])
         [ListItemSecondaryAction
          [TextField {:value (or (:file/pos-number file) "")
                      :type :number
                      :placeholder "POS#"
                      :inline? true
                      :on-change #(on-change (assoc-in value [i :file/pos-number]
                                                       (-> % .-target .-value)))}]
          [IconButton {:edge "end"
                       :on-click #(on-change (into (subvec value 0 i)
                                                   (subvec value (inc i))))}
           [icons/action-delete]]]])
      value))]
   [FileUploadButton {:id "files-field"
                      :drag-container-id "files-field-drag-container"
                      :on-drop #(on-change (into (or value []) %))}
    [icons/content-file-copy]
    (tr [:common :select-files])]])
