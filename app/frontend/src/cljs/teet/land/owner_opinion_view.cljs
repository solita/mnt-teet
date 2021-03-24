(ns teet.land.owner-opinion-view
  (:require [reagent.core :as r]
            [teet.ui.form :as form]
            [teet.ui.text-field :refer [TextField]]
            [teet.localization :refer [tr tr-enum]]
            [teet.ui.select :as select]
            teet.land.owner-opinion-specs
            [teet.ui.material-ui :refer [Collapse Paper Grid]]
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
            [teet.ui.query :as query]
            [teet.theme.theme-colors :as theme-colors]
            [teet.util.datomic :as du]
            [teet.ui.panels :as panels]))

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

(defn owner-opinion-row
  [e! project target opinion]
  [:div {:class (<class land-owner-opinion-row-style)}
   [:div
    [typography/TextBold {:style {:display :inline}}
     (:land-owner-opinion/respondent-name opinion)]
    [typography/SmallText {:style {:padding-left "0.25rem"
                                   :display :inline}}
     (:land-owner-opinion/respondent-connection-to-land opinion)]]])

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
        (tr [:land-owner-opinion :opinions])]
       [typography/SmallText {:style {:display :inline}}
        " " (str l-address " (" purpose ") " target)]]]
     (if (empty? opinions)
       [:div
        [:p (tr [:land :no-owners-opinions])]]
       (map
         (fn [opinion]
           ^{:key (:db/id opinion)}
           [owner-opinion-row e! project target opinion])
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
