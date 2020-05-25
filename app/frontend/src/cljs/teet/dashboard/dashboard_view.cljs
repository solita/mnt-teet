(ns teet.dashboard.dashboard-view
  (:require [teet.ui.itemlist :as itemlist]
            [teet.user.user-model :as user-model]
            [teet.project.task-model :as task-model]
            [teet.ui.typography :as typography]
            [teet.routes :as routes]
            [teet.ui.material-ui :refer [Link Paper Card CardHeader CardContent
                                         CardActionArea CardActions Divider Collapse
                                         IconButton]]
            [teet.localization :refer [tr tr-enum]]
            [teet.ui.util :refer [mapc]]
            [teet.ui.format :as fmt]
            [teet.ui.url :as url]
            [teet.project.project-model :as project-model]
            [herb.core :refer [<class]]
            [teet.notification.notification-controller :as notification-controller]
            [reagent.core :as r]
            [teet.ui.buttons :as buttons]
            [teet.ui.icons :as icons]
            [teet.util.collection :as cu]
            [teet.projects.projects-style :as projects-style]
            [teet.common.common-styles :as common-styles]
            [teet.ui.common :as common]
            [alandipert.storage-atom :refer [local-storage]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.activity.activity-view :as activity-view]))

(defonce open-projects-atom (local-storage (r/atom #{}) "dashboard-open-projects"))

(defn- project-card-style []
  {:margin-top "2rem"})

(defn- project-card-subheader-style []
  {:margin-left "2rem"
   :display :flex})

(defn- section-style []
  {:margin-bottom "2rem"})

(defn- section [label content]
  [:div {:class (<class section-style)}
   [typography/Heading2 {:style {:margin-bottom "1rem"
                                 :font-variant :all-petite-caps}} label]
   content])

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
     [:div (tr-enum status)]
     (mapc task-rows-by-group
           (sort-by (comp task-model/task-group-order :db/ident first)
                    (group-by :task/group tasks)))
     [Divider {:style {:margin "2rem 0"}}]])])

(defn- lifecycle-info
  [project {:thk.lifecycle/keys [type activities estimated-start-date estimated-end-date] :as _lifecycle}]
  [:div {:style {:margin-bottom "2rem"}}
   [:div {:style {:margin-bottom "1rem"
                  :display :flex}}
    [typography/Heading3 (tr-enum type)]
    [typography/GreyText {:style {:margin-left "0.5rem"}}
     (str (fmt/date estimated-start-date)
          "\u2013"
          (fmt/date estimated-end-date))]]
   (mapc (r/partial activity-info project) activities)])

(defn project-card [{:keys [e! open-projects toggle-project]}
                    {:keys [project notifications]}]
  (let [open? (boolean (open-projects (:db/id project)))]
    [common/hierarchical-container
     {:heading-color theme-colors/gray-lighter
      :heading-text-color theme-colors/gray-dark
      :heading-content
      [:div {:on-click #(toggle-project (:db/id project))}
       [:div {:class (<class common-styles/flex-align-center)}
        [:div {:class (<class projects-style/project-status-circle-style
                              (:thk.project/status project))}]
        [:div {:style {:flex-grow 10 :font-weight :bold}
               :class (<class common-styles/inline-block)}
         (project-model/get-column project :thk.project/project-name)]
        [buttons/button-secondary {:on-click (fn [e] (.stopPropagation e))
                                   :style {:margin "0.5rem"}
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
        [:div {:style {:background-color theme-colors/gray-lightest}}
         [section
          (tr [:dashboard :notifications])
          (if (empty? notifications)
            [:span {:class (<class common-styles/gray-text)}
             (tr [:dashboard :no-notifications-for-project])]
            (doall
             (for [{:notification/keys [type]
                    :meta/keys [created-at]
                    id :db/id} notifications]
               ^{:key (str id)}
               [:div
                [buttons/link-button {:on-click #(e! (notification-controller/->NavigateTo id))}
                 (tr-enum type) " " (fmt/date-time created-at)]])))]

         [section
          (tr [:dashboard :activities-and-tasks])
          (mapc (r/partial lifecycle-info project) (:thk.project/lifecycles project))]]])}]))

(defn dashboard-page [e!
                      {user :user :as _app}
                      dashboard _breadcrumbs]
  [:div {:style {:margin "3rem" :display "flex" :justify-content "center"}}
   [Paper {:style {:flex 1
                   :max-width "800px" :padding "1rem"}}
    [typography/Heading1 (tr [:dashboard :my-projects])]
    (mapc (r/partial project-card {:e! e!
                                   :open-projects @open-projects-atom
                                   :toggle-project #(swap! open-projects-atom cu/toggle %)})
          dashboard)]])
