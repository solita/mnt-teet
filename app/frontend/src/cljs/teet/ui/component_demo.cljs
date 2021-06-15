(ns teet.ui.component-demo
  (:require [clojure.string :as str]
            [teet.ui.material-ui :refer [Button Fab Divider Checkbox CircularProgress LinearProgress]]
            [teet.ui.text-field :refer [TextField] :as text-field]
            [teet.ui.file-upload :as file-upload]
            [teet.ui.icons :as icons]
            [herb.core :refer [<class]]
            [teet.ui.skeleton :as skeleton]
            [teet.ui.buttons :as buttons]
            [teet.ui.common :as ui-common]
            [teet.ui.container :as container]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.select :as select]
            [teet.ui.typography :refer [DataLabel Heading1 Heading2 Heading3 Heading4 Heading5 Paragraph SectionHeading
                                        Subtitle1 Subtitle2
                                        Text TextBold Text2 Text2Bold Text3 Text3Bold]]
            [teet.ui.url :as url]
            [tuck.core :as t]
            [teet.log :as log]
            [reagent.core :as r]
            [teet.ui.rich-text-editor :as rich-text-editor]
            [teet.ui.mentions :as mentions]
            [teet.ui.common :as common-ui]
            [teet.contract.contract-status :as contract-status]
            [teet.contract.contract-common :as contract-common]
            [teet.contract.contracts-view :as contracts-view]
            [teet.ui.table :as table]
            [teet.common.common-styles :as common-styles]
            [teet.ui.date-picker :as date-picker]))


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
    app))

(defn input-fields
  []
  (let [val (r/atom "")
        on-change (fn [e]
                    (reset! val (-> e .-target .-value)))]
    (fn []
      [:section
       [:div {:style {:display "flex"
                      :justify-content "space-evenly"
                      :margin-bottom "2rem"}}
        [TextField {:label "Tekstiä"
                    :value @val
                    :on-change on-change
                    :end-icon [text-field/file-end-icon ".pdf"]}]
        [TextField {:label "end adonrmnet"
                    :value @val
                    :on-change on-change
                    :placeholder "Calendar"
                    :variant :outlined
                    :button-click println}]
        [TextField {:label "Tekstiä"
                    :on-change on-change
                    :value @val
                    :placeholder "Placeholder"
                    :error true}]
        [TextField {:label "Tekstiä"
                    :on-change on-change
                    :value @val
                    :placeholder "Placeholder"
                    :error true
                    :error-text "Form field is required"
                    :variant :filled}]]
       [Divider]])))

(defn- on-toggle [a value]
  (swap! a #(if (% value)
              (disj % value)
              (conj % value))))

