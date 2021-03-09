(ns teet.ui.file-upload
  (:require [clojure.string :as str]
            [herb.core :refer [<class]]
            [reagent.core :as r]
            [teet.ui.material-ui :refer [IconButton Table TableHead TableBody TableRow TableCell]]
            [teet.ui.text-field :refer [TextField] :as text-field]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.icons :as icons]
            [teet.localization :refer [tr tr-enum]]
            [teet.ui.typography :refer [Heading1 SectionHeading]]
            [teet.ui.format :as format]
            [teet.file.file-model :as file-model]
            teet.file.file-spec
            [teet.ui.buttons :as buttons]
            [teet.ui.drag :as drag]
            [teet.log :as log]
            [teet.ui.select :as select]
            [teet.file.filename-metadata :as filename-metadata]
            [teet.ui.typography :as typography]
            [teet.common.common-styles :as common-styles]
            [teet.ui.common :as common-ui]
            [teet.util.datomic :as du]))

(defn ->vector [file-list]
  (mapv (fn [i]
          {:file-object (.item file-list i)})
        (range (.-length file-list))))

(defn- file-vector [e]
  (-> e .-target .-files ->vector))

(defn FileUpload
  "Note! Use one of the predefined file upload components, such as
  FileUploadButton instead of using this directly."
  [{:keys [on-drop drop-message drag-container-id]} & _children]
  (let [current-on-drop (atom on-drop)
        remove-upload-zone!
        (drag/register-drop-zone! {:element-id drag-container-id
                                   :on-drop #(let [on-drop @current-on-drop]
                                               (on-drop (drag/dropped-files %)))
                                   :label (or drop-message "")})]
    (r/create-class
     {:component-will-receive-props
      (fn [_this [_ {on-drop :on-drop} & _]]
        (reset! current-on-drop on-drop))

      :component-will-unmount
      (fn [_this]
        (remove-upload-zone!))

      :reagent-render
      (fn [{:keys [id on-drop multiple?]} & children]
        (into [:label
               {:htmlFor id}
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


(defn files-field-entry [file-entry]
  (-> file-entry
      :file-object
      file-model/file-info))

(defn wrong-task-error [expected-task-type task-types]
  (let [correct-task (some #(when (= (:filename/code %) expected-task-type)
                              (:db/ident %))
                           task-types)]
    (tr [:file-upload :file-belongs-to-task] {:task (tr-enum correct-task)})))

(defn validate-name [{:file/keys [description extension] :as _file-row}]
  (cond
    (not (file-model/valid-description-length? description)) {:error :description-too-long}
    (not (file-model/valid-chars-in-description? description)) {:error :invalid-chars-in-description}
    (str/blank? description) {:error :description-and-extension-required}
    (str/blank? extension) {:error :file-type-not-allowed}))

(defn validate-seq-number [{:file/keys [sequence-number]}]
  (when (and sequence-number
             ;; Valid sequence number 1 - 10000
             (not (< 0 sequence-number 10000)))
    {:error :invalid-sequence-number}))

(defn common-file-validation
  [file]
  (let [error (file-model/validate-file file)]
    (case (:error error)
      :file-type-not-allowed
      {:title (tr [:document :invalid-file-type])
       :description [:<>
                     (str/upper-case (file-model/filename->suffix (:file/name file)))
                     " "
                     [:a {:target "_blank"
                          :href "https://confluence.mkm.ee/pages/viewpage.action?spaceKey=TEET&title=TEET+File+format+list"}
                      (tr [:document :invalid-file-type])]]}

      :file-too-large
      {:title (tr [:document :file-too-large])
       :description ""}

      :description-too-long
      {:title (tr [:error :description-too-long] {:limit file-model/max-description-length})
       :description ""}

      :invalid-chars-in-description
      {:title (tr [:error :invalid-chars-in-description] {:characters file-model/allowed-chars-string})
       :description ""}

      :file-empty
      {:title (tr [:file-upload :empty-file])
       :description ""}
      ;; All validations ok
      nil)))

(defn validate-file [e! project-id task {:keys [metadata] :as file-row}]
  (let [file-info (files-field-entry file-row)
        file-row-info (merge file-row file-info)]
    (or (common-file-validation file-row-info)
      (when-let [error (or (and metadata (validate-name file-row-info))
                         (validate-seq-number file-row-info)
                         (file-model/validate-file-metadata project-id task metadata))]
        (case (:error error)

          ;; Check for wrong project
          :wrong-project
          {:title (tr [:file-upload :wrong-project])
           :description ""}

          ;; Check for wrong task (if metadata can be parsed)
          :wrong-task
          {:title (tr [:file-upload :wrong-task])
           :description [select/with-enum-values
                         {:e! e!
                          :attribute :task/type}
                         [wrong-task-error (:task metadata)]]}

          :description-and-extension-required
          {:title (tr [:file-upload :description-required])
           :description ""}

          :invalid-sequence-number
          {:title (tr [:file-upload :invalid-sequence-number])
           :description ""}

          ;; All validations ok
          nil)))))

(defn files-field-row [{:keys [e! update-file delete-file
                               project-id task]} file-row]
  [:<>
   [TableRow {}
    [TableCell {:style {:border :none}}
     [TextField {:value (:file/description file-row)
                 :on-change #(update-file {:file/description
                                           (-> % .-target .-value)})
                 :end-icon [text-field/file-end-icon (:file/extension file-row)]}]]
    [TableCell {:style {:border :none}}
     [select/select-enum {:e! e!
                          :show-label? false
                          :attribute :file/document-group
                          :value (:file/document-group file-row)
                          :on-change #(update-file {:file/document-group %})}]]
    [TableCell {:style {:border :none}}
     [TextField {:value (or (:file/sequence-number file-row) "")
                 :disabled (nil? (:file/document-group file-row))
                 :type :number
                 :placeholder "0000"
                 :inline? true
                 :on-change #(let [n (-> % .-target .-value)]
                               (update-file
                                {:file/sequence-number (if (str/blank? n)
                                                         nil
                                                         (js/parseInt n))}))}]]
    [TableCell {:style {:border :none}}
     [IconButton {:edge "end"
                  :on-click delete-file}
      [icons/action-delete]]]]
   [TableRow {}
    [TableCell {:colSpan 4}
     (if-let [{:keys [title description]} (validate-file e! project-id task file-row)]
       [common-ui/info-box {:variant :error
                            :title title
                            :content description}]
       [:<>
        (when (get-in file-row [:metadata :file-id])
          [common-ui/info-box {:cy :new-version
                               :title (tr [:file-upload :already-uploaded])
                               :content (tr [:file-upload :new-version-will-be-created])}])
        (when-let [part-number (some-> file-row :metadata :part js/parseInt)]
          (when (not (zero? part-number))
            (if-let [existing-part (some #(when (= part-number
                                                   (:file.part/number %))
                                            %)
                                         (:file.part/_task task))]
              ;; Existing file part
              [common-ui/info-box {:title (tr [:file-upload :file-will-be-added-to-part]
                                              {:part (:file.part/name existing-part)})
                                   :content (tr [:file-upload :file-will-be-added-to-part-text])}]

              ;; New file part
              [common-ui/info-box {:title (tr [:file-upload :file-new-part])
                                   :content (tr [:file-upload :file-new-part-text])}])))
        (let [{:file/keys [name size]} (files-field-entry file-row)]
          [:<>
           (when (:changed? file-row)
             (tr [:file-upload :original-filename] {:name name}))
           " "
           (format/file-size size)])])]]])

(defn files-field [{:keys [e! value on-change error project-id
                           activity task
                           single?]}]
  (let [update-file (fn [i new-file-data]
                      (on-change (update value i merge new-file-data
                                         {:changed? true})))
        delete-file (fn [i]
                      (on-change (into (subvec value 0 i)
                                       (subvec value (inc i)))))]
    [:div {:class (<class files-field-style error)
           :id "files-field-drag-container"}
     [SectionHeading (tr [:common :files])]
     [Table {:class (<class common-styles/margin-bottom 1)}
      [TableHead {}
       [TableRow {}
        [TableCell {}
         (tr [:file-upload :description])]
        [TableCell {}
         (tr [:fields :file/document-group])]
        [TableCell {}
         (if (du/enum= :activity.name/land-acquisition (:activity/name activity))
           (tr [:fields :file/position-number])
           (tr [:fields :file/sequence-number]))]]]

      [TableBody {}
       (doall
        (map-indexed
         (fn [i file-row]
           ^{:key i}
           [files-field-row {:e! e!
                             :project-id project-id
                             :task task
                             :update-file (r/partial update-file i)
                             :delete-file (r/partial delete-file i)}
            file-row])
         value))]]
     (when (not (and single? (pos? (count value))))
       [FileUploadButton {:id "files-field"
                          :drag-container-id "files-field-drag-container"
                          :on-drop #(on-change (into (or value []) %))}
        [icons/content-file-copy]
        (tr [:common :select-files])])]))
