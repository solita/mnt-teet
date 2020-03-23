(ns teet.project.project-setup-view
  (:require [goog.string :as gstring]
            [goog.string.format]
            [herb.core :refer [<class]]
            [reagent.core :as r]
            [teet.localization :refer [tr]]
            [teet.project.project-controller :as project-controller]
            [teet.project.project-model :as project-model]
            [teet.project.project-specs]
            [teet.project.project-style :as project-style]
            [teet.ui.buttons :as buttons]
            [teet.ui.container :as container]
            [teet.ui.form :as form]
            [teet.ui.icons :as icons]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.material-ui :refer [Grid Link]]
            [teet.ui.select :as select]
            [teet.ui.text-field :refer [TextField]]
            [teet.ui.material-ui :refer [RadioGroup FormControl FormControlLabel Radio]]
            [teet.ui.typography :as typography]
            [teet.util.collection :as cu]
            [teet.road.road-model :as road-model]
            [teet.ui.skeleton :as skeleton]
            [teet.common.common-styles :as common-styles]
            [teet.ui.panels :as panels]
            [teet.log :as log]))

(declare project-setup-steps)

(defn original-name-adornment [e! {:thk.project/keys [name] :as _project}]
  [:div {:style {:padding-top "6px"
                 :display     :flex}}
   [typography/Text {:style {:margin-right "6px"}} "Road name:"]
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
(defn num-range [{:keys [value on-change start-label end-label required spacing
                         reset-start reset-end
                         min-value max-value
                         error error-text]
                  :or   {spacing 3}}]
  (let [[start end] value]
    [Grid {:container true
           :spacing   spacing}
     [Grid {:item true
            :xs   6}
      [TextField (merge {:label     start-label
                         :on-change (fn [e]
                                      (on-change [(-> e .-target .-value) end]))
                         :value     start
                         ;; :type      :number
                         :step      "0.001"
                         :error     (num-range-error error value start min-value max-value)
                         :required  required}
                        (when reset-start
                          {:input-button-icon icons/av-replay
                           :input-button-click (reset-start value)}))]]
     [Grid {:item true
            :xs   6}
      [TextField (merge {:label     end-label
                         :on-change (fn [e]
                                      (on-change [start (-> e .-target .-value)]))
                         :value     end
                         ;; :type      :number
                         :step      "0.001"
                         :error     (num-range-error error value end min-value max-value)
                         :required  required}
                        (when reset-end
                          {:input-button-icon icons/av-replay
                           :input-button-click (reset-end value)}))]]
     (when (and error error-text)
       [Grid {:item true
              :xs 12}
        [:p {:class (<class common-styles/input-error-text-style)}
         error-text]])]))

(defn km-range-changed? [project]
  (let [{:thk.project/keys [start-m end-m]} project
        [start-km end-km] (-> project :basic-information-form :thk.project/km-range)
        form-start-m (long (* 1000 (js/parseFloat start-km)))
        form-end-m (long (* 1000 (js/parseFloat end-km)))]
    (or (not= form-start-m start-m)
        (not= form-end-m end-m))))

(defn format-range [km-range]
  (mapv #(gstring/format "%.3f" %) km-range))

(defn reset-range-value
  "Returns a function that, when called, resets the basic information
  forms original value for either start or end km while retaining the
  current value of the other"
  [e! project selector [current-start-km-str current-end-km-str]]
  (let [[orig-start-km-str orig-end-km-str] (-> project
                                                (project-model/get-column :thk.project/km-range)
                                                format-range)]
    (case selector
      :start (fn [_]
               (e! (project-controller/->UpdateBasicInformationForm {:thk.project/km-range [orig-start-km-str current-end-km-str]})))
      :end (fn [_]
             (e! (project-controller/->UpdateBasicInformationForm {:thk.project/km-range [current-start-km-str orig-end-km-str]}))))))

(defn project-setup-basic-information-form
  [e! project {:keys [step-label] :as step} _map]
  (when-not (:basic-information-form project)
    (e! (project-controller/->InitializeBasicInformationForm
          (cu/without-nils {:thk.project/project-name (:thk.project/name project)
                            :thk.project/km-range     (-> project
                                                          (project-model/get-column :thk.project/effective-km-range)
                                                          format-range)
                            :thk.project/owner        (:thk.project/owner project)
                            :thk.project/manager      (:thk.project/manager project)}))))
  (fn [e! {form :basic-information-form :as project} _]
    [:div {:class (<class project-style/initialization-form-wrapper)}
     [form/form {:e!              e!
                 :value           form
                 :on-change-event project-controller/->UpdateBasicInformationForm
                 :save-event      (project-controller/navigate-to-next-step-event project-setup-steps step)
                 :class           (<class project-style/wizard-form)
                 :spec            :project/initialization-form
                 :footer          nil
                 :id              step-label}

      ^{:attribute :thk.project/project-name
        :adornment [original-name-adornment e! project]}
      [TextField {:full-width true :variant :outlined}]

      (let [min-km (some-> project :road-info :start-m road-model/m->km)
            max-km (some-> project :road-info :end-m road-model/m->km)]
        ^{:xs 12 :attribute :thk.project/km-range
          :validate (fn [[start end :as value]]
                      (when (or (num-range-error nil value start min-km max-km)
                                (num-range-error nil value end min-km max-km))
                        (str "Valid km range: " min-km "km - " max-km "km")))}
        [num-range {:start-label "Start km"
                    :end-label   "End km"
                    :min-value min-km
                    :max-value max-km
                    :reset-start (partial reset-range-value e! project :start)
                    :reset-end   (partial reset-range-value e! project :end)}])

      ;; FIXME: The map should also reflect the changed range
      (when (km-range-changed? project)
        ^{:xs 12 :attribute :thk.project/m-range-change-reason}
        [TextField {:multiline true
                    :rows      3}])

      ^{:attribute :thk.project/owner}
      [select/select-user {:e! e!}]

      ^{:attribute :thk.project/manager}
      [select/select-user {:e! e!}]]]))

(defn fetching-features-skeletons
  [n]
  [:<>
   (doall
     (for [y (range n)]
       ^{:key y}
       [skeleton/skeleton {:parent-style (skeleton/restriction-skeleton-style)}]))])

(defn draw-selection [e! related-feature-type features draw-selection-features]
  (r/with-let [select? (r/atom true)]
    [:<>
     (when (seq draw-selection-features)
       [panels/modal {:title (str (count draw-selection-features) " "
                                  (case related-feature-type
                                    :restrictions (tr [:project :restrictions-tab])
                                    :cadastral-units (tr [:project :cadastral-units-tab])))
                      :on-close (e! project-controller/->DrawSelectionCancel)
                      :open-atom (r/wrap true :_)}
        [:div
         [select/radio {:value @select?
                        :on-change #(reset! select? %)
                        :items [true false]
                        :format-item {true (tr [:project :draw-selection :select])
                                      false (tr [:project :draw-selection :deselect])}}]
         [:div {:style {:display :flex
                        :justify-content :space-between}}
          [buttons/button-secondary {:on-click (e! project-controller/->DrawSelectionCancel)}
           (tr [:buttons :cancel])]
          [buttons/button-primary {:on-click (e! project-controller/->DrawSelectionConfirm
                                                 @select? related-feature-type)}
           (tr [:buttons :confirm])]]]])
     [Link {:on-click (e! project-controller/->DrawSelectionOnMap related-feature-type features)}
      (tr [:project :draw-selection :link])]]))

(defn restrictions-listing
  [e! open-types _road-buffer-meters {:keys [restrictions loading? checked-restrictions toggle-restriction
                                             draw-selection-features
                                             on-mouse-enter on-mouse-leave]}]
  [:<>
   (if loading?
     [fetching-features-skeletons 10]
     (let [restrictions-by-type (group-by :VOOND restrictions)]
       [:<>
        [container/collapsible-container {:on-toggle (e! project-controller/->ToggleSelectedCategory)
                                          :open? (open-types :selected)}
         (str (count checked-restrictions) " selected")
         (when (not-empty checked-restrictions)
           [itemlist/checkbox-list
            (for [restriction (sort-by (juxt :VOOND :teet-id) checked-restrictions)]
              {:id (:teet-id restriction)
               :checked? true
               :value (:VOOND restriction)
               :on-change (r/partial toggle-restriction restriction)
               :on-mouse-enter (e! project-controller/->FeatureMouseOvers "selected-restrictions"
                                   true restriction)
               :on-mouse-leave (e! project-controller/->FeatureMouseOvers "selected-restrictions"
                                   false restriction)})])]
        [:<>
         [typography/Heading2 {:style {:padding "1rem"}} "Found related features:"]
         (doall
           (for [[group restrictions] restrictions-by-type
                 :let [group-checked (into #{}
                                           (filter checked-restrictions restrictions))]]
             ^{:key group}
             [container/collapsible-container
              {:on-toggle (fn [_]
                            (e! (project-controller/->ToggleRestrictionCategory
                                 (into #{}
                                       (mapv :teet-id restrictions))
                                 group)))
               :open? (open-types group)
               :side-component [typography/SmallText (tr [:project :wizard :selected-count]
                                                         {:selected (count group-checked)
                                                          :total (count restrictions)})]}
              group
              [itemlist/checkbox-list
               {:on-select-all #(e! (project-controller/->SelectRestrictions (set restrictions)))
                :on-deselect-all #(e! (project-controller/->DeselectRestrictions (set restrictions)))
                :actions (list [draw-selection e! :restrictions restrictions draw-selection-features])}
               (for [restriction (sort-by (juxt :VOOND :teet-id) restrictions)
                     :let [checked? (boolean (group-checked restriction))]]
                 (merge {:id (:teet-id restriction)
                         :checked? checked?
                         :value (:VOOND restriction)
                         :on-change (r/partial toggle-restriction restriction)}
                        (when on-mouse-enter
                          {:on-mouse-enter (r/partial on-mouse-enter restriction)})
                        (when on-mouse-leave
                          {:on-mouse-leave (r/partial on-mouse-leave restriction)})))]]))]]))])

(defn cadastral-units-listing
  [e! _road-buffer-meters {:keys [loading? cadastral-units checked-cadastral-units
                                  draw-selection-features
                                  toggle-cadastral-unit on-mouse-enter on-mouse-leave]}]
  (r/with-let [open-types (r/atom #{})]
    (if loading?
      [fetching-features-skeletons 10]
      [:<>
       [container/collapsible-container {:on-toggle (fn [_]
                                                      (swap! open-types #(if (% :selected)
                                                                           (disj % :selected)
                                                                           (conj % :selected))))
                                         :open? (@open-types :selected)}
        (str (count checked-cadastral-units) " selected")
        (when (not-empty checked-cadastral-units)
          [itemlist/checkbox-list
           (for [cadastral-unit (sort-by (juxt :VOOND :teet-id) checked-cadastral-units)]
             {:id (:teet-id cadastral-unit)
              :checked? true
              :value (str (:L_AADRESS cadastral-unit) " " (:TUNNUS cadastral-unit))
              :on-change (r/partial toggle-cadastral-unit cadastral-unit)
              :on-mouse-enter (e! project-controller/->FeatureMouseOvers "selected-cadastral-units" true cadastral-unit)
              :on-mouse-leave (e! project-controller/->FeatureMouseOvers "selected-cadastral-units" false cadastral-unit)})])]

       [typography/Heading2 {:style {:padding "1rem"}} "Found related features: "]

       [:div {:style {:margin-top "1rem"}}
        [itemlist/checkbox-list
         {:on-select-all #(e! (project-controller/->SelectCadastralUnits (set cadastral-units)))
          :on-deselect-all #(e! (project-controller/->DeselectCadastralUnits (set cadastral-units)))
          :actions (list [draw-selection e! :cadastral-units cadastral-units draw-selection-features])}
         (doall
           (for [cadastral-unit (sort-by (juxt :VOOND :teet-id) cadastral-units)
                 :let [checked? (boolean (checked-cadastral-units cadastral-unit))]]
             {:id (:teet-id cadastral-unit)
              :checked? checked?
              :value (str (:L_AADRESS cadastral-unit) " " (:TUNNUS cadastral-unit))
              :on-change (r/partial toggle-cadastral-unit cadastral-unit)
              :on-mouse-enter (r/partial on-mouse-enter cadastral-unit)
              :on-mouse-leave (r/partial on-mouse-leave cadastral-unit)}))]]])))

(defn project-setup-restrictions-form [e! _project step {:keys [road-buffer-meters] :as _map}]
  (e! (project-controller/->FetchRelatedCandidates road-buffer-meters step))
  (fn [e! {:keys [checked-restrictions open-types feature-candidates draw-selection-features]
           :or {open-types #{}} :as _project} {step-label :step-label :as step} _map]
    (let [{:keys [loading? restriction-candidates]} feature-candidates]
      [:form {:id step-label
              :on-submit (let [step-constructor (project-controller/navigate-to-next-step-event project-setup-steps step)]
                           #(let [event (step-constructor)]
                              (e! event)
                              (.preventDefault %)))}
       [restrictions-listing e!
        open-types
        road-buffer-meters
        {:restrictions restriction-candidates
         :draw-selection-features draw-selection-features
         :loading? loading?
         :checked-restrictions (or checked-restrictions #{})
         :toggle-restriction (e! project-controller/->ToggleRestriction)
         :on-mouse-enter (e! project-controller/->FeatureMouseOvers "related-restriction-candidates" true)
         :on-mouse-leave (e! project-controller/->FeatureMouseOvers "related-restriction-candidates" false)}]])))

(defn project-setup-cadastral-units-form [e! _project step {:keys [road-buffer-meters] :as _map}]
  (e! (project-controller/->FetchRelatedCandidates road-buffer-meters step))
  (fn [e!
       {:keys [checked-cadastral-units feature-candidates draw-selection-features]}
       {step-label :step-label :as step}
       _map]
    (let [{:keys [loading? cadastral-candidates]} feature-candidates]
      [:form {:id step-label
              :on-submit (e! (project-controller/navigate-to-next-step-event project-setup-steps step))}
       [cadastral-units-listing e!
        road-buffer-meters
        {:cadastral-units cadastral-candidates
         :loading? loading?
         :draw-selection-features draw-selection-features
         :checked-cadastral-units (or checked-cadastral-units #{})
         :toggle-cadastral-unit (e! project-controller/->ToggleCadastralUnit)
         :on-mouse-enter (e! project-controller/->FeatureMouseOvers "related-cadastral-unit-candidates" true)
         :on-mouse-leave (e! project-controller/->FeatureMouseOvers "related-cadastral-unit-candidates" false)}]])))


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
  [:div {:class (<class project-style/project-view-header)}
   [:div {:class (<class project-style/wizard-header-step-info)}
    [typography/Text {:color :textSecondary}
     (tr [:project :wizard :project-setup])]
    [typography/Text {:color :textSecondary}
     (tr [:project :wizard :step-of] {:current step-number
                                      :total   (count project-setup-steps)})]]
   [typography/Heading2 (tr [:project :wizard step-label])]])

(defn setup-wizard-footer [e! {:keys [step-label step-number] :as step} project-id]
  [:div {:class (<class project-style/wizard-footer)}
   (if (> step-number 1)
     [buttons/button-secondary
      {:on-click (e! (project-controller/navigate-to-previous-step-event project-setup-steps step))}
      (tr [:buttons :back])]
     [buttons/button-text
      {:on-click #(e! (project-controller/->SkipProjectSetup project-id))}
      (tr [:project :wizard :skip-setup])])
   [buttons/button-primary {:type :submit
                            :form step-label}
    (if (= step-number (count project-setup-steps))
      (tr [:buttons :save])
      "Next")]])

(defn- step->map-layers [{:keys [step-label]}]
  (get {:basic-information #{:thk-project :thk-project-buffer}
        :restrictions #{:thk-project :thk-project-buffer :related-restrictions}
        :cadastral-units #{:thk-project :related-cadastral-units :thk-project-buffer}}
       step-label))

(defn view-settings [e! app project]
  (let [step (step-info (or (:setup-step project)
                            "basic-information"))]
    {:header      [setup-wizard-header step]
     :body        [(:body step) e! project step (:map app)]
     :footer      [setup-wizard-footer e! step (:thk.project/id project)]
     :map-settings {:geometry-range? true
                    :layers (step->map-layers step)}}))
