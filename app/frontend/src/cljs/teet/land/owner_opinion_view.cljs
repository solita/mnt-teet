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
            [teet.util.date :as date]
            [teet.ui.authorization-context :as authorization-context]))

(defn add-opinion-heading-style
  []
  {:padding-bottom "0.5rem"
   :border-bottom (str "1px solid " theme-colors/border-dark)
   :margin-bottom "1rem"})

(defn get-activities [project]
  (let [skip-activities #{:activity.name/warranty :activity.name/land-acquisition}]
    (into []
      (comp
        (mapcat :thk.lifecycle/activities)
        (remove (comp skip-activities :activity/name)))
      (:thk.project/lifecycles
        (du/idents->keywords project)))))

(defn owner-opinion-form
  [e! project target]
  (r/with-let [form-state (r/atom {})
               form-change (form/update-atom-event form-state merge)
               activities (get-activities project)]
    [:div {:style {:padding "0.5rem"
                   :overflow "hidden"}}
     [:div {:class (<class add-opinion-heading-style)}
      [typography/SmallText (tr [:land-owner-opinions :add-new-opinion])]]
     [form/form2 {:e! e!
                  :on-change-event form-change
                  :save-event #(common-controller/->SaveForm
                                 :land-owner-opinion/save-opinion
                                 {:form-data (common-controller/prepare-form-data (form/to-value @form-state))
                                  :project-id (:db/id project)
                                  :land-unit-id target}
                                 (fn [_response]
                                   (fn [e!]
                                     (e! (opinion-controller/->OpinionFormClose))
                                     (e! (opinion-controller/->IncreaseCommentCount target)))))
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
   :padding "1rem"})

(def open? (r/atom false))

(defn opinion-view-container
  [{:keys [content text-color open? heading heading-button on-toggle-open]
    :or {open? false}} bg-color]
  (r/with-let [open? (r/atom open?)
               toggle-open! #(do
                               (when on-toggle-open
                                 (on-toggle-open))
                               (.stopPropagation %)
                               (swap! open? not))]
    [:div {:class (<class common/hierarchical-heading-container2 bg-color text-color @open?)}
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
        [buttons/button-primary
         {:size :small
          :on-click toggle-open!}
         [(if @open? icons/hardware-keyboard-arrow-up icons/hardware-keyboard-arrow-down)]]
        ]]]
     (when content
       [Collapse {:in @open?
                  :mount-on-enter true}
        [:div {:style {:padding "1rem"}}
         content]])])
  )

(defn owner-opinion-heading []
  ;; TODO: implement
  )

(defn opinion-form [e! form project target close-event]
  (r/with-let [form-state
               (r/atom (select-keys
                         @form [:db/id
                                :land-owner-opinion/body
                                :land-owner-opinion/type
                                :land-owner-opinion/date
                                :land-owner-opinion/respondent-connection-to-land
                                :land-owner-opinion/authority-position
                                :land-owner-opinion/respondent-name
                                :land-owner-opinion/link-to-response]))
               form-change (form/update-atom-event form-state merge)
               activities (get-activities project)]
    (println @form-state)
    [:<>
     [form/form2 {:e! e!
                  :on-change-event form-change
                  :save-event #(common-controller/->SaveForm
                                 :land-owner-opinion/save-opinion
                                 {:form-data (common-controller/prepare-form-data (form/to-value @form-state))
                                  :project-id (:db/id project)
                                  :land-unit-id target}
                                 (fn [_response]
                                   (fn [e!]
                                     (e! (opinion-controller/->OpinionFormClose)))))
                  :value @form-state
                  :cancel-event close-event
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

(defn opinion-content [e! {id :db/id
                           body :land-owner-opinion/body
                           authority-position :land-owner-opinion/authority-position}
                       edit-right? editing?]
  [:div {:id (str "opinion-" id)}
   (if (not editing?)
     [:div
      [:div [rich-text-editor/display-markdown body]]
      [:div [rich-text-editor/display-markdown authority-position]]]
     (println "opinion-content is hidden"))])

(defn owner-opinion-details
  [e! {:keys [edit-rights?]}
   {id :db/id
    :land-owner-opinion/keys [respondent-connection-to-land body authority-position]
    :as opinion}
   project
   target]
  (r/with-let [seen-at (date/start-of-today)
               [pfrom pto] (common/portal)
               edit-open-atom (r/atom false)
               form-data (r/atom opinion)]
    [opinion-view-container
     {:heading [owner-opinion-heading seen-at opinion]
      :on-toggle-open #(e! (println "Opinion view toggled"))
      :open? true
      :heading-button [form/form-container-button
                       {:form-component [opinion-form e! form-data project target]
                        :container pfrom
                        :open-atom edit-open-atom
                        :id (str "edit-opinion-" id)
                        :form-value (select-keys @form-data [:db/id
                                                             :land-owner-opinion/body
                                                             :land-owner-opinion/type
                                                             :land-owner-opinion/date
                                                             :land-owner-opinion/respondent-connection-to-land
                                                             :land-owner-opinion/authority-position
                                                             :land-owner-opinion/respondent-name
                                                             :land-owner-opinion/link-to-response])
                        :button-component [buttons/button-secondary {:size :small}
                                           (tr [:buttons :edit])]}]
      :content
      [:<>
       [pto]
       [opinion-content e! @form-data edit-rights? @edit-open-atom]]}]))

(defn owner-opinion-row
  [e! project target opinion rights]
  (let [authorization {:edit-rights? (get rights :edit-opinion)
                       :review-rights? (get rights :review-opinion)}]
    [:div {:class (<class land-owner-opinion-row-style)}
     [:div
      [typography/TextBold {:style {:display :inline}}
       (:land-owner-opinion/respondent-name opinion)]
      [typography/SmallText {:style {:padding-left "0.25rem"
                                     :display :inline}}
       (:land-owner-opinion/respondent-connection-to-land opinion)]
      [owner-opinion-details e! authorization opinion project target]]]))

(defn owner-opinions-list
  [e! project unit target opinions]
  (let [l-address (:L_AADRESS unit)
        purpose (:SIHT1 unit)]
    [:div
     [buttons/button-primary {:class (<class common-styles/margin-bottom 1)
                              :on-click #(e! (opinion-controller/->OpinionFormOpen))
                              :start-icon (r/as-element [icons/content-add])}
      (tr [:unit-modal-page :add-new-owner-opinion])]
     [:div
      [:div {:class (<class common-styles/padding-bottom 1)}
       [typography/TextBold {:style {:display :inline}}
        (tr [:land-owner-opinions :opinions])]
       [typography/SmallText {:style {:display :inline}}
        " " (str l-address " (" purpose ") " target)]]]
     (if (empty? opinions)
       [:div
        [:p (tr [:land :no-owners-opinions])]]
       (map
         (fn [opinion]
           ^{:key (:db/id opinion)}
           [authorization-context/consume
            [owner-opinion-row e! project target opinion]])
         opinions))]))

(defn owner-opinions-unit-modal
  [{:keys [e! estate-info target app project]}]
  (let [unit (land-controller/get-unit-by-teet-id project target)
        new-opinion? (= (get-in app [:query :modal-new-opinion]) "true")]
    [:div
     (if new-opinion?
       [owner-opinion-form e! project target]
       [query/query {:e! e!
                     :query :land-owner-opinion/fetch-opinions
                     :args {:project-id (:db/id project)
                            :land-unit-id target}
                     :simple-view [owner-opinions-list e! project unit target]}])]))
