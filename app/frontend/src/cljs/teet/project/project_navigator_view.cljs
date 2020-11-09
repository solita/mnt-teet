(ns teet.project.project-navigator-view
  (:require [teet.ui.material-ui :refer [Link Collapse Paper Grid]]
            [herb.core :refer [<class]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.buttons :as buttons]
            [teet.ui.icons :as icons]
            [teet.ui.project-context :as project-context]
            [teet.ui.url :as url]
            [teet.ui.util :refer [mapc]]
            [teet.activity.activity-model :as activity-model]
            [teet.project.project-controller :as project-controller]
            [teet.localization :refer [tr tr-enum]]
            [reagent.core :as r]
            [teet.ui.format :as format]
            [teet.ui.typography :as typography]
            [teet.task.task-style :as task-style]
            [teet.project.project-map-view :as project-map-view]
            [teet.authorization.authorization-check :refer [when-authorized]]
            [teet.ui.panels :as panels]
            [teet.project.project-style :as project-style]
            [teet.project.task-model :as task-model]
            [teet.task.task-controller :as task-controller]
            [teet.project.project-menu :as project-menu]
            [teet.navigation.navigation-style :as navigation-style]))

(defn- svg-style
  [bottom?]
  (merge {:position  :absolute
          :left      "-1px"
          :transform "translateX(-50%)"}
         (if bottom?
           {:bottom "0px"}
           {:top "-1px"})))

(defn- circle-svg
  [{:keys [status size bottom? dark-theme?]}]
  (let [stroke-width 2
        r (- (/ size 2) (/ stroke-width 2))
        fill
        (if dark-theme?
          (if (= status :done)
            theme-colors/white
            theme-colors/gray-dark)
          (if (= status :done)
            theme-colors/gray-dark
            "#fff"))]
    [:svg {:class   (<class svg-style bottom?)
           :version "1.1" :width size :height size :fill "none" :xmlns "http://www.w3.org/2000/svg"}
     [:circle {:cx "50%" :cy "50%" :r r :fill fill :stroke-width stroke-width :stroke (if (= :not-started status)
                                                                                        theme-colors/gray-light
                                                                                        (if dark-theme?
                                                                                          theme-colors/white
                                                                                          theme-colors/gray-dark))}]
     (when (= status :started)
       [:circle {:cx "50%" :cy "50%" :r (- r 4) :fill (if dark-theme?
                                                        theme-colors/white
                                                        theme-colors/gray-dark)}])]))


(defn ol-class
  []
  {:padding-left 0
   :list-style :none})

(defn item-class
  [done? dark-theme?]
  {:padding-left "1.5rem"
   :margin-left "1rem"
   :position :relative
   :border-left (if done?
                  (str "2px solid " (if dark-theme? theme-colors/white "#000"))
                  (str "2px dashed " (if dark-theme? theme-colors/gray-light theme-colors/gray-lighter)))
   :padding-bottom "1px"                                    ;;This is so the border goes over the inner margins also
   })

(defn step-container-style
  [{:keys [offset background-color padding-top]}]
  {:display          :flex
   :justify-content  :space-between
   :position         :relative
   :align-items      :center
   :padding-top      (str padding-top "px")
   :padding-bottom   "0.5rem"
   :top              (str (- offset padding-top) "px")
   :background-color (if background-color
                       background-color
                       :none)})

(defn task-info
  []
  {:display :flex
   :flex 1
   :align-items :start
   :flex-direction :column
   :position :relative})

(defn empty-section-style
  []
  {:padding "0 0 1rem 0"})

(defn activity-container-style
  [dark-theme?]
  {:color (if dark-theme?
            theme-colors/gray-lighter
            theme-colors/gray-light)
   :background-color (if dark-theme?
                       theme-colors/gray
                       theme-colors/gray-lightest)
   ;:top "-17px"
   :padding-top "10px"
   :padding-left "1rem"})


(defn task-list-container-style
  [dark-theme?]
  {:color (if dark-theme?
            theme-colors/gray-lighter
            theme-colors/gray-light)
   :background-color (if dark-theme?
                       theme-colors/gray
                       theme-colors/gray-lightest)
   ;:top "-17px"
   :margin-bottom "1rem"
   :padding "1rem"})

