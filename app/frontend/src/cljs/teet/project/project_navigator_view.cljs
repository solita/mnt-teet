(ns teet.project.project-navigator-view
  (:require [teet.ui.material-ui :refer [Link Collapse Paper Grid]]
            [herb.core :refer [<class]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.buttons :as buttons]
            [teet.ui.icons :as icons]
            [teet.ui.util :refer [mapc]]
            [teet.project.activity-model :as activity-model]
            [teet.project.project-controller :as project-controller]
            [teet.localization :refer [tr]]
            [reagent.core :as r]
            [teet.ui.format :as format]
            [teet.ui.typography :as typography]
            [teet.project.task-model :as task-model]
            [teet.task.task-style :as task-style]
            [teet.project.project-map-view :as project-map-view]
            [teet.ui.breadcrumbs :as breadcrumbs]
            [teet.ui.panels :as panels]))

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


(defn- ol-class
  []
  {:padding-left 0
   :list-style   :none})

(defn- item-class
  [done? dark-theme?]
  {:padding-left "1.5rem"
   :margin-left "1rem"
   :position :relative
   :border-left (if done?
                  (str "2px solid " (if dark-theme? theme-colors/white "black"))
                  (str "2px solid " theme-colors/gray-lighter))})

(defn- step-container-style
  [{:keys [offset background-color padding-top]}]
  {:display          :flex
   :justify-content  :space-between
   :position         :relative
   :align-items      :center
   :padding-top      (str padding-top "px")
   :padding-bottom   "1.5rem"
   :top              (str (- offset padding-top) "px")
   :background-color (if background-color
                       background-color
                       :none)})

(defn- task-info
  [dark-theme?]
  {:color theme-colors/gray-light
   :background-color (if dark-theme?
                       theme-colors/gray
                       theme-colors/gray-lightest)
   :top "-17px"
   :padding-top "10px"
   :padding-left "1rem"
   :padding-bottom "1rem"
   :display :block
   :position :relative})

