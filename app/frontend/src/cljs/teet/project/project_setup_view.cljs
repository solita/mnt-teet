(ns teet.project.project-setup-view
  (:require [goog.string :as gstring]
            [goog.string.format]
            [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.localization :refer [tr tr-tree]]
            [teet.project.project-controller :as project-controller]
            [teet.project.project-style :as project-style]
            [teet.ui.buttons :as buttons]
            [teet.ui.container :as container]
            [teet.ui.form :as form]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.material-ui :refer [Grid]]
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

(defn original-name-adornment [e! {:thk.project/keys [name] :as project}]
  [:div {:style {:padding-top "6px"
                 :display :flex}}
   [typography/Text {:display :inline} "Road name:"]
   [buttons/link-button {:on-click #(e! (project-controller/->UpdateBasicInformationForm {:thk.project/project-name name}))}
    name]])

(defn- nan? [x]
  (not (= x x)))

(defn- num-range-error [error [start end] own-value]
  (or error
      (and own-value (nan? (js/parseFloat own-value)))
      (and start end
           (< (js/parseFloat end)
              (js/parseFloat start)))))

;; FIXME: This is a generic component, move to another namespace
(defn num-range [{:keys [error value on-change start-label end-label required spacing]
                 :or {spacing 3}}]
  (let [[start end] value]
    [Grid {:container true
           :spacing spacing}
     [Grid {:item true
            :xs 6}
      [TextField {:label start-label
                  :on-change (fn [e]
                               (on-change [(-> e .-target .-value) end]))
                  :value start
                  :error (num-range-error error value start)
                  :required required}]]
     [Grid {:item true
            :xs 6}
      [TextField {:label end-label
                  :on-change (fn [e]
                               (on-change [start (-> e .-target .-value)]))
                  :value end
                  :error (num-range-error error value end)
                  :required required}]]]))

(defn km-range-changed? [project]
  (let [{:thk.project/keys [start-m end-m]} project
        [start-km end-km] (-> project :basic-information-form :thk.project/km-range)
        form-start-m (long (* 1000 (js/parseFloat start-km)))
        form-end-m (long (* 1000 (js/parseFloat end-km)))]
    (or (not= form-start-m start-m)
        (not= form-end-m end-m))))

(defn project-setup-basic-information-form
  [e! project]
  (e! (project-controller/->UpdateBasicInformationForm
       {:thk.project/project-name (:thk.project/name project)
        :thk.project/km-range [(gstring/format "%.3f" (/ (:thk.project/start-m project) 1000.0))
                               (gstring/format "%.3f" (/ (:thk.project/end-m project) 1000.0))]}))
  (fn [e! project]
    [:<>
     [:div {:class (<class project-style/initialization-form-wrapper)}
      [form/form {:e!              e!
                  :value           (:basic-information-form project)
                  :on-change-event project-controller/->UpdateBasicInformationForm
                  :save-event      project-controller/->SaveBasicInformation
                  :class (<class project-style/wizard-form)
                  :footer initialization-form-footer}

       ^{:attribute :thk.project/project-name
         :adornment [original-name-adornment e! project]}
       [TextField {:full-width true :variant :outlined}]

       ^{:xs 12 :attribute :thk.project/km-range}
       [num-range {:start-label "Start km"
                   :end-label "End km"}]

       (when (km-range-changed? project)
         ^{:xs 12 :attribute :thk.project/meter-range-changed-reason}
         [TextField {}])

       ^{:attribute :thk.project/owner}
       [select/select-user {:e! e!}]

       ^{:attribute :thk.project/manager}
       [select/select-user {:e! e!}]]]]))

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
         [itemlist/checkbox-list
          (for [{:keys [voond id]} (sort-by :voond restrictions)
                :let [checked? (boolean (group-checked id))]]
            {:checked? checked?
             :value voond
             :on-change (r/partial toggle-restriction id)})]]))]))

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
