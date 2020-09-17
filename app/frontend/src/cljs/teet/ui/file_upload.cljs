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
            [teet.ui.buttons :as buttons]
            [teet.ui.drag :as drag]))




(defn ->vector [file-list]
  (mapv (fn [i]
          {:file-object (.item file-list i)})
        (range (.-length file-list))))

(defn- file-vector [e]
  (-> e .-target .-files ->vector))



(defn FileUpload
  "Note! Use one of the predefined file upload components, such as
  FileUploadButton instead of using this directly."
  [{:keys [id on-drop drop-message multiple? drag-container-id]} & children]
  (r/with-let [remove-upload-zone!
               (drag/register-drop-zone! {:element-id drag-container-id
                                          :on-drop #(on-drop (drag/dropped-files %))
                                          :label (or drop-message "")})]
    (into [:label
           {:htmlFor id}
           [:input {:style {:display "none"}
                    :id id
                    :multiple multiple?
                    :droppable "true"
                    :type "file"
                    :on-change #(on-drop (file-vector %))}]]
          children)
    (finally
      (remove-upload-zone!))))

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
