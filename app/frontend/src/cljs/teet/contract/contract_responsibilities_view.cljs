(ns teet.contract.contract-responsibilities-view
  (:require [teet.common.common-styles :as common-styles]
            [teet.localization :refer [tr tr-enum]]
            [herb.core :refer [<class] :as herb]
            [teet.ui.text-field :refer [TextField]]
            [teet.contract.contract-common :as contract-common]
            [teet.contract.contract-style :as contract-style]
            [teet.ui.material-ui :refer [Grid Checkbox Divider]]
            [teet.ui.typography :as typography]
            [reagent.core :as r]
            teet.contract.contract-spec
            [teet.user.user-model :as user-model]
            [teet.ui.url :as url]
            [teet.ui.util :as ui-util]))

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
    [:tr {:class (<class contract-style/responsibilities-table-row-style)}
     (ui-util/mapc
       (fn [[heading opts]]
         [:td (merge
                {:class (<class contract-style/responsibilities-table-heading-cell-style)}
                opts)
          heading])
       table-headings)]]
   (for [task-group groups
         :let [title (first task-group)
               tasks (second task-group)
               rows (reduce
                      (fn [acc item]
                        (conj
                          acc
                          [[[url/Link (:navigation-info item)
                             (tr-enum (:task/type item))]]
                           [(:owner item)]
                           [(if (nil? (:task/assignee item))
                              ""
                              (user-model/user-name (:task/assignee item)))]
                           [(tr-enum (:task/status item))]]))
                      []
                      tasks)]]
     [:tbody
      [:tr
       [:th {:colSpan 4
             :class (<class contract-style/responsibilities-table-header-style)}
        (tr-enum title)]]
      (ui-util/mapc
        (fn [row]
          [:tr {:class (<class contract-style/responsibilities-table-row-style)}
           (ui-util/mapc
             (fn [[column {:keys [style class] :as opts}]]
               [:td (merge
                      {:style (merge {} style)
                       :class (herb/join
                                (<class contract-style/responsibilities-table-cell-style)
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
       [typography/Heading4 (if (get-in target [:target :activity/name])
                              (tr [:enum (get-in target [:target :activity/name])])
                              "")]
       [url/Link
        (merge (:target-navigation-info target)
               {:component-opts {:data-cy "target-responsibility-activity-link"}})
        (tr [:link :target-view-activity])]]
      (when (not-empty groups)
        (simple-table-with-many-bodies [[(tr [:contract :responsible :header :task])]
                                        [(tr [:contract :responsible :header :tram-reviewer])]
                                        [(tr [:contract :responsible :header :company-responsible])]
                                        [(tr [:contract :responsible :header :status])]] groups))])])

(defn responsibilities-page
  [e! app contract]
  (let [targets (:thk.contract/targets contract)]
    [:div {:class (<class contract-style/contract-responsibilities-container-style)}
     [contract-common/contract-heading e! app contract]
     [:div {:class (<class contract-style/responsibilities-page-container)}
      (when
        (not-empty targets)
        [:div {:class (<class common-styles/margin-top 2)}
         [typography/Heading1 {:class (<class common-styles/margin-top 2)}
          (tr [:contract :table-heading :task-responsibilities])]
         [targets-responsibilities targets]])]]))
