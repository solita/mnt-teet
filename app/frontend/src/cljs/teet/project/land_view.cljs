(ns teet.project.land-view
  (:require [teet.ui.typography :as typography]
            [herb.core :refer [<class]]
            [teet.common.common-styles :as common-styles]
            [teet.ui.buttons :as buttons]
            [teet.localization :refer [tr tr-enum]]
            [teet.ui.util :refer [mapc]]
            [reagent.core :as r]
            [teet.ui.material-ui :refer [ButtonBase Collapse]]
            [teet.ui.text-field :refer [TextField]]
            [teet.project.land-controller :as land-controller]
            [postgrest-ui.components.query :as postgrest-query]
            [teet.theme.theme-colors :as theme-colors]
            [garden.color :refer [darken]]
            [teet.ui.url :as url]
            [teet.land.land-specs]
            [teet.project.project-controller :as project-controller]
            [teet.map.map-controller :as map-controller]
            [teet.ui.form :as form]
            [teet.common.common-controller :as common-controller]
            [teet.ui.select :as select]
            [cljs-time.core :as time]
            [cljs-time.coerce :as c]))

(defn cadastral-unit-style
  [selected?]
  (let [bg-color (if selected?
                   theme-colors/gray-lighter
                   theme-colors/gray-lightest)]
    ^{:pseudo {:hover {:background-color (darken bg-color 10)}}}
    {:flex 1
     :padding "0.5rem"
     :display :flex
     :flex-direction :column
     :transition "background-color 0.2s ease-in-out"
     :align-items :normal
     :background-color bg-color
     :border-bottom (str "1px solid " theme-colors/gray-lighter)}))

(defn impact-form-style
  []
  {:background-color theme-colors/gray-lighter
   :padding "1.5rem"})

(defn impact-form-footer
  [{:keys [validate disabled? cancel]}]
  [:div {:style {:display :flex
                 :flex-direction :row
                 :justify-content :flex-end
                 :background-color theme-colors/gray-lighter
                 :padding "0 1.5rem 1.5rem 0"}}
   [buttons/button-secondary {:style {:margin-right "1rem"}
                              :on-click cancel
                              :size :small}
    (tr [:buttons :cancel])]
   [buttons/button-primary {:disabled disabled?
                            :type :submit
                            :size :small
                            :on-click validate}
    (tr [:buttons :save])]])

(defn area-to-obtain-class
  []
  {:display :flex})

(defn cadastral-unit-form
  [e! {:keys [PINDALA] :as unit}]
  (r/with-let [[impact-form update-impact-form]
               (common-controller/internal-state (merge
                                                   {:land-acquisition/impact :land-acquisition.impact/undecided}
                                                   (:land-acquisition unit)) {:merge? true})]
    (let [show-extra-fields? (= (:land-acquisition/impact @impact-form) :land-acquisition.impact/purchase-needed)]
      [:div
       [form/form {:e! e!
                   :value @impact-form
                   :on-change-event update-impact-form
                   :save-event #(land-controller/->SubmitLandPurchaseForm @impact-form (:teet-id unit))
                   :spec :land-acquisition/form
                   :cancel-event #(land-controller/->ToggleLandUnit unit)
                   :footer impact-form-footer
                   :class (<class impact-form-style)}
        ^{:attribute :land-acquisition/impact}
        [select/select-enum {:e! e!
                             :attribute :land-acquisition/impact
                             :show-empty-selection? false}]
        (when show-extra-fields?
          ^{:attribute :land-acquisition/pos-number :xs 6}
          [TextField {:type :number}])
        (when show-extra-fields?
          ^{:attribute :land-acquisition/area-to-obtain
            :container-class (<class area-to-obtain-class)
            :adornment [:div {:style {:flex-basis "50%"
                                      :flex-shrink 0
                                      :margin-left "0.5rem"
                                      :display :flex
                                      :align-items :center}}
                        (if-let [area (:land-acquisition/area-to-obtain @impact-form)]
                          [:span (tr [:land :net-area-balance] {:area (- PINDALA area)})]
                          [:span (tr [:land :total-area] {:area PINDALA})])]}
          [TextField {:type :number}])]])))

(defn acquisition-impact-status
  [impact]
  (let [impact (if impact
                 impact
                 :land-acquisition.impact/undecided)
        color (case impact
                :land-acquisition.impact/purchase-needed
                theme-colors/red
                :land-acquisition.impact/purchase-not-needed
                theme-colors/green
                :land-acquisition.impact/undecided
                theme-colors/gray-lighter
                theme-colors/gray-lighter)]
    [:div {:class (<class common-styles/flex-align-center)}
     [:div {:class (<class common-styles/status-circle-style color)
            :title impact}]
     [:span {:style {:text-align :left}
             :class (<class common-styles/gray-text)}
      (tr-enum impact)]]))

(defn cadastral-unit-container-style
  []
  ^{:pseudo {:first-of-type {:border-top "1px solid white"}}}
  {:border-left "1px solid white"
   :display :flex
   :position :relative
   :flex 1
   :flex-direction :column
   :margin-left "15px"})

(defn cadastral-unit-quality-style
  [quality]
  {:position :absolute
   :left "-15px"
   :top "50%"
   :transform "translateY(-50%)"
   :color (if (= quality :bad)
            theme-colors/red
            theme-colors/orange)})

