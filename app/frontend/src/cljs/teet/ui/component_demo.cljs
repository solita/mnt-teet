(ns teet.ui.component-demo
  (:require [teet.ui.material-ui :refer [Paper Button Fab IconButton TextField Chip Avatar MuiThemeProvider CssBaseline Typography Divider Checkbox]]
            [teet.ui.icons :as icons]))
(defn demo
  []
  [:div
   [:section
    [Typography {:variant "h1"}
     "Typography"]
    [Typography {:variant "h2"}
     "Typography"]
    [Typography {:variant "h3"}
     "Typography"]
    [Typography {:variant "h4"}
     "Typography"]
    [Typography {:variant "h5"}
     "Typography"]
    [Typography {:variant "h6"}
     "Typography"]]
   [Divider]
   [:section
    [Typography {:variant "h5"}
     "Buttons"]
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
     [Typography {:variant "h5"}
      "Checkbox"]
     [:div {:style {:display :flex
                    :justify-content :space-evenly
                    :margin-bottom :2rem}}
      [Checkbox {:color :default}]
      [Checkbox {:color :primary}]
      [Checkbox {:color :secondary}]
      [Checkbox {:color :primary :disabled true}]]]
    [:section
     [Typography {:variant :h5}
      "Textfields"]
     [:div {:style {:display "flex"
                    :justify-content "space-evenly"
                    :margin-bottom "2rem"}}
      [TextField {:label "Tekstiä"}]
      [TextField {:label "Tekstiä" :placeholder "Placeholder" :variant :outlined}]
      [TextField {:label "Tekstiä" :placeholder "Placeholder" :error true}]
      [TextField {:label "Tekstiä" :placeholder "Placeholder" :error true :variant :filled}]]]]])
