(ns teet.project.land-view
  (:require [teet.ui.typography :as typography]
            [herb.core :refer [<class]]
            [teet.common.common-styles :as common-styles]
            [teet.ui.buttons :as buttons]
            [teet.localization :refer [tr]]
            [teet.ui.util :refer [mapc]]
            [reagent.core :as r]
            [teet.ui.material-ui :refer [ButtonBase]]
            [teet.ui.text-field :refer [TextField]]
            [teet.project.land-controller :as land-controller]
            [postgrest-ui.components.query :as query]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.url :as url]
            [clojure.string :as str]))

(defn cadastral-unit
  [unit]
  ^{:key (str (:TUNNUS unit))}
  [ButtonBase {:style {:flex 1
                       :padding "0.5rem"
                       :justify-content :flex-start
                       :background-color theme-colors/gray-lightest
                       :border-bottom (str "1px solid " theme-colors/gray-lighter)}}
   [:div
    [typography/SectionHeading (:L_AADRESS unit)]]])

(defn cadastral-group
  [[group units]]
  ^{:key (str group)}
  (let [unit-count (count units)]
    [:div {:style {:margin-bottom "2rem"}}
     [:div.heading {:style {:background-color theme-colors/gray
                            :padding "0.5rem"
                            :color theme-colors/white}}
      [typography/SectionHeading (tr [:cadastral-group group])]
      [:span unit-count (if (= 1 unit-count)
                          " unit"
                          " units")]]
     [:div {:style {:display :flex
                    :flex-direction :column}}
      (mapc
        cadastral-unit
        units)]]))

(defn search-field
  [value-atom on-change]
  [:div {:style {:margin-bottom "1rem"}}
   [TextField {:label "Filter units"
               :value @value-atom
               :on-change on-change}]])

(defn cadastral-groups
  [units]
  (r/with-let [search-value (r/atom "")
               on-change (fn [e]
                           (reset! search-value (-> e .-target .-value)))]
    (let [units (if (empty? @search-value)
                  units
                  (filter #(str/includes? (str/lower-case (get % :L_AADRESS)) @search-value) units))
          grouped (if (empty? units)
                    []
                    (->> units
                         (group-by :OMVORM)))]
      [:div
       [search-field search-value on-change]
       [:div
        (mapc
          cadastral-group
          grouped)]])))

(defn related-cadastral-units-info
  [e! app project]
  (let [related-ids (map #(subs % 2)
                         (:thk.project/related-cadastral-units project))
        api-url (get-in app [:config :api-url])]
    [:div
     [:div {:style {:margin-top "1rem"}
            :class (<class common-styles/heading-and-button-style)}
      [typography/Heading2 "Cadastral units"]
      [buttons/button-secondary {:href (url/with-params :tab "data" :configure "cadastral-units")} "Edit"]]
     [query/query {:endpoint api-url
                   :state (:thk.project/related-cadastral-units-info project)
                   :set-state! (e! land-controller/->SetCadastralInfo)
                   :table "feature"
                   :where {"id" [:in related-ids]}
                   :select ["properties"]}
      cadastral-groups]]))
