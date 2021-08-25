(ns teet.land.owner-opinion-view
  (:require [reagent.core :as r]
            [teet.ui.form :as form]
            [teet.ui.text-field :refer [TextField]]
            [teet.localization :refer [tr tr-enum]]
            [teet.ui.select :as select]
            teet.land.owner-opinion-specs
            [teet.ui.material-ui :refer [Collapse Paper Grid Popper ButtonBase Divider IconButton]]
            [teet.ui.date-picker :as date-picker]
            [teet.ui.rich-text-editor :as rich-text-editor]
            [teet.common.common-controller :as common-controller]
            [teet.ui.buttons :as buttons]
            [teet.common.common-styles :as common-styles]
            [herb.core :refer [<class]]
            [teet.ui.common :as common]
            [teet.ui.icons :as icons]
            [teet.ui.typography :as typography]
            [teet.land.land-controller :as land-controller]
            [teet.land.owner-opinion-controller :as opinion-controller]
            [teet.land.owner-opinion-style :as owner-opinion-style]
            [teet.ui.query :as query]
            [teet.theme.theme-colors :as theme-colors]
            [teet.util.datomic :as du]
            [teet.ui.panels :as panels]
            [teet.ui.format :as format]
            [teet.ui.authorization-context :as authorization-context]
            [teet.authorization.authorization-check :as authorization-check]))

(defn add-opinion-heading-style
  []
  {:padding-bottom "0.5rem"
   :border-bottom (str "1px solid " theme-colors/border-dark)
   :margin-bottom "1rem"})


