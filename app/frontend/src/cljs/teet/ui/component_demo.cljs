(ns teet.ui.component-demo
  (:require [clojure.string :as str]
            [teet.ui.material-ui :refer [Paper Button Fab IconButton TextField Chip Avatar MuiThemeProvider CssBaseline Divider Checkbox]]
            [teet.ui.file-upload :as file-upload]
            [teet.ui.icons :as icons]
            [teet.ui.typography :refer [DataLabel Heading1 Heading2 Heading3 Paragraph SectionHeading Text]]
            [tuck.core :as t]))

(defrecord TestFileUpload [files])

(extend-protocol t/Event
  TestFileUpload
  (process-event [{files :files} app]
    (js/alert (str "Dropped "
                   (str/join ", "
                             (map #(str "\"" (.-name %) "\"") files))))
    app))

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
      "FooBar"]]
    [:section
     [Heading2
      "Checkbox"]
     [:div {:style {:display :flex
                    :justify-content :space-evenly
                    :margin-bottom :2rem}}
      [Checkbox {:color :default}]
      [Checkbox {:color :primary}]
      [Checkbox {:color :secondary}]
      [Checkbox {:color :primary :disabled true}]]]
    [:section
     [Heading2 "Textfields"]
     [:div {:style {:display "flex"
                    :justify-content "space-evenly"
                    :margin-bottom "2rem"}}
      [TextField {:label "Tekstiä"}]
      [TextField {:label "Tekstiä" :placeholder "Placeholder" :variant :outlined}]
      [TextField {:label "Tekstiä" :placeholder "Placeholder" :error true}]
      [TextField {:label "Tekstiä" :placeholder "Placeholder" :error true :variant :filled}]]]
    [:section
     [Heading2 "File upload"]
     [:div {:style {:display "flex"
                    :justify-content "space-evenly"
                    :margin-bottom "2rem"}}
      [file-upload/FileUploadButton {:id "upload-btn"
                                     :on-drop #(e! (->TestFileUpload %))
                                     :drop-message "Drop it like it's hot"}
       "Click to upload"]]]]])
