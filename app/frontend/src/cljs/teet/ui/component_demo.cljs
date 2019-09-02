(ns teet.ui.component-demo
  (:require [clojure.string :as str]
            [teet.ui.material-ui :refer [Paper Button Fab IconButton TextField Chip Avatar MuiThemeProvider CssBaseline Divider Checkbox]]
            [teet.ui.file-upload :as file-upload]
            [teet.ui.icons :as icons]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.progress :as progress]
            [teet.ui.typography :refer [DataLabel Heading1 Heading2 Heading3 Paragraph SectionHeading Text]]
            [tuck.core :as t]
            [cljs-bean.core :refer [->clj]]
            [taoensso.timbre :as log]))

(defrecord TestFileUpload [files])
(defrecord UploadFiles [files])
(defrecord UploadFileUrlReceived [file url])

(defn- file-info [^js/File f]
  {:document/name (.-name f)
   :document/size (.-size f)
   :document/type (.-type f)
   :thk/id "14935"})


(extend-protocol t/Event
  TestFileUpload
  (process-event [{files :files} app]
    (js/alert (str "Dropped "
                   (str/join ", "
                             (map #(str "\"" (pr-str (file-info %)) "\"") files))))
    app)

  UploadFiles
  (process-event [{files :files} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :document/upload
           :payload (file-info (first files))
           :result-event #(->UploadFileUrlReceived (first files) %)}))

  UploadFileUrlReceived
  (process-event [{:keys [file url]} app]
    (log/info "upload file: " file " to URL: " url)
    (-> (js/fetch (:url url) #js {:method "PUT"
                              :body file})
        (.then (fn [^js/Response resp]
                 (if (.-ok resp)
                   (log/info "Upload ok" (.-status resp))
                   (log/warn "Upload failed: " (.-status resp) (.-statusText resp))))))
    app)
  )

(defn demo
  [e!]
  [:div
   [Heading1 "TEET UI Components"]
   [Divider]
   [:section
    [Heading2 "Typography"]
    [:div
     [Heading1 "Heading 1"]
     [Heading2 "Heading 2"]
     [Heading3 "Heading 3"]
     [SectionHeading "Section Heading"]
     [DataLabel "Data Label / Table Heading"]
     [Text "This text uses typography component 'Text'. Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."]
     [Paragraph "This text uses typography component 'Paragraph' and thus has a bottom margin."]
     [Paragraph "See?"]]]
   [Divider]
   [:section
    [Heading2 "Buttons"]
    [:div {:style {:display "flex"
                   :margin-bottom "2rem"
                   :justify-content "space-evenly"
                   :flex-wrap :wrap}}
     [Button {:color "primary"}
      "Foo bar baz"]
     [Button {:color "secondary"}
      "Foo bar baz"]
     [Button {:color :primary :variant :contained}
      [icons/content-add]
      "Foo bar baz"]
     [Button {:color "secondary" :variant "contained"}
      "Foo bar baz"]
     [Button {:variant "contained"
              :disabled true}
      "Foo bar baz"]
     [Button {:variant "contained"}
      "Foo bar baz"]
     [Button {:color "primary" :variant "outlined"}
      "Foo bar baz"]
     [Button {:variant "outlined"}
      "Foo bar baz"]
     [Button {:variant "outlined"
              :disabled true}
      "Foo bar baz"]
     [Button {:color "secondary" :variant "outlined"}
      "Foo bar baz"]]
    [:div {:style {:display "flex"
                   :justify-content "space-evenly"
                   :margin-bottom "2rem"}}
     [Fab
      [icons/navigation-check]]
     [Fab {:color "secondary"}
      [icons/navigation-check]]
     [Fab {:color "primary"}
      [icons/navigation-check]]
     [Fab {:disabled true}
      [icons/navigation-check]]
     [Fab {:variant "extended"}
      [icons/navigation-check]
      "FooBar"]
     [Divider]]
    [:section
     [Heading2
      "Checkbox"]
     [:div {:style {:display :flex
                    :justify-content :space-evenly
                    :margin-bottom :2rem}}
      [Checkbox {:color :default}]
      [Checkbox {:color :primary}]
      [Checkbox {:color :secondary}]
      [Checkbox {:color :primary :disabled true}]]
     [Divider]]
    [:section
     [Heading2 "Textfields"]
     [:div {:style {:display "flex"
                    :justify-content "space-evenly"
                    :margin-bottom "2rem"}}
      [TextField {:label "Tekstiä"}]
      [TextField {:label "Tekstiä" :placeholder "Placeholder" :variant :outlined}]
      [TextField {:label "Tekstiä" :placeholder "Placeholder" :error true}]
      [TextField {:label "Tekstiä" :placeholder "Placeholder" :error true :variant :filled}]]
     [Divider]]
    [:section
     [Heading2 "File upload"]
     [:div {:style {:display "flex"
                    :justify-content "space-evenly"
                    :margin-bottom "2rem"}}
      [file-upload/FileUploadButton {:id "upload-btn"
                                     :on-drop #(e! (->UploadFiles %) #_(->TestFileUpload %))
                                     :drop-message "Drop it like it's hot"}
       "Click to upload"]]]]
    [Divider]
    [:section
     [Heading2 "Itemlist component"]
     [:div {:style {:width "50%"
                    :margin "2rem 0"}}
      [itemlist/ProgressList
       {:title "itemlist title" :subtitle "Foo bar"}
       [{:status :success
         :link "#/12"
         :name "First task"}
        {:status :fail
         :link "#/123"
         :name "second task"}
        {:status :created
         :link "#/1234"
         :name "third task"}
        {:status :created
         :link "#/1323"
         :name "asdasd task"}
        {:status :success
         :link "#/3232"
         :name "fifth task"}]]]
     [:div {:style {:width "50%"
                    :margin "2rem 0"}}
      [itemlist/LinkList
       {:title "itemlist title" :subtitle "Foo bar"}
       [{:link "/foo"
         :name "First task"}
        {:status :fail
         :name "second task"}
        {:link "/foo"
         :name "third task"}
        {:link "/foo"
         :name "asdasd task"}
        {:link "/:success"
         :name "fifth task"}]]]

     [Divider]]])