(defn owner-opinion-export-form
  [e! project close form-atom]
  (r/with-let [skip-activities #{:activity.name/warranty :activity.name/land-acquisition}
               activities (into []
                                (comp
                                  (mapcat :thk.lifecycle/activities)
                                  (remove (comp skip-activities :activity/name)))
                                (:thk.project/lifecycles
                                  (du/idents->keywords project)))
               form-change (form/update-atom-event form-atom merge)]
    [form/form2 {:e! e!
                 :on-change-event form-change
                 :value @form-atom
                 :cancel-fn close
                 :spec :land-owner-opinion/export}
     [:div {:class (<class common-styles/gray-container-style)}
      [form/field :land-owner-opinion/activity
       [select/form-select {:show-empty-selection? true
                            :empty-selection-label (tr [:land-owner-opinion :choose-activity])
                            :items (mapv #(select-keys % [:db/id :activity/name]) activities)
                            :format-item (comp tr-enum :activity/name)}]]
      [form/field :land-owner-opinion/type
       [select/select-enum {:e! e!
                            :show-empty-selection? true
                            :empty-selection-label (tr [:land-owner-opinion :choose-type])
                            :attribute :land-owner-opinion/type}]]]
     [:div {:class (<class form/form-buttons :flex-end)}
      [buttons/button-secondary {:on-click close
                                 :style {:margin-right "1rem"}}
       (tr [:buttons :cancel])]
      (let [{:land-owner-opinion/keys [activity type] :as form-value} @form-atom]
        [buttons/button-primary {:target "_blank"
                                 :id "view-export-html"
                                 :disabled (or (nil? activity) (nil? type))
                                 :href (common-controller/query-url
                                         :land-owner-opinion/export-opinions
                                         (update form-value :land-owner-opinion/activity :db/id))}
         (tr [:buttons :preview])])]]))

(defn get-activities [project]
  (let [skip-activities #{:activity.name/warranty :activity.name/land-acquisition}]
    (into []
      (comp
        (mapcat :thk.lifecycle/activities)
        (remove (comp skip-activities :activity/name)))
      (:thk.project/lifecycles
        (du/idents->keywords project)))))

(defn- opinion-form-controls [e! activities]
  [Grid {:container true
         :spacing 1}
   [Grid {:item true
          :md 4
          :xs 12}
    [form/field :land-owner-opinion/activity
     [select/form-select {:show-empty-selection? true
                          :items (mapv #(select-keys % [:db/id :activity/name]) activities)
                          :format-item (comp tr-enum :activity/name)}]]]
   [Grid {:item true
          :md 4
          :xs 12}
    [form/field :land-owner-opinion/respondent-name
     [TextField {}]]]
   [Grid {:item true
          :md 4
          :xs 12}
    [form/field :land-owner-opinion/date
     [date-picker/date-input {}]]]
   [Grid {:item true
          :md 4
          :xs 12}
    [form/field :land-owner-opinion/type
     [select/select-enum {:e! e! :attribute :land-owner-opinion/type}]]]
   [Grid {:item true
          :md 4
          :xs 12}
    [form/field :land-owner-opinion/respondent-connection-to-land
     [TextField {}]]]
   [Grid {:item true
          :md 4
          :xs 12}
    [form/field :land-owner-opinion/link-to-response
     [TextField {}]]]
   [Grid {:item true
          :md 6
          :xs 12}
    [form/field :land-owner-opinion/body
     [rich-text-editor/rich-text-field {}]]]
   [Grid {:item true
          :md 6
          :xs 12}
    [form/field :land-owner-opinion/authority-position
     [rich-text-editor/rich-text-field {}]]]
   [Grid {:item true
          :xs 12}
    [form/footer2]]])


(defn land-owner-opinion-export-modal
  [e! project open-atom]
  (r/with-let [close #(reset! open-atom false)
               form-atom (r/atom {})]
    [:<>
     [panels/modal {:max-width :sm
                    :open-atom open-atom
                    :title (tr [:land-owner-opinion :export-modal-title])
                    :on-close close}
      [owner-opinion-export-form e! project close form-atom]]]))

(defn owner-opinion-form
  [e! project target]
  (r/with-let [form-state (r/atom {})
               form-change (form/update-atom-event form-state merge)
               skip-activities #{:activity.name/warranty :activity.name/land-acquisition}
               activities (into []
                                (comp
                                  (mapcat :thk.lifecycle/activities)
                                  (remove (comp skip-activities :activity/name)))
                                (:thk.project/lifecycles
                                  (du/idents->keywords project)))]
    [:div {:style {:padding "0.5rem"
                   :overflow "hidden"}}
     [:div {:class (<class add-opinion-heading-style)}
      [typography/SmallText (tr [:land-owner-opinion :add-new-opinion])]]
     [form/form2 {:e! e!
                  :on-change-event form-change
                  :save-event #(common-controller/->SaveForm
                                 :land-owner-opinion/save-opinion
                                 {:form-data (common-controller/prepare-form-data (form/to-value @form-state))
                                  :project-id (:db/id project)
                                  :land-unit-id target}
                                 (fn [_response]
                                   (fn [e!]
                                     (e! (opinion-controller/->IncreaseCommentCount target))
                                     (e! (opinion-controller/->OpinionFormClose)))))
                  :value @form-state
                  :cancel-event #(opinion-controller/->OpinionFormClose)
                  :spec :land-owner-opinion/form}
      [Grid {:container true
             :spacing 1}
       [Grid {:item true
              :md 4
              :xs 12}
        [form/field :land-owner-opinion/activity
         [select/form-select {:show-empty-selection? true
                              :items (mapv #(select-keys % [:db/id :activity/name]) activities)
                              :format-item (comp tr-enum :activity/name)}]]]
       [Grid {:item true
              :md 4
              :xs 12}
        [form/field :land-owner-opinion/respondent-name
         [TextField {}]]]
       [Grid {:item true
              :md 4
              :xs 12}
        [form/field :land-owner-opinion/date
         [date-picker/date-input {}]]]
       [Grid {:item true
              :md 4
              :xs 12}
        [form/field :land-owner-opinion/type
         [select/select-enum {:e! e! :attribute :land-owner-opinion/type}]]]
       [Grid {:item true
              :md 4
              :xs 12}
        [form/field :land-owner-opinion/respondent-connection-to-land
         [TextField {}]]]
       [Grid {:item true
              :md 4
              :xs 12}
        [form/field :land-owner-opinion/link-to-response
         [TextField {}]]]
       [Grid {:item true
              :md 6
              :xs 12}
        [form/field :land-owner-opinion/body
         [rich-text-editor/rich-text-field {}]]]
       [Grid {:item true
              :md 6
              :xs 12}
        [form/field :land-owner-opinion/authority-position
         [rich-text-editor/rich-text-field {}]]]
       [Grid {:item true
              :xs 12}
        [form/footer2]]]]]))

(defn land-owner-opinion-row-style
  []
  ^{:pseudo {:last-of-type {:border-bottom "1px solid"
                            :border-bottom-color theme-colors/border-dark}}}
  {:border-top "1px solid"
   :border-color theme-colors/border-dark
   :padding "1rem 1rem 1rem 0"})

(defn opinion-view-container
  [{:keys [content text-color open? heading heading-button on-toggle-open edit-atom]} bg-color]
  (r/with-let [open? (r/atom open?)
               toggle-open! #(do
                               (when on-toggle-open
                                 (on-toggle-open))
                               (.stopPropagation %)
                               (swap! open? not))]
    [:div {:class (<class common/collapsable-heading-container bg-color text-color)}
     [:div
      [:div {:class [(<class owner-opinion-style/opinion-container-heading-box)]}
       [:div {:style {:flex-grow 1
                      :text-align :start}}
        heading]
       (when (and heading-button @open?)
         [:div {:style {:flex-grow 0}
                :on-click (fn [e]
                            (.stopPropagation e))}
          heading-button])
       [:div {:style {:margin-left "1rem"}}
        (when (not @edit-atom)
          [buttons/button-primary
            {:size :small
             :on-click toggle-open!}
            [(if @open? icons/hardware-keyboard-arrow-up icons/hardware-keyboard-arrow-down)]
            (if @open? (tr [:buttons :close]) (tr [:buttons :open]))])]]]
     (when content
       [Collapse {:in @open?
                  :mount-on-enter true}
        [:div
         content]])]))

(defn get-activity-name [opinion]
  (let [activity-name (get-in opinion [:land-owner-opinion/activity :activity/name])]
    (tr-enum activity-name)))

(defn get-activity-type [opinion]
  (let [activity-type (get-in opinion [:land-owner-opinion/type])]
    (tr-enum activity-type)))

(defn row-item-style
  []
  {:margin-right "0.5rem"
   :margin-bottom "1rem"})

(defn opinion-list-header-row
  "[[data-title data-value is-bold is-label-hidden]...]"
  [data]
  [:div {:class (<class common-styles/flex-row-wrap)}
   (doall
     (for [{:keys [label item is-bold is-label-hidden] :as row} data
           :when row]
       ^{:key (str item "-" label)}
       [:div
        {:class [(<class row-item-style)]}
        (if (true? is-label-hidden)
          [typography/HiddenText label]
          (if (true? is-bold)
            [typography/SectionHeading label]
            [typography/Text label]))
        [:div item]]))])

(defn owner-opinion-heading [opinion]
  [opinion-list-header-row
   [{:label (:land-owner-opinion/respondent-name opinion)
     :item (get-activity-name opinion)
     :is-bold true
     :is-label-hidden false}
    {:label "."
     :item "/"
     :is-bold false
     :is-label-hidden true}
    (if (clojure.string/blank? (:land-owner-opinion/respondent-connection-to-land opinion))
      {:label "."
       :item (get-activity-type opinion)
       :is-bold false
       :is-label-hidden true}
      {:label (:land-owner-opinion/respondent-connection-to-land opinion)
       :item (get-activity-type opinion)
       :is-bold false
       :is-label-hidden false})]])

(defn owner-opinion-edit-form [e! form-state close-form-and-refresh project target close-event]
  (r/with-let [form-change (form/update-atom-event form-state merge)
               activities (get-activities project)
               decrease-opinions-count (fn [_response]
                                         (fn [e!]
                                           (e! (opinion-controller/->DecreaseCommentCount target))
                                           (close-form-and-refresh)))]
    [:<>
     [form/form2 {:e! e!
                  :on-change-event form-change
                  :save-event #(common-controller/->SaveFormWithConfirmation
                                 :land-owner-opinion/save-opinion
                                 {:form-data (common-controller/prepare-form-data
                                               (form/to-value @form-state))
                                  :project-id (:db/id project)
                                  :land-unit-id target}
                                 close-form-and-refresh
                                 (tr [:land-owner-opinion :update :success-message]))
                  :value @form-state
                  :cancel-event close-event
                  :delete (common-controller/->SaveFormWithConfirmation
                            :land-owner-opinion/delete-opinion
                            {:db/id (:db/id @form-state)}
                            decrease-opinions-count
                            (tr [:land-owner-opinion :delete :success-message]))
                  :delete-message (tr [:land-owner-opinion :delete :confirmation-text])
                  :delete-cancel-button-text (tr [:land-owner-opinion :delete :cancel-text])
                  :spec :land-owner-opinion/form
                  :id (str "owner-opinion-" (:db/id @form-state))}
      (opinion-form-controls e! activities)]]))

(defn opinion-content [e! {id :db/id
                           body :land-owner-opinion/body
                           authority-position :land-owner-opinion/authority-position
                           response-date :land-owner-opinion/date
                           link-to-response :land-owner-opinion/link-to-response
                           creator :meta/creator
                           modifier :meta/modifier :as opinion}
                       edit-right? editing?]
   [:div {:id (str "opinion-" id)}
    (when (not editing?)
      [Grid {:container true
             :spacing 2}
       [Grid {:item true
              :md 6
              :xs 12}
        [typography/TextBold (tr [:fields :land-owner-opinion/date])]]
       [Grid {:item true
              :md 6
              :xs 12}
        [typography/SmallText (tr [:fields :land-owner-opinion/link-to-response])]]
       [Grid {:item true
              :md 6
              :xs 12}
        [typography/TextBold (format/date response-date)]]
       [Grid {:item true
              :md 6
              :xs 12}
        [typography/SmallText [:a {:target :_blank
                                   :href link-to-response} link-to-response]]]
       [Grid {:item true
              :md 6
              :xs 12}
        [typography/Heading4 (tr [:fields :land-owner-opinion/body])]]
       [Grid {:item true
              :md 6
              :xs 12}
        [Grid {:container true
               :spacing 0
               :direction :row
               :align :flex-end}
         [Grid {:item true
                :md 6
                :xs 12}
          [typography/Heading4 (tr [:fields :land-owner-opinion/authority-position])]]
         [Grid {:item true
                :md 6
                :xs 12}
          [typography/Heading5 (if modifier
                                      (str (:user/given-name modifier) " " (:user/family-name modifier))
                                      (str (:user/given-name creator) " " (:user/family-name creator)))]]]]
       [Grid {:item true
              :md 6
              :xs 12}
        [rich-text-editor/display-markdown body]]
       [Grid {:item true
              :md 6
              :xs 12}
        [rich-text-editor/display-markdown authority-position]]])])

(defn- get-opinion-data-for-update
  "Select updatable data from opinion and transform activity enum to key word to be selectable"
  [opinion]
  (->
    (select-keys opinion
      [:db/id :land-owner-opinion/activity :land-owner-opinion/body
       :land-owner-opinion/type :land-owner-opinion/date
       :land-owner-opinion/respondent-connection-to-land
       :land-owner-opinion/authority-position :land-owner-opinion/respondent-name
       :land-owner-opinion/link-to-response
       :meta/creator :meta/modifier])
    (update-in [:land-owner-opinion/activity :activity/name] du/enum->kw)))

(defn owner-opinion-details
  "Contains Opinion details update form and \"Edit\" button to show or hide it"
  [e! {:keys [edit-rights?]} {id :db/id :as opinion} project target refresh!]
  (r/with-let [[pfrom pto] (common/portal)
               edit-open-atom (r/atom false)
               close-form-and-refresh (fn [_] (reset! edit-open-atom false) (refresh!))
               form-data (r/atom (get-opinion-data-for-update opinion))]
    [:div
     ;;[:span (pr-str opinion)]
     [opinion-view-container
      {:heading (if (not @edit-open-atom)
                  [owner-opinion-heading opinion]
                  [typography/SmallText (tr [:land-owner-opinion :edit-opinion])])
       :open? false
       :edit-atom edit-open-atom
       :heading-button [authorization-check/when-authorized :land-owner-opinion/save-opinion project
                        [form/form-container-button
                         {:form-component [owner-opinion-edit-form e! form-data
                                           close-form-and-refresh project target]
                          :container pfrom
                          :open-atom edit-open-atom
                          :id (str "edit-opinion-" id)
                          :form-value form-data
                          :button-component [buttons/button-secondary {:size :small}
                                             (tr [:buttons :edit])]}]]
       :content
       [:<>
        [pto]
        [opinion-content e!
         (get-opinion-data-for-update opinion) edit-rights? @edit-open-atom]]}]]))

(defn owner-opinion-row
  [e! project target refresh! opinion rights]
  (let [authorization {:edit-rights? (get rights :edit-opinion)
                       :review-rights? (get rights :review-opinion)}]
    [:div {:class (<class land-owner-opinion-row-style)}
     [owner-opinion-details e! authorization opinion project target refresh!]]))

(defn owner-opinions-list
  [e! project unit target refresh! opinions]
  (let [l-address (:L_AADRESS unit)
        purpose (:SIHT1 unit)]
    [:div
     [authorization-check/when-authorized :land-owner-opinion/save-opinion project
      [buttons/button-primary {:class (<class common-styles/margin-bottom 3)
                               :on-click #(e! (opinion-controller/->OpinionFormOpen))
                               :start-icon (r/as-element [icons/content-add])}
       (tr [:unit-modal-page :add-new-owner-opinion])]]
     [:div
      [:div {:class (<class common-styles/padding-bottom 1)}
       [typography/TextBold {:style {:display :inline}}
        (tr [:land-owner-opinion :opinions])]
       [typography/SmallText {:style {:display :inline}}
        " " (str l-address " (" purpose ") " target)]]]
     (if (empty? opinions)
       [:div
        [:p (tr [:land :no-owners-opinions])]]
       (->> opinions
         (sort-by :meta/created-at)
         reverse
         (map (fn [opinion]
                ^{:key (:db/id opinion)}
                [authorization-context/consume
                 [owner-opinion-row e! project target refresh! opinion]]))))]))

(defn owner-opinions-unit-modal
  [{:keys [e! estate-info target app project]}]
  (let [unit (land-controller/get-unit-by-teet-id project target)
               new-opinion? (= (get-in app [:query :modal-new-opinion]) "true")]
    (r/with-let [refresh (r/atom nil)]
      [:div
       (if new-opinion?
         [owner-opinion-form e! project target]
         [query/query {:e! e!
                       :query :land-owner-opinion/fetch-opinions
                       :args {:project-id (:db/id project)
                              :land-unit-id target}
                       :refresh @refresh
                       :simple-view [owner-opinions-list e! project unit target
                                     #(reset! refresh (.getTime (js/Date.)))]}])])))
