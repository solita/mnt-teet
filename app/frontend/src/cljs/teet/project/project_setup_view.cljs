(ns teet.project.project-setup-view
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.localization :refer [tr tr-tree]]
            [teet.project.project-controller :as project-controller]
            [teet.project.project-style :as project-style]
            [teet.ui.buttons :as buttons]
            [teet.ui.container :as container]
            [teet.ui.form :as form]
            [teet.ui.icons :as icons]
            [teet.ui.material-ui :refer [ButtonBase Checkbox Collapse FormControlLabel]]
            [teet.ui.select :as select]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.typography :as typography]))

(defn initialization-form-footer [{:keys [cancel validate disabled?]}]
  [:div {:class (<class project-style/wizard-footer)}
   ;; TODO this should be a text button and cancel
   [typography/Text "Skip setup"]
   [buttons/button-primary
    {:on-click validate
     :type     :submit
     :disabled disabled?}
    "Next"]])

(defn project-setup-basic-information-form
  [e! project]
  (e! (project-controller/->UpdateBasicInformationForm
       {:thk.project/project-name (:thk.project/name project)}))
  (fn [e! project]
    [:<>
     [:div {:class (<class project-style/initialization-form-wrapper)}
      [form/form {:e!              e!
                  :value           (:basic-information-form project)
                  :on-change-event project-controller/->UpdateBasicInformationForm
                  :save-event      project-controller/->SaveBasicInformation
                  :class (<class project-style/wizard-form)
                  :footer initialization-form-footer}

       ^{:attribute :thk.project/project-name}
       [TextField {:full-width true :variant :outlined}]

       ^{:attribute :thk.project/owner}
       [select/select-user {:e! e!}]

       ^{:attribute :thk.project/manager}
       [select/select-user {:e! e!}]]]]))

(defn- restrictions-check-group [restrictions checked-restrictions toggle-restriction]
  [:div
   (doall
    (for [{:keys [voond id] :as restriction} (sort-by :voond restrictions)
          :let [checked? (boolean (checked-restrictions id))]]
      ^{:key id}
      [FormControlLabel
       {:control (r/as-element
                  [Checkbox {:color :primary
                             :checked checked?
                             :on-change (r/partial toggle-restriction id)}])
        :label voond}]))])

(defn restrictions-listing
  [e! {:keys [restrictions checked-restrictions toggle-restriction]}]
  (r/with-let [open-types (r/atom #{})
               restrictions-by-type (group-by :type restrictions)]
    [:<>
     (doall
      (for [[group restrictions] restrictions-by-type
            :let [group-checked (into #{}
                                      (comp
                                       (map :id)
                                       (filter checked-restrictions))
                                      restrictions)]]
        ^{:key group}
        [container/collapsible-container {:on-toggle (fn [_]
                                                       (swap! open-types #(if (% group)
                                                                            (disj % group)
                                                                            (conj % group))))
                                          :open? (@open-types group)}
         group
         [restrictions-check-group restrictions group-checked toggle-restriction]]))]))

(defn project-setup-restrictions-form [e! _]
  (e! (project-controller/->FetchRestrictions))
  (fn [e! {:keys [restriction-candidates checked-restrictions] :as project}]
    (when restriction-candidates
      [restrictions-listing e! {:restrictions restriction-candidates
                                :checked-restrictions (or checked-restrictions #{})
                                :toggle-restriction (e! project-controller/->ToggleRestriction)}])))

(defn project-setup-cadastral-units-form [e! project]
  [:div "Tada"])

(defn project-setup-activities-form [e! project]
  [:div "Tada"])

(defn project-setup-wizard [e! project step]
  (let [[step label component]
        (case step
          "basic-information" [1 :basic-information [project-setup-basic-information-form e! project]]
          "restrictions" [2 :restrictions [project-setup-restrictions-form e! project]]
          "cadastral-units" [3 :cadastral-units [project-setup-cadastral-units-form e! project]]
          "activities" [4 :activities [project-setup-activities-form e! project]])]
    [:<>
     [:div {:class (<class project-style/wizard-header)}
      [:div {:class (<class project-style/wizard-header-step-info)}
       [typography/Text {:color :textSecondary}
        (tr [:project :wizard :project-setup])]
       [typography/Text {:color :textSecondary}
        (tr [:project :wizard :step-of] {:current step :total 4})]]
      [typography/Heading2 (tr [:project :wizard label])]]
     component]))
