(ns teet.contract.contract-responsibilities-view
  (:require [teet.common.common-styles :as common-styles]
            [teet.localization :refer [tr tr-enum]]
            [herb.core :refer [<class] :as herb]
            [teet.ui.icons :as icons]
            [teet.ui.text-field :refer [TextField]]
            [teet.contract.contract-common :as contract-common]
            [teet.contract.contract-style :as contract-style]
            [teet.ui.material-ui :refer [Grid Checkbox Divider]]
            [teet.ui.typography :as typography]
            [teet.ui.buttons :as buttons]
            [reagent.core :as r]
            teet.contract.contract-spec
            [teet.common.common-controller :as common-controller]
            [teet.contract.contract-partners-controller :as contract-partners-controller]
            [teet.routes :as routes]
            [teet.ui.form :as form]
            [teet.ui.select :as select]
            [teet.ui.common :as common]
            [teet.ui.validation :as validation]
            [teet.authorization.authorization-check :as authorization-check]
            [teet.user.user-model :as user-model]
            [teet.ui.format :as format]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.table :as table]
            [clojure.string :as str]
            [re-svg-icons.feather-icons :as fi]
            [teet.ui.url :as url]
            [teet.contract.contract-model :as contract-model]
            [teet.contract.contract-status :as contract-status]
            [teet.ui.util :as ui-util]))

(defn- simple-table-row-style
  []
  ^{:pseudo {:first-of-type {:border-top :none
                             :border-right :none
                             :border-left :none}}}
  {:border-width "1px"
   :border-style :solid
   :border-color theme-colors/gray-lighter})

(defn- table-heading-cell-style
  []
  {:white-space :nowrap
   :font-weight 500
   :font-size "0.875rem"
   :color theme-colors/gray
   :padding"1rem 0.5rem 1rem 0.5rem"
   :text-align :left})

(defn- simple-table-cell-style
  []
  {:padding "1rem 0.5rem 1rem 0.5rem"
   :border-width "1px"
   :border-style :solid
   :border-color theme-colors/gray-lighter})

(defn simple-table-with-many-bodies
  [table-headings groups]
  [:table {:style {:border-collapse :collapse
                   :width "100%"}}
   [:colgroup
    [:col {:span "1" :style {:width "35%"}}]
    [:col {:span "1" :style {:width "25%"}}]
    [:col {:span "1" :style {:width "25%"}}]
    [:col {:span "1" :style {:width "15%"}}]]
   [:thead
    [:tr {:class (<class simple-table-row-style)}
     (ui-util/mapc
       (fn [[heading opts]]
         [:td (merge
                {:class (<class table-heading-cell-style)}
                opts)
          heading])
       table-headings)]]
   (for [task-group groups
         :let [title (first task-group)
               tasks (second task-group)
               rows (reduce (fn [acc item]
                              (conj acc [[[url/Link (:navigation-info item)
                                           (tr-enum (:task/type item))]]
                                         [(:owner item)]
                                         [(if (nil? (:task/assignee item))
                                            "Not assigned"
                                            (user-model/user-name (:task/assignee item)))]
                                         [(tr-enum (:task/status item))]])) [] tasks)]]
     [:tbody
      [:tr
       [:th {:colspan 4
             :style {:text-align :left
                     :padding "1rem 0.5rem 1rem 0.5rem"
                     :border-width "0 0 1px 0"
                     :border-style :solid
                     :border-color theme-colors/gray-lighter}} (tr-enum title)]]
      (ui-util/mapc
        (fn [row]
          [:tr {:class (<class simple-table-row-style)}
           (ui-util/mapc
             (fn [[column {:keys [style class] :as opts}]]
               [:td (merge
                      {:style (merge {} style)
                       :class (herb/join
                                (<class simple-table-cell-style)
                                (when class
                                  class))}
                      (dissoc opts :style :class))
                column])
             row)])
        rows)])])

(defn targets-responsibilities
  [targets]
  [:div
   (for [target targets
         :let [task? (some? (get-in target [:target :task/type]))
               tasks (get-in target [:activity :activity/tasks])
               groups (group-by :task/group tasks)]]
     [:div {:style {:padding "2rem 0 2rem 0"}}
      [:div {:class (herb/join (<class common-styles/flex-row-w100-space-between-center)
                               (<class common-styles/margin-bottom 2))}
       [typography/Heading1 (get-in target [:project :thk.project/name])]
       [typography/Heading4 (tr [:enum (get-in target [:target :activity/name])])]
       [url/Link
        (merge (:target-navigation-info target)
               {:component-opts {:data-cy "target-responsibility-activity-link"}})
        (tr [:link :target-view-activity])]]
      (simple-table-with-many-bodies [[(tr [:contract :responsible :header :task])]
                                      [(tr [:contract :responsible :header :tram-reviewer])]
                                      [(tr [:contract :responsible :header :company-responsible])]
                                      [(tr [:contract :responsible :header :status])]] groups)])])

(defn responsibilities-page
  [e! app contract]
  (let [targets (:thk.contract/targets contract)]
    [:div {:class (<class contract-style/contract-page-container-style)}
     [contract-common/contract-heading e! app contract]
     [:div {:class (<class contract-style/responsibilities-page-container)}
      (when
        (not-empty targets)
        [:div {:class (<class common-styles/margin-bottom 4)}
         [typography/Heading4 {:class (<class common-styles/margin-bottom 2)}
          (tr [:contract :table-heading :task-responsibilities])]
         [targets-responsibilities targets]])]]))
