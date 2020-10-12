(ns teet.project.project-timeline-view
  (:require [herb.core :as herb :refer [<class]]
            [reagent.core :as r]
            [teet.localization :refer [tr tr-tree]]
            [teet.project.project-model :as project-model]
            [teet.project.project-style :as project-style]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.buttons :as buttons]
            [teet.ui.format :as format]
            [teet.ui.icons :as icons]
            [teet.ui.panels :as panels]
            [teet.ui.timeline :as timeline]))

(defn- lifecycle-label
  []
  {:text-transform :uppercase
   :font-weight :bold})

(defn- task-label
  []
  ^{:pseudo {:before {:content "'• '"}}}
  {})

(def ^:private item-styles
  {:project {:background theme-colors/blue
             :text       theme-colors/white}
   :lifecycle {:background theme-colors/blue-light
               :text       theme-colors/white
               :label-class (<class lifecycle-label)}
   :activity {:background theme-colors/blue-lighter
              :text       theme-colors/blue-dark}
   :task {:background theme-colors/gray-lighter
          :text       theme-colors/blue-dark
          :label-class (<class task-label)}})


(defn timeline [dark-theme? {:thk.project/keys [estimated-start-date estimated-end-date lifecycles]
                             :as project}]
  (r/with-let [show-in-modal? (r/atom false)]
    (when (and estimated-start-date estimated-end-date)
      (let [tr* (tr-tree [:enum])
            project-name (project-model/get-column project :thk.project/project-name)
            timeline-component [timeline/timeline {:start-date estimated-start-date
                                                   :end-date   estimated-end-date
                                                   :item-styles item-styles}
                                ;; Lifecycles
                                (mapcat (fn [{:thk.lifecycle/keys [type estimated-start-date estimated-end-date
                                                                   activities]}]
                                          (concat
                                           [{:label      (-> type :db/ident tr*)
                                             :start-date estimated-start-date
                                             :end-date   estimated-end-date
                                             :item-type  :lifecycle
                                             :hover      [:div (tr* (:db/ident type))]}]
                                           ;; Activities
                                           (mapcat (fn [{:activity/keys [name status estimated-start-date estimated-end-date tasks]
                                                         :as _activity}]
                                                     (let [label (-> name :db/ident tr*)]
                                                       (concat [{:label label
                                                                 :start-date estimated-start-date
                                                                 :end-date estimated-end-date
                                                                 :item-type :activity
                                                                 :hover [:div
                                                                         [:div [:b (tr [:fields :activity/name]) ": "] label]
                                                                         [:div [:b (tr [:fields :activity/status]) ": "] (tr* (:db/ident status))]]}]
                                                               (for [{:task/keys [description type estimated-start-date estimated-end-date]}
                                                                     (sort-by :task/estimated-start-date tasks)]
                                                                 ;; TODO what label
                                                                 {:label (tr [:enum (:db/ident type)])
                                                                  :start-date estimated-start-date
                                                                  :end-date estimated-end-date
                                                                  :item-type :task
                                                                  ;; TODO what to hover?
                                                                  :hover [:div description]}))))
                                                   activities)))
                                        (sort-by :thk.lifecycle/estimated-start-date lifecycles))]]

        [:<>
         [buttons/link-button {:on-click #(reset! show-in-modal? true)
                               :class (<class project-style/project-timeline-link dark-theme?)}
          (tr [:project :show-timeline])]
         (when @show-in-modal?
           [panels/modal {:title (str project-name " "
                                      (format/date estimated-start-date) " – " (format/date estimated-end-date))
                          :max-width "lg"
                          :on-close #(reset! show-in-modal? false)}
            timeline-component])]))))