(defn container-demo []
  (r/with-let [container-open? (r/atom false)
               checkbox-items (r/atom #{})]
    [container/collapsible-container {:on-toggle #(swap! container-open?
                                                         (fn [x] (not x)))
                                      :open? @container-open?}
     "Collapsible container"
     [itemlist/checkbox-list
      [{:checked? (@checkbox-items "One")
        :value "One"
        :on-toggle (on-toggle checkbox-items "One")}
       {:checked? (@checkbox-items "Two")
        :value "Two"
        :on-toggle (on-toggle checkbox-items "One")}]]]))

(defn- rte-demo []
  (r/with-let [editor-state (r/atom nil)
               saved-state (r/atom nil)]
    [:<>
     [:div
      [rich-text-editor/display-markdown @saved-state]]
     [:div {:style {:margin "2rem"}}
       [rich-text-editor/rich-text-field {:value @editor-state
                                         :on-change #(reset! editor-state %)}]

      [buttons/button-primary {:on-click (fn [_]
                                           (reset! saved-state (rich-text-editor/editor-state->markdown
                                                                 (:editor-state @editor-state))))}
       "Save"]]]))

(defn- typography-demo []
  [:div
   [Heading1 "Heading 1"]
   [Heading2 "Heading 2"]
   [Heading3 "Heading 3"]
   [Heading4 "Heading 4"]
   [Heading5 "Heading 5"]
   [Subtitle1 "Subtitle 1"]
   [Subtitle2 "Subtitle 2"]
   [:hr]
   [SectionHeading "Section Heading"]
   [DataLabel "Data Label / Table Heading"]
   [:hr]
   [TextBold "TextBold: This text uses typography component 'TextBold'."]
   [Text "Text: This text uses typography component 'Text'."]
   [Text2Bold "Text2Bold: This text uses typography component 'Text2Bold'."]
   [Text2 "Text2: This text uses typography component 'Text2'."]
   [Text3Bold "Text3Bold: This text uses typography component 'Text3Bold'."]
   [Text3 "Text3: This text uses typography component 'Text3'."]
   [Paragraph "This text uses the 'Paragraph' variant of 'Text' and thus has a bottom margin.  Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."]
   [Paragraph "See? This is another paragraph."]
   [url/Link {:page :root} "Link to main page"][:br]
   [url/Link2 {:page :root} "Smaller link to main page"]])

(defn buttons-demo []
  [:<>
   [:div {:style {:display "flex"
                  :margin-bottom "2rem"
                  :justify-content "space-evenly"
                  :flex-wrap :wrap}}

    [buttons/button-primary "Button component"]

    [buttons/large-button-primary "Large button"]

    [Button {:color "secondary"}
     "Foo bar baz"]
    [Button {:color :primary :variant :contained
             :start-icon (r/as-element [icons/content-add])}
     "Foo bar baz"]
    [Button {:color "secondary" :variant "contained"
             :end-icon (r/as-element [icons/content-add])}
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
   [buttons/stand-alone-icon-button {:icon [icons/social-person-outlined {:color :primary}]}]
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
    [Divider]]])

(defn checkbox-demo []
  [:div {:style {:display :flex
                 :justify-content :space-evenly
                 :margin-bottom :2rem}}
   [Checkbox {:color :default}]
   [Checkbox {:color :primary}]
   [Checkbox {:color :secondary}]
   [Checkbox {:color :primary :disabled true}]])

(defn- file-upload-demo [e!]
  [:div {:id "file-upload-drag-container"
         :style {:display "flex"
                 :justify-content "space-evenly"
                 :margin-bottom "2rem"}}
   [file-upload/FileUploadButton {:id "upload-btn"
                                  :drag-container-id "file-upload-drag-container"
                                  :on-drop #(e! (->UploadFiles %) #_(->TestFileUpload %))
                                  :drop-message "Drop it like it's hot"}
    "Click to upload"]])

(defn- loader-demo []
  [:div {:style {:width "100%"
                :margin-top "5px"
                :padding "5px 5rem"}}
   [:div {:style {:margin "2rem 0"}}
    [CircularProgress]]
   [:div {:style {:margin "2rem 0"}}
    [LinearProgress]]
   [:div
    [skeleton/skeleton {:width "60%"
                        :height "2rem"}]]])

(defn skeleton-demo []
  [:div {:style {:width "100%"
                 :margin-top "5px"
                 :margin "5px 5rem"}}
   [skeleton/skeleton {:width "60%"
                       :height "2rem"}]])

(defn select-demo []
  [:<>
   [:div {:style {:width "50%"
                  :margin "2rem 0"}}
    [select/form-select {:value "et"
                         :label "Language"
                         :show-empty-selection? true
                         :id "language-select"
                         :name "Language"
                         :items
                         [{:value "et" :label "bar"}
                          {:value "en" :label "baz"}]}]]
   [:div
    [select/select-with-action {:label "Label"
                                :id "select-id"
                                :value "foo"
                                :items [{:value "foo" :label "Foo"}

                                        {:value "bar" :label "Bar"}]}]]])

(defn datepicker-demo []
  [:section
   [:div {:style {:display "flex"
                  :justify-content "space-evenly"}}
    [date-picker/date-input {:label "Date"
                             :placeholder "Placeholder"
                             :on-change #()}]
    [date-picker/date-input {:label "Date with error label"
                             :placeholder "Placeholder"
                             :on-change #()
                             :error true
                             :error-text "This is an error"}]
    [date-picker/date-input {:label "Date with error tooltip"
                             :placeholder "Placeholder"
                             :on-change #()
                             :error true
                             :error-text "This is an error"
                             :error-tooltip? true}]]])

(defn- labeled-data-demo []
  [:div
   [ui-common/labeled-data {:label "Label" :data "Some textual data"}]])

(defn- mentions-demo [e!]
  (r/with-let [empty-state (r/atom "")
               existing-state (r/atom "Hey @[Carla Consultant](ccbedb7b-ab30-405c-b389-292cdfe85271) how are you?")]
    [:<>
     [:div {:data-cy "mentions-empty"}
      "Mentions with initially empty data: "
      [mentions/mentions-input
       {:e! e!
        :value @empty-state
        :on-change #(reset! empty-state (-> % .-target .-value))}]]
     [:div {:data-cy "mentions-existing"}
      "Mentions with existing data: "
      [mentions/mentions-input
       {:e! e!
        :value @existing-state
        :on-change #(reset! existing-state (-> % .-target .-value))}]]]))

(defn- context-menu-demo [e!]
  [:div {:style {:margin-left "100px"}}
   [ui-common/context-menu {:label "click to open"
                            :icon [icons/notification-time-to-leave]
                            :items [{:label "please click"
                                     :on-click #(js/alert "fooled you!")
                                     :icon [icons/maps-directions-boat]}

                                    {:label "click me too"
                                     :on-click #(js/alert "fooled you again!")
                                     :icon [icons/maps-local-pizza]}

                                    {:label "this one is legit"
                                     :link {:target :_blank
                                            :href "http://example.com"}
                                     :icon [icons/notification-bluetooth-audio]}]}]])

(defn contract-demo
  [e!]
  [:div
   [:h4 {:style {:margin-top "8px"}} "Status"]
   [:div {:style {:margin-top "8px" :margin-bottom "8px"}}
    [:h5 {:style {:margin-bottom "8px"}} "Status without label, default size"]
    [:div {:style {:display :flex
                   :align-items :center}}
     [contract-status/contract-status {} :thk.contract.status/signed]
     [contract-status/contract-status {} :thk.contract.status/completed]
     [contract-status/contract-status {} :thk.contract.status/in-progress]
     [contract-status/contract-status {} :thk.contract.status/deadline-approaching]
     [contract-status/contract-status {} :thk.contract.status/deadline-overdue]
     [contract-status/contract-status {} :thk.contract.status/warranty]]]
   [:div {:style {:margin-top "16px" :margin-bottom "8px"}}
    [:h5 {:style {:margin-top "16px" :margin-bottom "8px"}} "Status with label. default size"]
    [:div
     [contract-status/contract-status {:show-label? true} :thk.contract.status/signed]
     [contract-status/contract-status {:show-label? true} :thk.contract.status/completed]
     [contract-status/contract-status {:show-label? true} :thk.contract.status/in-progress]
     [contract-status/contract-status {:show-label? true} :thk.contract.status/deadline-approaching]
     [contract-status/contract-status {:show-label? true} :thk.contract.status/deadline-overdue]
     [contract-status/contract-status {:show-label? true} :thk.contract.status/warranty]]]
   [:div {:style {:margin-top "16px" :margin-bottom "8px"}}
    [:h5 {:style {:margin-top "16px" :margin-bottom "8px"}} "Status without label. Size 15px"]
    [:div {:style {:display :flex
                   :align-items :center}}
     [contract-status/contract-status {:size 15} :thk.contract.status/signed]
     [contract-status/contract-status {:size 15} :thk.contract.status/completed]
     [contract-status/contract-status {:size 15} :thk.contract.status/in-progress]
     [contract-status/contract-status {:size 15} :thk.contract.status/deadline-approaching]
     [contract-status/contract-status {:size 15} :thk.contract.status/deadline-overdue]
     [contract-status/contract-status {:size 15} :thk.contract.status/warranty]]]
   [:h4 {:style {:margin-top "24px"}} "External links"]
   [:div {:style {:margin-top "8px" :margin-bottom "8px"}}
    [contract-common/contract-external-links {:thk.contract/procurement-id 1234
                                              :thk.contract/procurement-number "12341234"
                                              :thk.contract/external-link "www.test.test"}]]
   [:h4 {:style {:margin-top "24px"}} "Information row"]
   [:div {:style {:margin-top "8px" :margin-bottom "8px"}}
    [contract-common/contract-information-row {:thk.contract/type :thk.contract.type/construction-works
                                               :thk.contract/signed-at #inst "2021-05-04T00:00:00.000-00:00"
                                               :thk.contract/start-of-work #inst "2021-05-04T00:00:00.000-00:00"
                                               :thk.contract/deadline #inst "2021-05-04T00:00:00.000-00:00"
                                               :thk.contract/extended-deadline #inst "2021-05-04T00:00:00.000-00:00"
                                               :thk.contract/warranty-end-date #inst "2048-05-08T00:00:00.000-00:00"
                                               :thk.contract/cost "34324"
                                               :thk.contract/status :thk.contract.status/warranty}]]
   [:h4 {:style {:margin-top "24px"}} "Quick Filters"]
   [:div {:style {:margin-top "8px" :margin-bottom "8px"}}
    (r/with-let [shortcut-atom (r/atom :my-contracts)
                 change-shortcut (fn [new-value]
                                   (reset! shortcut-atom new-value))
                 shortcuts [:my-contracts
                            :all-contracts
                            :unassigned]]
      [contracts-view/search-shortcuts {:value @shortcut-atom
                                        :options shortcuts
                                        :change-shortcut change-shortcut}])]
   [:h4 {:style {:margin-top "24px"}} "Filters (this component is contract list specific. Changes are required, if it will be reused)"]
   [:div
    (r/with-let [filter-fields [[:road-number {:type :search-field}]
                                [:project-name {:type :search-field}]
                                [:contract-name {:type :search-field}]
                                [:procurement-number {:type :search-field}]]]
      [contracts-view/filter-inputs {:e! e!
                                     :filter-input-fields filter-fields
                                     :filters-visibility? true}])]])


(defn simple-table-demo
  []
  [table/simple-table
   [["Foo" {}]
    ["bar" {}]
    ["baz" {}]
    ["bax" {}]]
   [[["Foo"]
     ["bar"]
     ["baz"]
     ["bax"]]
    [["Foo"]
     ["bar"]
     ["baz"]
     ["bax"]]]])

(defn basic-information-column-demo
  []
  [:div {:style {:display :flex
                 :justify-content :center}}
   [:div {:style {:flex 1
                  :max-width "500px"}}
    [common-ui/basic-information-column
     [{:key "1"
       :label [:strong "foo"]
       :data [:span "wiuh"]}
      {:key "2"
       :label [:strong "bar"]
       :data [:span "wiuh"]}]]]])

(defn basic-ui-component-demo
  []
  [:div
   [:div {:class (<class common-styles/margin-bottom 2)}
    [:h1 {:class (<class common-styles/margin-bottom 1)}
     "Primary tag component: "]
    [common-ui/primary-tag "Primary tag"]]])

(def demos
  [{:id :context-menu
    :heading "Context menu"
    :component [context-menu-demo]}
   {:id :rte
    :heading "Rich text editor"
    :component [rte-demo]}
   {:id :typography
    :heading "Typography"
    :component [typography-demo]}
   {:id :buttons
    :heading "Buttons"
    :component [buttons-demo]}
   {:id :checkbox
    :heading "Checkbox"
    :component [checkbox-demo]}
   {:id :text
    :heading "Text fields"
    :component [input-fields]}
   {:id :upload
    :heading "File upload"
    :component [file-upload-demo]}
   {:id :itemlist
    :heading "Loaders"
    :component [loader-demo]}
   {:id :select
    :heading "Select"
    :component [select-demo]}
   {:id :datepicker
    :heading "Datepicker"
    :component [datepicker-demo]}
   {:id :labeled-data
    :heading "Labeled data"
    :component [labeled-data-demo]}
   {:id :container
    :heading "Container"
    :component [container-demo]}
   {:id :mentions
    :heading "Mentions input"
    :component [mentions-demo]}
   {:id :contracts
    :heading "Contract components"
    :component [contract-demo]}
   {:id :simple-table
    :heading "simple table"
    :component [simple-table-demo]}
   {:id :basic-information-column
    :heading "basic info column demo"
    :component [basic-information-column-demo]}

   {:id :common-basic-ui-components
    :heading "basic UI components demo"
    :component [basic-ui-component-demo]}])

(defn demo
  [e! {query :query :as _app}]
  (let [show-demo? (if-let [show (:show query)]
                    (into #{}
                          (map keyword)
                          (str/split show #","))
                    (constantly true))]
    [:<>
     (for [{:keys [id heading component]} demos
           :when (show-demo? id)]
       ^{:key (str id)}
       [:div
        [Heading2 heading]
        (conj component e!)
        [Divider {:style {:margin "2rem 0"}}]])]))
