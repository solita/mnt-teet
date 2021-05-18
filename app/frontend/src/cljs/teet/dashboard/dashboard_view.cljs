(ns teet.dashboard.dashboard-view
  (:require [alandipert.storage-atom :refer [local-storage]]
            [herb.core :refer [<class]]
            [reagent.core :as r]
            [teet.activity.activity-view :as activity-view]
            [teet.common.common-styles :as common-styles]
            [teet.localization :refer [tr tr-enum]]
            [teet.notification.notification-controller :as notification-controller]
            [teet.project.project-model :as project-model]
            [teet.project.task-model :as task-model]
            [teet.projects.projects-style :as projects-style]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.buttons :as buttons]
            [teet.ui.common :as common]
            [teet.ui.format :as fmt]
            [teet.ui.material-ui :refer [Paper Divider Collapse ButtonBase]]
            [teet.ui.typography :as typography]
            [teet.ui.url :as url]
            [teet.ui.util :refer [mapc]]
            [teet.util.collection :as cu]
            [teet.util.datomic :as du]
            [teet.land.land-style :as land-style]
            [clojure.string :as cstr]))

(defonce open-projects-atom (local-storage (r/atom #{}) "dashboard-open-projects"))


(defn- project-card-subheader-style []
  {:margin-left "1.5rem"
   :display :flex})

(defn- section-style []
  {:margin-bottom "2rem"})

(defn- section [label content]
  [:div {:class (<class section-style)}
   [typography/Heading2 {:style {:margin-bottom "0.5rem"
                                 :font-variant :all-petite-caps}} label]
   content])

(defn- is-task-part-notification?
  "Checks if the notification type is task-part as these require additional part name in the message"
  [notification-type]
  (or
    (du/enum= notification-type :notification.type/task-part-waiting-for-review)
    (du/enum= notification-type :notification.type/task-part-review-accepted)
    (du/enum= notification-type :notification.type/task-part-review-rejected)))

(defn- task-row [{:task/keys [group type status estimated-start-date estimated-end-date]
                  id :db/id :as t}]
  [:<>
   (url/consume-navigation-info
    (fn [{params :params}]
      [:div {:class (<class common-styles/flex-row)}
       [:div {:class (<class common-styles/flex-table-column-style 30)}
        (activity-view/task-name-and-status t)]
       [:div {:class (<class common-styles/flex-table-column-style 30)}
        (fmt/date estimated-start-date)
        " \u2013 "
        (fmt/date estimated-end-date)]
       [:div {:class (<class common-styles/flex-table-column-style 40)}
        (tr-enum status)]]))])

(defn- task-rows-by-group [[group tasks]]
  [:<>
   [:b (tr-enum group) ":"]
   (mapc task-row tasks)])

(defn- activity-info
  [project {:activity/keys [name estimated-start-date estimated-end-date
                    status tasks]
    id :db/id :as _activity}]
  [:<>
   (url/provide-navigation-info
    {:params {:project (:thk.project/id project)
              :activity (str id)}}
    [:<>
     [:div {:style {:display :flex}}
      [:b (tr-enum name)]
      [:div {:style {:margin-left "0.5rem"}}
        (str (fmt/date estimated-start-date)
             "\u2013"
             (fmt/date estimated-end-date))]]
     [:div [:span (tr-enum status)]]
     (mapc task-rows-by-group
           (sort-by (comp task-model/task-group-order :db/ident first)
                    (group-by :task/group tasks)))
     [Divider {:style {:margin "2rem 0"}}]])])

(defn- lifecycle-info
  [project {:thk.lifecycle/keys [type activities estimated-start-date estimated-end-date] :as _lifecycle}]
  [:div {:style {:margin-bottom "2rem"}}
   [:div {:class (<class common-styles/margin-bottom 1)}
    [typography/Heading3
     (tr-enum type)
     [:span {:class [(<class common-styles/gray-text)
                     (<class common-styles/margin-left 0.5)]}
      (str (fmt/date estimated-start-date)
           "\u2013"
           (fmt/date estimated-end-date))]]]
   (mapc (r/partial activity-info project) activities)])

(defn project-card [{:keys [e! open-projects toggle-project]}
                    {:keys [project notifications]}]
  (let [open? (boolean (open-projects (:db/id project)))]
    [common/hierarchical-container
     {:heading-color theme-colors/gray-lighter
      :heading-text-color theme-colors/gray-dark
      :show-polygon? open?
      :heading-content
      [ButtonBase {:class (<class land-style/group-style)
                   :on-click #(toggle-project (:db/id project))}
       [:div {:class (<class common-styles/flex-row-w100-space-between-center)}
        [:div {:class (<class common-styles/space-between-center)}
         [:div {:class (<class projects-style/project-status-circle-style
                               (:thk.project/status project))}]
         [:div {:style {:flex-grow 10 :font-weight :bold}
                :class (<class common-styles/inline-block)}
          (project-model/get-column project :thk.project/project-name)]]
        [buttons/button-secondary {:on-click (fn [e] (.stopPropagation e))
                                   :style {:margin "0 0.5rem"}
                                   :element "a"
                                   :href (url/project (:thk.project/id project))}
         (tr [:dashboard :open])]]
       [:div {:class (<class project-card-subheader-style)}
        (str (fmt/date (:thk.project/estimated-start-date project))
             "\u2013"
             (fmt/date (:thk.project/estimated-end-date project)))]]
      :children
      (list
        ^{:key "project-info"}
        [Collapse {:in open?}
         [:div {:style {:padding "1rem"
                        :background-color theme-colors/gray-lightest}}
          [section
           (tr [:dashboard :notifications])
           (if (empty? notifications)
             [:span {:class (<class common-styles/gray-text)}
              (tr [:dashboard :no-notifications-for-project])]
             (doall
               (for [{:notification/keys [type]
                      :meta/keys [created-at]
                      task-part-name :notification/target
                      id :db/id} notifications]
                 ^{:key (str id)}
                 [:div
                  [buttons/link-button {:on-click #(e! (notification-controller/->NavigateTo id))}
                   (if (is-task-part-notification? type)    ;; In case of a task part notification, the translated message includes a task part name and number
                     (tr [:enum (:db/ident type)] {:task-part-name (str "#" (:file.part/number task-part-name)
                                                                        (if (cstr/blank? (:file.part/name task-part-name))
                                                                          ""
                                                                          (str " \"" (:file.part/name task-part-name) "\"")))})
                     (tr-enum type)) " " (fmt/date-time created-at)]])))]

          [section
           (tr [:dashboard :activities-and-tasks])
           (mapc (r/partial lifecycle-info project) (:thk.project/lifecycles project))]]])}]))

(defn dashboard-page [e!
                      {user :user :as _app}
                      dashboard]
  [:div {:style {:margin "3rem" :display "flex" :justify-content "center"}}
   [Paper {:style {:flex 1
                   :max-width "800px" :padding "1rem"}}
    [typography/Heading1 {:data-cy "dashboard-header"}
     (tr [:dashboard :my-projects])]
    (mapc (r/partial project-card {:e! e!
                                   :open-projects @open-projects-atom
                                   :toggle-project #(swap! open-projects-atom cu/toggle %)})
          dashboard)]])