(defn- stepper-button-style
  [{:keys [size open? dark-theme?]}]
  ^{:pseudo {:hover {:color (if dark-theme?
                              theme-colors/blue-light
                              theme-colors/blue-dark)}}}
  {:border :none
   :background :none
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

(defn- lifecycle-style
  [open? last? done? dark-theme?]
  (merge
    {:padding-left "1.5rem"
     :margin-left  "1rem"
     :position     :relative
     :border-left  "2px solid transparent"}
    (when (or open? (not last?))
      {:border-left (if done?
                      (str "2px solid " (if dark-theme? theme-colors/white "black"))
                      (str "2px solid " theme-colors/gray-lighter))})))

(defn- flex-column
  []
  {:display        :flex
   :flex-direction :column
   :align-items    :flex-start})

(defn- activity-step-state
  [activity]
  (let [status (get-in activity [:activity/status :db/ident])]
    (cond
      (activity-model/activity-ready-statuses status)
      :done
      (activity-model/activity-in-progress-statuses status)
      :started
      :else
      :not-started)))

(defn- task-step-state
  [task]
  (let [status (get-in task [:task/status :db/ident])
        task-done-statuses #{:task.status/accepted :task.status/completed
                              :task.status/rejected :task.status/canceled}]
    (cond
      (task-done-statuses status)
      :done
      (task-model/in-progress? status)
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
                              (filter activity-model/activity-ready-statuses)
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
   :padding "1rem"})

(defn- activity [{:keys [e! stepper dark-theme? disable-buttons? project-id lc-id rect-button]}
                 {activity-id :db/id
                  activity-est-end :activity/estimated-end-date
                  activity-est-start :activity/estimated-start-date
                  :as activity}]

  (let [activity-state (activity-step-state activity)
        activity-status (get-in activity [:activity/status :db/ident])
        activity-open? (= (str activity-id) (str (:activity stepper)))]
    ^{:key (str (:db/id activity))}
    [:ol {:class (<class ol-class)}
     [:li
      [:div {:class (<class item-class (= :done activity-state) dark-theme?)}
       [circle-svg {:status activity-state :size 20 :dark-theme? dark-theme?}]
       [:div {:class (<class step-container-style {:offset -4})}
        [:div {:class (<class flex-column)}
         [:button {:on-click #(e! (project-controller/->ToggleStepperActivity activity-id))
                   :class (<class stepper-button-style {:size "20px"
                                                        :open? activity-open?
                                                        :dark-theme? dark-theme?})}
          (tr [:enum (:db/ident (:activity/name activity))])]
         [typography/SmallText
          [:strong
           (tr [:enum activity-status]) " "]
          (format/date activity-est-start) " – " (format/date activity-est-end)]]
        (when (= (str activity-id) (str (:activity stepper)))
          [buttons/button-secondary {:size :small
                                     :disabled disable-buttons?
                                     :on-click (e! #(project-controller/->OpenEditActivityDialog
                                                     (str activity-id) (str lc-id)))}
           (tr [:buttons :edit])])]]
      (when activity-open?
        [:<>
         (if (:activity/tasks activity)
           [:ol {:class (<class ol-class)}
            (doall
             (for [{:task/keys [type] :as task} (:activity/tasks activity)]
               (let [task-status (task-step-state task)]
                 ^{:key (str (:db/id task))}
                 [:li
                  [:div {:class (<class item-class (= :done activity-state) dark-theme?)}
                   [circle-svg {:status task-status :size 14 :dark-theme? dark-theme?}]
                   [:div {:class (<class task-info dark-theme?)}
                    [Link {:href (str "#/projects/" project-id "/" activity-id "/" (:db/id task))
                           :class (<class stepper-button-style {:size "16px"
                                                                :open? false
                                                                :dark-theme? dark-theme?})}
                     (tr [:enum (:db/ident type)])]]]])))]
           [:div {:class (<class item-class (= :done activity-state) dark-theme?)}
            [:div {:class (<class task-info dark-theme?)}
             [:span (tr [:project :activity :no-tasks])]]])
         [:div {:class (<class item-class (= :done activity-state) dark-theme?)}
          [circle-svg {:status :not-started :size 14 :dark-theme? dark-theme?}]
          [:div {:class (<class task-info dark-theme?)}
           [rect-button {:size :small
                         :disabled disable-buttons?
                         :on-click (e! project-controller/->OpenTaskDialog (str activity-id))
                         :start-icon (r/as-element
                                      [icons/content-add])}
            (tr [:project :add-task])]]]])]]))

(defn project-navigator
  [e! {:thk.project/keys [lifecycles] :as _project} stepper dark-theme?]
  (let [lifecycle-ids (mapv :db/id lifecycles)
        lc-id (:lifecycle stepper)
        old-stepper? (empty? (filter #(= lc-id %) lifecycle-ids))]
    (when old-stepper?
      (e! (project-controller/->ToggleStepperLifecycle (first lifecycle-ids)))))
  (fn [e! {:thk.project/keys [lifecycles id] :as _project} stepper]
    (let [rect-button (if dark-theme?
                        buttons/rect-white
                        buttons/rect-primary)]
      [:div {:class (<class navigator-container-style dark-theme?)}
       [:ol {:class (<class ol-class)}
        (doall
          (map-indexed
            (fn [i {lc-id :db/id
                    :thk.lifecycle/keys [activities estimated-end-date estimated-start-date type] :as lifecycle}]
              (let [last? (= (+ i 1) (count lifecycles))
                    lc-type (:db/ident type)
                    disable-buttons? (= :thk.lifecycle-type/construction lc-type) ;; Disable buttons related to adding stages or tasks in construction until that part is more planned out
                    first-activity-status (activity-step-state (first activities))
                    lc-status (lifecycle-status lifecycle)]
                ^{:key (str lc-id)}
                [:li
                 ;; Use first activity status instead of lifecycle, because there is no work to be done between the lifecycle and the first activity
                 [:div {:class (<class lifecycle-style (= (str lc-id) (str (:lifecycle stepper))) last? (= :done first-activity-status) dark-theme?)}
                  [circle-svg {:status lc-status :size 28 :dark-theme? dark-theme?}]
                  [:div {:class (<class step-container-style {:offset -3})}
                   [:div {:class (<class flex-column)}
                    [:button
                     {:class (<class stepper-button-style {:size "24px"
                                                           :open? (= (str lc-id) (str (:lifecycle stepper)))
                                                           :dark-theme? dark-theme?})
                      :on-click #(e! (project-controller/->ToggleStepperLifecycle lc-id))}
                     (tr [:enum (get-in lifecycle [:thk.lifecycle/type :db/ident])])]
                    [typography/SmallText
                     (format/date estimated-start-date) " – " (format/date estimated-end-date)]]]]
                 [:div
                  [Collapse {:in (= (str lc-id) (str (:lifecycle stepper)))}
                   (mapc (partial activity {:e! e!
                                            :stepper stepper
                                            :dark-theme? dark-theme?
                                            :disable-buttons? disable-buttons?
                                            :lc-id lc-id
                                            :rect-button rect-button
                                            :project-id id})
                         (:thk.lifecycle/activities lifecycle))

                   [:div {:class (<class item-class (= :done lc-status) dark-theme?)}
                    [circle-svg {:status :not-started :size 20 :bottom? last? :dark-theme? dark-theme?}]
                    [:div {:style (merge {:position :relative}
                                         (if last?
                                           {:top "3px"}
                                           {:top "-3px"
                                            :padding-bottom "1.5rem"}))}
                     [rect-button {:size :small
                                   :disabled disable-buttons?
                                   :on-click (e! project-controller/->OpenActivityDialog (str lc-id))
                                   :start-icon (r/as-element
                                                 [icons/content-add])}
                      (tr [:project :add-activity lc-type])]]]]]]))
            lifecycles))]])))

(defmulti project-navigator-dialog (fn [_opts dialog]
                                     (:type dialog)))

(defmethod project-navigator-dialog :default
  [_opts dialog]
  [:div "Unsupported project navigator dialog " (pr-str dialog)])

(defn project-navigator-with-content [{:keys [e! project app breadcrumbs] :as opts} & content]
  [:<>
   [breadcrumbs/breadcrumbs breadcrumbs]
   [typography/Heading1 (:thk.project/name project)]
   (when-let [dialog (get-in app [:stepper :dialog])]
     [panels/modal {:title (tr [:project (:type dialog)])
                    :open-atom (r/wrap true :_)
                    :on-close (e! project-controller/->CloseDialog)}
      [project-navigator-dialog opts dialog]])
   [Paper {:class (<class task-style/task-page-paper-style)}
    [Grid {:container true
           :spacing   3}
     [Grid {:item  true
            :xs    3
            :style {:max-width "400px"}}
      [project-navigator e! project (:stepper app) true]]
     [Grid {:item  true
            :xs    6
            :style {:max-width "800px"}}
      content]
     [Grid {:item  true
            :xs    :auto
            :style {:display :flex
                    :flex    1}}
      [project-map-view/project-map e! app project]]]]])