(defn stepper-button-style
  [{:keys [size open? dark-theme?]}]
  ^{:pseudo {:hover {:color (if dark-theme?
                              theme-colors/blue-light
                              theme-colors/blue-dark)}}}
  {:border :none
   :background :none
   :text-decoration :none
   :transition "color 0.2s ease-in-out"
   :font-size size
   :font-weight (if open?
                  :bold
                  :normal)
   :color (if dark-theme?
            theme-colors/white
            theme-colors/primary)
   :cursor :pointer
   :padding 0})

(defn lifecycle-style
  [open? last? done? dark-theme?]
  (merge
    {:padding-left "1.5rem"
     :margin-left  "1rem"
     :position     :relative
     :border-left  "2px solid transparent"}
    (when (or open? (not last?))
      {:border-left (if done?
                      (str "2px solid " (if dark-theme? theme-colors/white "black"))
                      (str "2px dashed " (if dark-theme? theme-colors/gray-light theme-colors/gray-lighter)))})))

(defn flex-column
  []
  {:display        :flex
   :flex-direction :column
   :align-items    :flex-start})

(defn- activity-step-state
  [activity]
  (let [status (get-in activity [:activity/status :db/ident])]
    (cond
      (activity-model/activity-finished-statuses status)
      :done
      (activity-model/activity-in-progress-statuses status)
      :started
      :else
      :not-started)))

