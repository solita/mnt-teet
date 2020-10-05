(ns teet.project.project-setup-view
  (:require [goog.string :as gstring]
            [goog.string.format]
            [reagent.core :as r]
            [teet.localization :refer [tr]]
            [teet.project.project-controller :as project-controller]
            [teet.project.land-controller :as land-controller]
            [teet.project.project-model :as project-model]
            [teet.project.project-specs]
            [teet.ui.buttons :as buttons]
            [teet.ui.container :as container]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.select :as select]
            [teet.ui.typography :as typography]
            [teet.ui.skeleton :as skeleton]
            [teet.ui.panels :as panels]))

(defn original-name-adornment [e! {:thk.project/keys [name] :as _project}]
  [:div {:style {:padding-top "6px"
                 :display     :flex}}
   [typography/Text {:style {:margin-right "6px"}} (tr [:fields :thk.project/road-name]) ": "]
   [buttons/link-button {:on-click #(e! (project-controller/->UpdateBasicInformationForm {:thk.project/project-name name}))}
    name]])

(defn km-range-changed? [project]
  (let [{:thk.project/keys [start-m end-m custom-end-m custom-start-m]} project
        [start-km end-km] (-> project :basic-information-form :thk.project/km-range)
        form-start-m (long (* 1000 (js/parseFloat start-km)))
        form-end-m (long (* 1000 (js/parseFloat end-km)))]
    (or (not= form-start-m (or custom-start-m start-m))
        (not= form-end-m (or custom-end-m end-m)))))

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

(defn fetching-features-skeletons
  [n]
  [:<>
   (doall
     (for [y (range n)]
       ^{:key y}
       [skeleton/skeleton {:parent-style (skeleton/restriction-skeleton-style)}]))])

(defn draw-selection [e! related-feature-type features draw-selection-features]
  (r/with-let [select? (r/atom true)
               current-open (r/atom false)]
    [:<>
     [panels/modal {:title (str (count draw-selection-features) " "
                                (case related-feature-type
                                  :restrictions (tr [:project :restrictions-tab])
                                  :cadastral-units (tr [:project :cadastral-units-tab])))
                    :on-close #(do
                                 (reset! current-open false)
                                 (e! project-controller/->DrawSelectionCancel))
                    :open-atom (r/wrap (and (not (nil? draw-selection-features)) @current-open) :_)}
      [:div
       [select/radio {:value @select?
                      :on-change #(reset! select? %)
                      :items [true false]
                      :format-item {true (tr [:project :draw-selection :select])
                                    false (tr [:project :draw-selection :deselect])}}]
       [:div {:style {:display :flex
                      :justify-content :space-between}}
        [buttons/button-secondary {:on-click #(do
                                                (reset! current-open false)
                                                (e! (project-controller/->DrawSelectionCancel)))}
         (tr [:buttons :cancel])]
        [buttons/button-primary {:on-click #(do
                                              (reset! current-open false)
                                              (e! (project-controller/->DrawSelectionConfirm
                                                    @select? related-feature-type)))}
         (tr [:buttons :confirm])]]]]
     [buttons/link-button {:on-click #(do
                                        (reset! current-open true)
                                        (e! (project-controller/->DrawSelectionOnMap related-feature-type features)))}
      (tr [:project :draw-selection :link])]]))

(defn restrictions-listing
  [e! open-types road-buffer-meters {:keys [restrictions loading? checked-restrictions toggle-restriction
                                             draw-selection-features search-type
                                             on-mouse-enter on-mouse-leave]}]
  [:<>
   (if loading?
     [fetching-features-skeletons 10]
     (let [restrictions-by-type (group-by :VOOND restrictions)]
       [:<>
        [container/collapsible-container {:on-toggle (e! project-controller/->ToggleSelectedCategory)
                                          :open? (open-types :selected)}
         (tr [:common :n-selected] {:count (str (count checked-restrictions))})
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
         (if (= search-type :drawn-area)
           [typography/Heading2 {:style {:padding "1rem"}} (tr [:search-area :restriction-results-by-area] {:count (count restrictions)})]
           [typography/Heading2 {:style {:padding "1rem"}} (tr [:search-area :restriction-results-by-buffer] {:count (count restrictions)
                                                                                                              :meters road-buffer-meters})])
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
               :side-component [typography/SmallGrayText (tr [:project :wizard :selected-count]
                                                             {:selected (count group-checked)
                                                              :total (count restrictions)})]}
              group
              [itemlist/checkbox-list
               {:on-select-all #(e! (project-controller/->SelectRestrictions (set restrictions)))
                :on-deselect-all #(e! (project-controller/->DeselectRestrictions (set restrictions)))
                :actions (list [draw-selection e! :restrictions restrictions draw-selection-features group])}
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
  [e! road-buffer-meters {:keys [loading? cadastral-units checked-cadastral-units
                                  draw-selection-features search-type
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

       (if (= search-type :drawn-area)
         [typography/Heading2 {:style {:padding "1rem"}} (tr [:search-area :cadastral-results-by-area] {:count (count cadastral-units)})]
         [typography/Heading2 {:style {:padding "1rem"}} (tr [:search-area :cadastral-results-by-buffer] {:count (count cadastral-units)
                                                                                                          :meters road-buffer-meters})])

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
              :value (str (:L_AADRESS cadastral-unit) " " (:TUNNUS cadastral-unit) " "
                          (when (land-controller/unit-new? (:TUNNUS cadastral-unit) cadastral-units)
                            (tr [:land :new-cadastral-unit]))
                          (when (:deleted cadastral-unit)
                            (tr [:land :archived-unit])))
              :on-change (r/partial toggle-cadastral-unit cadastral-unit)
              :on-mouse-enter (r/partial on-mouse-enter cadastral-unit)
              :on-mouse-leave (r/partial on-mouse-leave cadastral-unit)}))]]])))