(defn cadastral-unit
  [e! {:keys [TUNNUS KINNISTU MOOTVIIS MUUDET selected?] :as unit}]
  ^{:key (str TUNNUS)}
  (let [quality-date (time/date-time 2018 01 01)            ;;Arbitrary date before which data quality is bad
        quality (cond
                  (and (= MOOTVIIS "mõõdistatud, L-EST")
                       (not (time/before? (c/from-string MUUDET) quality-date)))
                  :good
                  (and (= MOOTVIIS "mõõdistatud, L-EST")
                       (time/before? (c/from-string MUUDET) quality-date))
                  :not-so-good
                  :else
                  :bad)]
    [:div {:class (<class cadastral-unit-container-style)}
     [:div {:class (<class cadastral-unit-quality-style quality)}
      [:span {:title (str MOOTVIIS " – " MUUDET)} (case quality
                                                    :bad "!!!"
                                                    :not-so-good "!"
                                                    "")]]
     [ButtonBase {:on-mouse-enter (e! project-controller/->FeatureMouseOvers "geojson_features_by_id" true unit)
                  :on-mouse-leave (e! project-controller/->FeatureMouseOvers "geojson_features_by_id" false unit)
                  :on-click (e! land-controller/->ToggleLandUnit unit)
                  :class (<class cadastral-unit-style selected?)}
      [typography/SectionHeading {:style {:text-align :left}} (:L_AADRESS unit)]
      [:div {:style {:display :flex
                     :width "100%"
                     :justify-content :space-between}}
       [acquisition-impact-status (get-in unit [:land-acquisition :land-acquisition/impact])]
       [:span {:class (<class common-styles/gray-text)}
        (tr [:land :estate]) " " KINNISTU]]]
     [Collapse
      {:in selected?
       :mount-on-enter true}
      [cadastral-unit-form e! unit]]]))

(defn cadastral-heading-container-style
  []
  (let [bg-color theme-colors/gray]
    ^{:pseudo {:before {:content "''"
                        :width 0
                        :height 0
                        :border-bottom "15px solid transparent"
                        :border-left (str "15px solid " bg-color)
                        :position :absolute
                        :bottom "-15px"
                        :transform "rotate(90deg)"
                        :left 0}}}
    {:background-color bg-color
     :position :relative
     :padding "0.5rem"
     :color theme-colors/white}))

(defn cadastral-group
  [e! [group units]]
  ^{:key (str group)}
  [:div {:style {:margin-bottom "2rem"}}
   [:div.heading {:class (<class cadastral-heading-container-style)}
    [typography/SectionHeading (tr [:cadastral-group group])]
    [:span (count units) " " (if (= 1 (count units))
                           (tr [:land :unit])
                           (tr [:land :units]))]]
   [:div {:style {:display :flex
                  :flex-direction :column}}
    (mapc
      (r/partial cadastral-unit e!)
      units)]])

(defn search-field
  [e! cadastral-search-value]
  (r/with-let [on-change (fn [e]
                           (e! (land-controller/->SearchOnChange (-> e .-target .-value))))]
    [:div {:style {:margin-bottom "1rem"}}
     [TextField {:label (tr [:land :filter-label])
                 :value cadastral-search-value
                 :on-change on-change}]]))

(defn cadastral-groups
  [e! _ _]
  (e! (land-controller/->SearchOnChange ""))
  (fn [e! project units]
    (let [land-acquisitions (into {}
                                  (map
                                    (juxt :land-acquisition/cadastral-unit
                                          #(update % :land-acquisition/impact :db/ident)))
                                  (:land-acquisitions project))
          ids (:thk.project/filtered-cadastral-units project) ;; set by the cadastral search field
          units (map (fn [unit]
                       (assoc unit :land-acquisition (get land-acquisitions (:teet-id unit))))
                     units)
          units (if (empty? ids)
                  []
                  (filter #(ids (:teet-id %)) units))
          grouped (if (empty? units)
                    []
                    (->> units
                         (group-by :OMVORM)))]
      [:<>
       [:div
        (when (:land-acquisitions project)
          (mapc
            (r/partial cadastral-group e!)
            grouped))]])))

(defn related-cadastral-units-info
  [e! _app project]
  (e! (land-controller/->FetchLandAcquisitions (:thk.project/id project)))
  (fn [e! app project]
    (let [related-ids (map #(subs % 2)
                           (:thk.project/related-cadastral-units project))
          api-url (get-in app [:config :api-url])
          datasource-id (map-controller/datasource-id-by-name app "cadastral-units")]
      [:div
       [:div {:style {:margin-top "1rem"}
              :class (<class common-styles/heading-and-button-style)}
        [typography/Heading2 (tr [:project :cadastral-units-tab])]
        [buttons/button-secondary {:href (url/set-query-param :tab "data" :configure "cadastral-units")}
         (tr [:buttons :edit])]]
       [search-field e! (:cadastral-search-value project)]
       ;; Todo add skeleton
       [postgrest-query/query {:endpoint api-url
                               :state (:thk.project/related-cadastral-units-info project)
                               :set-state! (e! land-controller/->SetCadastralInfo)
                               :table "feature"
                               :where {"id" [:in related-ids]
                                       "datasource_id" [:= datasource-id]}
                               :select ["properties"]}
        [cadastral-groups e! (dissoc project :cadastral-search-value)]]])))