(defn- lifecycle-status
  [{:thk.lifecycle/keys [activities] :as lifecycle}]
  (let [in-progress-activities (->> lifecycle
                                    :thk.lifecycle/activities
                                    (map #(get-in % [:activity/status :db/ident]))
                                    (filter activity-model/activity-in-progress-statuses)
                                    count)
        ready-activities (->> lifecycle
                              :thk.lifecycle/activities
                              (map #(get-in % [:activity/status :db/ident]))
                              (filter activity-model/activity-finished-statuses)
                              count)
        lc-status (cond
                    (and (zero? in-progress-activities) (zero? ready-activities))
                    :not-started
                    (= (count activities) ready-activities)
                    :done
                    :else
                    :started)]
    lc-status))

(defn navigator-container-style
  [dark-theme?]
  {:background-color (if dark-theme?
                       theme-colors/gray-dark
                       theme-colors/white)
   :padding "0 0 1rem 1rem"
   :overflow-y :auto
   :flex 1})

(defn custom-list-indicator
  [dark-theme?]
  ^{:pseudo {:before {:content "'\\2022'"
                      :color (if dark-theme?
                               theme-colors/white
                               theme-colors/primary)
                      :display :inline-block
                      :width "1em"
                      :font-weight :bold
                      :margin-left "-1em"}}}
  {:display :flex
   :padding "0.5rem"
   :margin-left "1rem"})

(defn- activity-task-list [{:keys [e! dark-theme? project-id rect-button]}
                           {:keys [activity activity-state activity-id]}]
  (let [tasks (:activity/tasks activity)]
    [:<>
     (if (seq tasks)
       [:div
        (mapc
          (fn [[group tasks]]
            [:div.task-group
             [:ol {:class (<class ol-class)
                   :style {:padding-bottom "0.5rem"}}
              [:li.task-group-label
               [:div
                [typography/SmallGrayText {:style {:text-transform :uppercase
                                               :font-weight :bold}}
                 (tr-enum group)]]]
              (doall
                (for [{:task/keys [type] :as task} tasks]
                  ^{:key (str (:db/id task))}
                  [:li.task-group-task {:class (<class custom-list-indicator dark-theme?)}
                   [:div
                    [:div
                     [Link {:href (str "#/projects/" project-id "/" activity-id "/" (:db/id task))
                            :class (<class stepper-button-style {:size "16px"
                                                                 :open? false
                                                                 :dark-theme? dark-theme?})}
                      (tr [:enum (:db/ident type)])]]]]))]])

          ;; group tasks by the task group
          (sort-by (comp task-model/task-group-order :db/ident first)
                   (group-by :task/group tasks)))]
       [:div {:class (<class empty-section-style)}
        [typography/GreyText (tr [:project :activity :no-tasks])]])
     [:div #_{:class (<class item-class (= :done activity-state) dark-theme?)}
      [when-authorized :activity/add-tasks
       activity
       [:div.project-navigator-add-task
        [rect-button {:size :small
                      :on-click (e! task-controller/->OpenAddTasksDialog activity-id)
                      :start-icon (r/as-element
                                    [icons/content-add])}
         (tr [:project :add-task])]]]]]))


(defn- activity
  [{:keys [params dark-theme? activity-section-content activity-link-page] :as ctx}
   {activity-id :db/id
    activity-est-end :activity/estimated-end-date
    activity-est-start :activity/estimated-start-date
    :as activity}]
  (let [activity-state (activity-step-state activity)
        activity-open? (= (str activity-id) (:activity params))]
    ^{:key (str (:db/id activity))}
    [:ol.project-navigator-activity {:class (<class ol-class)}
     [:li
      [:div {:class (<class item-class (= :done activity-state) dark-theme?)}
       [circle-svg {:status activity-state :size 20 :dark-theme? dark-theme?}]
       [:div {:class (<class step-container-style {:offset -4})}
        [:div {:class (<class flex-column)}
         [url/Link {:page (or activity-link-page :activity)
                    :params {:activity (:db/id activity)}
                    :class (<class stepper-button-style {:size "20px"
                                                         :open? activity-open?
                                                         :dark-theme? dark-theme?})}
          (tr [:enum (:db/ident (:activity/name activity))])]
         [:span.project-navigator-activity-dates
          [typography/SmallGrayText
           (format/date activity-est-start) " – " (format/date activity-est-end)]]]]
       (when activity-open?
         [:div {:class (<class task-list-container-style dark-theme?)}
          [activity-section-content ctx {:activity activity
                                         :activity-id activity-id
                                         :activity-state activity-state}]])]]]))

(defn project-navigator
  [e! {:thk.project/keys [lifecycles] :as project} {:keys [stepper] :as _app} _ _ _]
  (let [lifecycle-ids (mapv :db/id lifecycles)
        lc-id (:lifecycle stepper)
        old-stepper? (empty? (filter #(= lc-id %) lifecycle-ids))]
    (when old-stepper?
      (e! (project-controller/->ToggleStepperLifecycle (first lifecycle-ids)))))
  (fn [e! {:thk.project/keys [lifecycles id] :as _project} {:keys [stepper params user] :as _app}
       {:keys [dark-theme? activity-section-content add-activity? activity-link-page] :as _opts}]
    (let [rect-button (if dark-theme?
                        buttons/rect-white
                        buttons/rect-primary)]
      [:div.project-navigator {:class (<class navigator-container-style dark-theme?)}
       [:ol {:class (<class ol-class)}
        (doall
          (map-indexed
            (fn [i {lc-id :db/id
                    :thk.lifecycle/keys [activities estimated-end-date estimated-start-date type] :as lifecycle}]
              (let [last? (= (+ i 1) (count lifecycles))
                    lc-type (:db/ident type)
                    first-activity-status (activity-step-state (first activities))
                    lc-status (lifecycle-status lifecycle)
                    open? (= (str lc-id) (str (:lifecycle stepper)))]
                ^{:key (str lc-id)}
                [:li
                 ;; Use first activity status instead of lifecycle, because there is no work to be done between the lifecycle and the first activity
                 [:div.project-navigator-lifecycle {:class (<class lifecycle-style (= (str lc-id) (str (:lifecycle stepper))) last? (= :done first-activity-status) dark-theme?)}
                  [circle-svg {:status lc-status :size 28 :dark-theme? dark-theme?}]
                  [:div {:class (<class step-container-style {:offset -3})}
                   [:div {:class (<class flex-column)}
                    [:button.project-navigator-lifecycle-toggle
                     {:class (<class stepper-button-style {:size "24px"
                                                           :open? open?
                                                           :dark-theme? dark-theme?})
                      :on-click #(e! (project-controller/->ToggleStepperLifecycle lc-id))}
                     (tr [:enum (get-in lifecycle [:thk.lifecycle/type :db/ident])])]
                    [:span.project-navigator-lifecycle-dates
                     [typography/SmallGrayText
                      (format/date estimated-start-date) " – " (format/date estimated-end-date)]]]]]
                 [:div
                  [Collapse {:in open?}
                   (mapc (partial activity {:e! e!
                                            :user user
                                            :stepper stepper
                                            :activity-link-page activity-link-page
                                            :activity-section-content activity-section-content
                                            :dark-theme? dark-theme?
                                            :lc-id lc-id
                                            :rect-button rect-button
                                            :project-id id
                                            :params params})
                         (:thk.lifecycle/activities lifecycle))
                   (when add-activity?
                     [when-authorized :activity/create
                      project
                      [:div {:class (<class item-class (= :done lc-status) dark-theme?)}
                       (when last?
                         [circle-svg {:status :not-started :size 20 :bottom? last? :dark-theme? dark-theme?}])
                       [:div.project-navigator-add-activity
                        {:style (merge {:position :relative}
                                       (if last?
                                         {:top "3px"}
                                         {:top "-3px"
                                          :padding-bottom "0.5rem"}))}
                        [rect-button {:size :small
                                      :on-click (e! project-controller/->OpenActivityDialog (str lc-id))
                                      :start-icon (r/as-element
                                                    [icons/content-add])}
                         (tr [:project :add-activity lc-type])]]]])]]]))
            lifecycles))]])))


(defn project-task-navigator
  [e! project app dark-theme?]
  [project-navigator e! project app {:dark-theme? dark-theme?
                                     :activity-section-content activity-task-list
                                     :add-activity? true}])

(defmulti project-navigator-dialog (fn [_opts dialog]
                                     (:type dialog)))

(defmethod project-navigator-dialog :default
  [_opts dialog]
  [:div "Unsupported project navigator dialog " (pr-str dialog)])

(defn- override-project-navigator-dialog-options
  [dialog]
  (case (:type dialog)
    :new-activity {:max-width :md
                   :title (tr [:project :add-activity
                               (:lifecycle-type dialog)])}
    :add-tasks {:max-width :md}
    {}))


(defn project-navigator-dialogs [{:keys [e! app] :as opts}]
  (when-let [dialog (get-in app [:stepper :dialog])]
    [panels/modal (merge {:title (tr [:project (:type dialog)])
                          :open-atom (r/wrap true :_)
                          :on-close (e! project-controller/->CloseDialog)
                          :max-width :sm}
                         (override-project-navigator-dialog-options dialog))
      [project-navigator-dialog opts dialog]]))

(defn project-navigator-with-content
  "Page structure showing project navigator along with content."
  [{:keys [e! project app column-widths show-map?]
    :or {column-widths [3 6 :auto]
         show-map? true}
    :as opts} content]
  (let [[nav-w content-w] column-widths]
    [project-context/provide
     {:db/id (:db/id project)
      :thk.project/id (:thk.project/id project)}
     [:div.project-navigator-with-content {:class (<class project-style/page-container)}
      [typography/Heading1 (or (:thk.project/project-name project)
                               (:thk.project/name project))]
      [project-navigator-dialogs opts]
      [Paper {:class (<class task-style/task-page-paper-style)}
       [Grid {:container true
              :wrap :nowrap
              :spacing   0}
        [Grid {:item true
               :xs nav-w
               :class (<class navigation-style/navigator-left-panel-style)}
         [project-menu/project-menu e! app project true]
         [project-task-navigator e! project app true]]
        [Grid {:item  true
               :xs content-w
               :style {:padding "2rem 1.5rem"
                       :overflow-y :auto
                       ;; content area should scroll, not the whole page because we
                       ;; want map to stay in place without scrolling it
                       }}
         content]
        (when show-map?
          [Grid {:item  true
                 :xs :auto
                 :style {:display :flex
                         :flex    1}}
           [project-map-view/project-map e! app project]])]]]]))
