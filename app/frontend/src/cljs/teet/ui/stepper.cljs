(ns teet.ui.stepper
  (:require [teet.ui.material-ui :refer [Link Divider Collapse]]
            [herb.core :refer [<class]]
            [teet.theme.theme-colors :as theme-colors]
            [teet.ui.icons :as icons]
            [teet.project.project-controller :as project-controller]
            [teet.routes :as routes]
            [teet.localization :refer [tr tr-tree]]))

(def foo [{:name      "Design"
           :start     (js/Date. "2019-01-01")
           :end       (js/Date. "2019-05-01")
           :status    :done
           :sub-steps [{:name      "Pre-design"
                        :start     (js/Date. "2019-01-01")
                        :status    :done
                        :end       (js/Date. "2019-05-01")
                        :sub-steps [{:name "foobar"}
                                    {:name "barfoo"}
                                    {:name "fobar"}]}
                       {:name      "Design"
                        :start     (js/Date. "2019-01-01")
                        :status    :started
                        :sub-steps [{:name "foobar"}
                                    {:name "barfoo"}
                                    {:name "fobar"}]
                        :end       (js/Date. "2019-05-01")}]}
          {:name      "Construction"
           :start     (js/Date. "2019-01-01")
           :end       (js/Date. "2019-05-01")
           :status    :started
           :sub-steps [{:name   "Pre-design"
                        :start  (js/Date. "2019-01-01")
                        :status :started
                        :end    (js/Date. "2019-05-01")}
                       {:name   "Design"
                        :start  (js/Date. "2019-01-01")
                        :status :not-started
                        :end    (js/Date. "2019-05-01")}]}])



(defn circle-svg
  [{:keys [status size]}]
  [:svg {:style   {:position  :absolute
                   :left      "-1px"
                   :top       "-1px"
                   :transform "translateX(-50%)"}
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
  [done?]
  {:padding-left "1.5rem"
   :margin-left  "1rem"
   :border-left  "2px solid black"
   :position     :relative})

(defn goal-style
  []
  {:padding-left "1.5rem"
   :margin-left  "1rem"
   :position     :relative})

(defn step-info
  [{:keys [offset background-color padding-top]}]
  {:display          :block
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

(defn vertical-stepper
  [e! {:thk.project/keys [lifecycles] :as _project} stepper]
  [:div
   [:ol {:class (<class ol-class)}
    (doall
      (for [{lc-id  :db/id
             status :status :as lifecycle} lifecycles]
        ^{:key (str lc-id)}
        [:li
         [:div {:class (<class item-class (= :done status))}
          [circle-svg {:status :started :size 35}]
          [:div {:class (<class step-info {:offset 2})}
           [:button
            {:class    (<class stepper-button-style {:size "24px" :open? (= (str lc-id) (str (:lifecycle stepper)))})
             :on-click #(e! (project-controller/->ToggleStepperLifecycle lc-id))}
            (tr [:enum (get-in lifecycle [:thk.lifecycle/type :db/ident])])]]]
         [:div
          [Collapse {:in (= (str lc-id) (str (:lifecycle stepper)))}
           (for [{status :status :as activity} (:thk.lifecycle/activities lifecycle)]
             ^{:key (str (:db/id activity))}
             [:ol {:class (<class ol-class)}
              [:li
               [:div {:class (<class item-class (= :done status))}
                [circle-svg {:status status :size 24}]
                [:div {:class (<class step-info {:offset 0})}
                 [:button {:class (<class stepper-button-style {:size "20px" :open? false})}
                  (tr [:enum (:db/ident (:activity/name activity))])]]]
               [:div
                (if (:activity/tasks activity)
                  [:ol {:class (<class ol-class)}
                   (for [{:task/keys [status type] :as task} (:activity/tasks activity)]
                     ^{:key (str (:db/id task))}
                     [:li
                      [:div {:class (<class item-class (= :done status))}
                       [circle-svg {:status :foo :size 12}]
                       [:div {:class (<class task-info)}
                        [:button {:class (<class stepper-button-style {:size "18px" :open? false})}
                         (tr [:enum (:db/ident type)])]]]])]
                  [:div {:class (<class item-class (= :done status))}
                   [:div {:class (<class task-info)}
                    [:span "no tasks"]]])
                [:div {:class (<class item-class (= :done status))}
                 [:div {:class (<class task-info)}
                  [:button "add task"]]]]]])
           [:div {:class (<class item-class (= :done status))}
            [:button "Add stage"]]]]]))]])
