(ns teet.project.project-setup-view
  (:require [goog.string :as gstring]
            [goog.string.format]
            [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.localization :refer [tr tr-tree]]
            [teet.project.project-controller :as project-controller]
            [teet.project.project-model :as project-model]
            [teet.project.project-specs]
            [teet.project.project-style :as project-style]
            [teet.ui.buttons :as buttons]
            [teet.ui.container :as container]
            [teet.ui.form :as form]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.material-ui :refer [Grid]]
            [teet.ui.select :as select]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.typography :as typography]
            [teet.road.road-model :as road-model]
            [teet.log :as log]))

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

(defn- num-range-error [error [start end] own-value min-value max-value]
  (let [v (when own-value
            (js/parseFloat own-value))]
    (or error
        (nan? v)
        (and v min-value (< v min-value))
        (and v max-value (> v max-value))
        (and start end
             (< (js/parseFloat end)
                (js/parseFloat start))))))

;; FIXME: This is a generic component, move to another namespace
(defn num-range [{:keys [error value on-change start-label end-label required spacing
                         min-value max-value]
                  :or {spacing 3}
                  :as nr}]
  (log/info "num-range " nr)
  (let [[start end] value]
    [Grid {:container true
           :spacing spacing}
     [Grid {:item true
            :xs 6}
      [TextField {:label start-label
                  :on-change (fn [e]
                               (on-change [(-> e .-target .-value) end]))
                  :value start
                  :type :number
                  :step "0.001"
                  :error (num-range-error error value start min-value max-value)
                  :required required}]]
     [Grid {:item true
            :xs 6}
      [TextField {:label end-label
                  :on-change (fn [e]
                               (on-change [start (-> e .-target .-value)]))
                  :value end
                  :type :number
                  :step "0.001"
                  :error (num-range-error error value end min-value max-value)
                  :required required}]]]))

(defn km-range-changed? [project]
  (let [{:thk.project/keys [start-m end-m]} project
        [start-km end-km] (-> project :basic-information-form :thk.project/km-range)
        form-start-m (long (* 1000 (js/parseFloat start-km)))
        form-end-m (long (* 1000 (js/parseFloat end-km)))]
    (or (not= form-start-m start-m)
        (not= form-end-m end-m))))

(defn- project-km-range [project]
  (mapv #(gstring/format "%.3f" %)
        (project-model/get-column project :thk.project/effective-km-range)))

(defn project-setup-basic-information-form
  [e! project {:keys [step-label]}]
  (e! (project-controller/->UpdateBasicInformationForm
       {:thk.project/project-name (:thk.project/name project)
        :thk.project/km-range (project-km-range project)
        :thk.project/owner (:thk.project/owner project)
        :thk.project/manager (:thk.project/manager project)}))
  (fn [e! {form :basic-information-form :as project}]
    [:div {:class (<class project-style/initialization-form-wrapper)}
     [form/form {:e!              e!
                 :value           form
                 :on-change-event project-controller/->UpdateBasicInformationForm
                 :save-event      project-controller/->SaveBasicInformation
                 :class (<class project-style/wizard-form)
                 :spec :project/initialization-form
                 :footer nil
                 :id step-label}

      ^{:attribute :thk.project/project-name
        :adornment [original-name-adornment e! project]}
      [TextField {:full-width true :variant :outlined}]

      ^{:xs 12 :attribute :thk.project/km-range}
      [num-range {:start-label "Start km"
                  :end-label "End km"
                  :min-value (some-> form :road-info :start_m road-model/m->km)
                  :max-value (some-> form :road-info :end_m road-model/m->km)}]

      ;; FIXME: The map should also reflect the changed range
      (when (km-range-changed? project)
        ^{:xs 12 :attribute :thk.project/meter-range-changed-reason}
        [TextField {:multiline true
                    :rows 3}])

      ^{:attribute :thk.project/owner}
      [select/select-user {:e! e!}]

      ^{:attribute :thk.project/manager}
      [select/select-user {:e! e!}]]]))

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

(defn project-setup-restrictions-form [e! _  _step _step-label]
  (e! (project-controller/->FetchRestrictions))
  (fn [e! {:keys [restriction-candidates checked-restrictions] :as project} _step _step-label]
    (when restriction-candidates
      [restrictions-listing e! {:restrictions restriction-candidates
                                :checked-restrictions (or checked-restrictions #{})
                                :toggle-restriction (e! project-controller/->ToggleRestriction)}])))

(defn project-setup-cadastral-units-form [e! project]
  [:div "Tada"])

(defn project-setup-activities-form [e! project]
  [:div "Tada"])

(def project-setup-steps
  [{:step-label :basic-information
    :body       project-setup-basic-information-form}
   {:step-label :restrictions
    :body       project-setup-restrictions-form}
   {:step-label :cadastral-units
    :body       project-setup-cadastral-units-form}])

(defn step-info [step-name]
  (first (keep-indexed #(when (= (:step-label %2)
                                 (keyword step-name))
                          (assoc %2 :step-number (inc %1)))
                       project-setup-steps)))

(defn setup-wizard-header [{:keys [step-label step-number]}]
  [:div {:class (<class project-style/wizard-header)}
   [:div {:class (<class project-style/wizard-header-step-info)}
    [typography/Text {:color :textSecondary}
     (tr [:project :wizard :project-setup])]
    [typography/Text {:color :textSecondary}
     (tr [:project :wizard :step-of] {:current step-number :total 4})]]
   [typography/Heading2 (tr [:project :wizard step-label])]])

(defn setup-wizard-footer [{:keys [step-label step-number]}]
  [:div {:class (<class project-style/wizard-footer)}
   ;; TODO this should be a text button and cancel
   (if (> step-number 1)
     [buttons/button-secondary
      #_{:on-click back}
      "Back"]
     [buttons/link-button
      "Cancel"])
   [buttons/button-primary {:type :submit
                            :form step-label}
    "Next"]])

(defn project-setup [e!
                     {{step-name :step
                       :or {step-name "basic-information"}}
                      :query
                      :as _app}
                     project]
  (let [step (step-info step-name)]
    {:header [setup-wizard-header step]
     :body [(:body step) e! project step]
     :footer [setup-wizard-footer step]}))
