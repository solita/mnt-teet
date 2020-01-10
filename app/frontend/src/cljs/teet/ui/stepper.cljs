(ns teet.ui.stepper
  (:require [teet.ui.material-ui :refer [Link Divider Collapse]]
            [herb.core :refer [<class]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.icons :as icons]
            [teet.project.project-controller :as project-controller]
            [teet.routes :as routes]
            [teet.localization :refer [tr tr-tree]]))

(defn circle-svg
  [{:keys [status size bottom?]}]
  [:svg {:style   (merge {:position  :absolute
                          :left      "-1px"
                          :transform "translateX(-50%)"}
                         (if bottom?
                           {:bottom "0px"}
                           {:top "-1px"}))
         :version "1.1" :width size :height size :fill "none" :xmlns "http://www.w3.org/2000/svg"}
   [:circle {:cx "50%" :cy "50%" :r (Math/floor (/ size 2)) :fill "#34394C"}]
   (cond
     (= status :started)
     [:circle {:cx "50%" :cy "50%" :r (- (/ size 2) 4) :stroke "white" :stroke-width "3"}]
     (= status :done)
     nil
     :else
     [:circle {:cx "50%" :cy "50%" :r (- (/ size 2) 4) :stroke "white" :fill "white" :stroke-width "3"}])])


(defn ol-class
  []
  {:padding-left 0
   :list-style   :none})

(defn item-class
  [done? last?]
  {:padding-left "1.5rem"
   :margin-left  "1rem"
   :position     :relative
   :border-left  (if done?
                   "2px solid black"
                   (str "2px solid " theme-colors/gray-lighter))})

(defn goal-style
  []
  {:padding-left "1.5rem"
   :margin-left  "1rem"
   :position     :relative})

(defn step-info
  [{:keys [offset background-color padding-top]}]
  {:display          :flex
   :justify-content  :space-between
   :position         :relative
   :padding-top      (str padding-top "px")
   :padding-bottom   "2rem"
   :top              (str (- offset padding-top) "px")
   :background-color (if background-color
                       background-color
                       :none)})

(defn task-info
  []
  {:background-color theme-colors/gray-lightest
   :top              "-17px"
   :padding-top      "10px"
   :padding-left     "1rem"
   :padding-bottom   "1rem"
   :display          :block
   :position         :relative})

(defn stepper-button-style
  [{:keys [size open?]}]
  {:border      :none
   :background  :none
   :font-size   size
   :font-weight (if open?
                  :bold
                  :normal)
   :color       theme-colors/primary
   :cursor      :pointer
   :padding     0})

(defn lifecycle-style
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

(defn vertical-stepper
  [e! {:thk.project/keys [lifecycles id] :as _project} stepper]
  [:div
   [:ol {:class (<class ol-class)}
    (doall
      (map-indexed
        (fn [i {lc-id  :db/id
                status :status :as lifecycle}]
          (let [last? (= (+ i 1) (count lifecycles))]
            ^{:key (str lc-id)}
            [:li
             [:div {:class (<class lifecycle-style (= (str lc-id) (str (:lifecycle stepper))) last? (= :done status))}
              [circle-svg {:status :started :size 35}]
              [:div {:class (<class step-info {:offset 2})}
               [:button
                {:class    (<class stepper-button-style {:size "24px" :open? (= (str lc-id) (str (:lifecycle stepper)))})
                 :on-click #(e! (project-controller/->ToggleStepperLifecycle lc-id))}
                (tr [:enum (get-in lifecycle [:thk.lifecycle/type :db/ident])])]]]
             [:div
              [Collapse {:in (= (str lc-id) (str (:lifecycle stepper)))}
               (for [{status      :status
                      activity-id :db/id :as activity} (:thk.lifecycle/activities lifecycle)]
                 ^{:key (str (:db/id activity))}
                 [:ol {:class (<class ol-class)}
                  [:li
                   [:div {:class (<class item-class (= :done status) last?)}
                    [circle-svg {:status :started :size 24}]
                    [:div {:class (<class step-info {:offset 0})}
                     [:button {:on-click #(e! (project-controller/->ToggleStepperActivity activity-id))
                               :class    (<class stepper-button-style {:size "20px" :open? false})}
                      (tr [:enum (:db/ident (:activity/name activity))])]
                     (when (= (str activity-id) (str (:activity stepper)))
                       [:button {:on-click (e! #(project-controller/->OpenEditActivityDialog (str activity-id)))}
                        "Edit"])]]
                   (when (= (str activity-id) (str (:activity stepper)))
                     [:<>
                      (if (:activity/tasks activity)
                        [:ol {:class (<class ol-class)}
                         (for [{:task/keys [status type] :as task} (:activity/tasks activity)]
                           ^{:key (str (:db/id task))}
                           [:li
                            [:div {:class (<class item-class (= :done status) last?)}
                             [circle-svg {:status :foo :size 12}]
                             [:div {:class (<class task-info)}
                              [Link {:href  (str "#/projects/" id "/" (:db/id task))
                                     :class (<class stepper-button-style {:size "18px" :open? false})}
                               (tr [:enum (:db/ident type)])]]]])]
                        [:div {:class (<class item-class (= :done status) last?)}
                         [:div {:class (<class task-info)}
                          [:span "no tasks"]]])
                      [:div {:class (<class item-class (= :done status) last?)}
                       [circle-svg {:status :foo :size 12}]
                       [:div {:class (<class task-info)}
                        [:button {:on-click (e! project-controller/->OpenTaskDialog (str activity-id))}
                         "add task"]]]])]])
               [:div {:class (<class item-class (= :done status) last?)}
                [circle-svg {:status :default :size 24 :bottom? last?}]
                [:div (when-not last?
                        {:style {:position       :relative
                                 :top            "-3px"
                                 :padding-bottom "2rem"}})
                 [:button {:on-click (e! project-controller/->OpenActivityDialog (str lc-id))}
                  "Add stage"]]]]]]))
        lifecycles))]])
