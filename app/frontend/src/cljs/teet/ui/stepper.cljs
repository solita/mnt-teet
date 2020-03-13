(ns teet.ui.stepper
  (:require [teet.ui.material-ui :refer [Link Collapse]]
            [herb.core :refer [<class]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.buttons :as buttons]
            [teet.ui.icons :as icons]
            [teet.project.activity-model :as activity-model]
            [teet.project.project-controller :as project-controller]
            [teet.localization :refer [tr]]
            [reagent.core :as r]
            [teet.ui.format :as format]
            [teet.ui.typography :as typography]
            [teet.project.task-model :as task-model]))

(defn- svg-style
  [bottom?]
  (merge {:position  :absolute
          :left      "-1px"
          :transform "translateX(-50%)"}
         (if bottom?
           {:bottom "0px"}
           {:top "-1px"})))

(defn- circle-svg
  [{:keys [status size bottom?]}]
  (let [stroke-width 2
        r (- (/ size 2) (/ stroke-width 2))
        fill (if (= status :done)
               theme-colors/gray-dark
               "#fff")]
    [:svg {:class   (<class svg-style bottom?)
           :version "1.1" :width size :height size :fill "none" :xmlns "http://www.w3.org/2000/svg"}
     [:circle {:cx "50%" :cy "50%" :r r :fill fill :stroke-width stroke-width :stroke (if (= :not-started status)
                                                                                        theme-colors/gray-light
                                                                                        theme-colors/gray-dark)}]
     (when (= status :started)
       [:circle {:cx "50%" :cy "50%" :r (- r 4) :fill theme-colors/gray-dark}])]))


(defn- ol-class
  []
  {:padding-left 0
   :list-style   :none})

(defn- item-class
  [done? _last?]
  {:padding-left "1.5rem"
   :margin-left  "1rem"
   :position     :relative
   :border-left  (if done?
                   "2px solid black"
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
  []
  {:color            theme-colors/gray-light
   :background-color theme-colors/gray-lightest
   :top              "-17px"
   :padding-top      "10px"
   :padding-left     "1rem"
   :padding-bottom   "1rem"
   :display          :block
   :position         :relative})

(defn- stepper-button-style
  [{:keys [size open?]}]
  ^{:pseudo {:hover {:color theme-colors/blue-dark}}}
  {:border      :none
   :background  :none
   :transition "color 0.2s ease-in-out"
   :font-size   size
   :font-weight (if open?
                  :bold
                  :normal)
   :color       theme-colors/primary
   :cursor      :pointer
   :padding     0})

(defn- lifecycle-style
  [open? last? done?]
  (merge
    {:padding-left "1.5rem"
     :margin-left  "1rem"
     :position     :relative
     :border-left  "2px solid transparent"}
    (when (or open? (not last?))
      {:border-left (if done?
                      "2px solid black"
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

(defn vertical-stepper
  [e! {:thk.project/keys [lifecycles] :as _project} stepper]
  (let [lifecycle-ids (mapv :db/id lifecycles)
        lc-id (:lifecycle stepper)
        old-stepper? (empty? (filter #(= lc-id %) lifecycle-ids))]
    (when old-stepper?
      (e! (project-controller/->ToggleStepperLifecycle (first lifecycle-ids)))))
  (fn [e! {:thk.project/keys [lifecycles id] :as _project} stepper]
    [:div
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
               [:div {:class (<class lifecycle-style (= (str lc-id) (str (:lifecycle stepper))) last? (= :done first-activity-status))}
                [circle-svg {:status lc-status :size 28}]
                [:div {:class (<class step-container-style {:offset -3})}
                 [:div {:class (<class flex-column)}
                  [:button
                   {:class (<class stepper-button-style {:size "24px" :open? (= (str lc-id) (str (:lifecycle stepper)))})
                    :on-click #(e! (project-controller/->ToggleStepperLifecycle lc-id))}
                   (tr [:enum (get-in lifecycle [:thk.lifecycle/type :db/ident])])]
                  [typography/SmallText
                   (format/date estimated-start-date) " – " (format/date estimated-end-date)]]]]
               [:div
                [Collapse {:in (= (str lc-id) (str (:lifecycle stepper)))}
                 (doall
                   (for [{activity-id :db/id
                          activity-est-end :activity/estimated-end-date
                          activity-est-start :activity/estimated-start-date
                          :as activity} (:thk.lifecycle/activities lifecycle)]
                     (let [activity-state (activity-step-state activity)
                           activity-status (get-in activity [:activity/status :db/ident])
                           activity-open? (= (str activity-id) (str (:activity stepper)))]
                       ^{:key (str (:db/id activity))}
                       [:ol {:class (<class ol-class)}
                        [:li
                         [:div {:class (<class item-class (= :done activity-state) last?)}
                          [circle-svg {:status activity-state :size 20}]
                          [:div {:class (<class step-container-style {:offset -4})}
                           [:div {:class (<class flex-column)}
                            [:button {:on-click #(e! (project-controller/->ToggleStepperActivity activity-id))
                                      :class (<class stepper-button-style {:size "20px" :open? activity-open?})}
                             (tr [:enum (:db/ident (:activity/name activity))])]
                            [typography/SmallText
                             [:strong
                              (tr [:enum activity-status]) " "]
                             (format/date activity-est-start) " – " (format/date activity-est-end)]]
                           (when (= (str activity-id) (str (:activity stepper)))
                             [buttons/button-secondary {:size :small
                                                        :disabled disable-buttons?
                                                        :on-click (e! #(project-controller/->OpenEditActivityDialog (str activity-id) (str lc-id)))}
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
                                      [:div {:class (<class item-class (= :done activity-state) last?)}
                                       [circle-svg {:status task-status :size 14}]
                                       [:div {:class (<class task-info)}
                                        [Link {:href (str "#/projects/" id "/" (:db/id task))
                                               :class (<class stepper-button-style {:size "16px" :open? false})}
                                         (tr [:enum (:db/ident type)])]]]])))]
                              [:div {:class (<class item-class (= :done activity-state) last?)}
                               [:div {:class (<class task-info)}
                                [:span (tr [:project :activity :no-tasks])]]])
                            [:div {:class (<class item-class (= :done activity-state) last?)}
                             [circle-svg {:status :not-started :size 14}]
                             [:div {:class (<class task-info)}
                              [buttons/rect-primary {:size :small
                                                     :disabled disable-buttons?
                                                     :on-click (e! project-controller/->OpenTaskDialog (str activity-id))
                                                     :start-icon (r/as-element
                                                                   [icons/content-add])}
                               (tr [:project :add-task])]]]])]])))
                 [:div {:class (<class item-class (= :done lc-status) last?)}
                  [circle-svg {:status :not-started :size 20 :bottom? last?}]
                  [:div {:style (merge {:position :relative}
                                       (if last?
                                         {:top "3px"}
                                         {:top "-3px"
                                          :padding-bottom "1.5rem"}))}
                   [buttons/rect-primary {:size :small
                                          :disabled disable-buttons?
                                          :on-click (e! project-controller/->OpenActivityDialog (str lc-id))
                                          :start-icon (r/as-element
                                                        [icons/content-add])}
                    (tr [:project :add-activity lc-type])]]]]]]))
          lifecycles))]]))
