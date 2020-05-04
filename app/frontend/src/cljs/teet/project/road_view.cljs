(ns teet.project.road-view
  "Road objects tab"
  (:require [teet.ui.query :as query]
            [reagent.core :as r]
            [teet.ui.typography :as typography]
            [teet.ui.util :refer [mapc]]
            [teet.util.collection :as cu]))

(defn road-object [{:keys [open toggle]} object]
  [:div
   [typography/Heading3 {:on-click #(toggle object)}
    "road object"]
   (when (open object)
     [:div {:style {:margin-left "1rem"}}
      (pr-str object)])])

(defn road-objects [{:keys [open toggle] :as opts} type objects]
  [:div
   [typography/Heading3 {:on-click #(toggle type)}
    (str type) " " (count objects)]
   (when (open type)
     [:div {:style {:margin-left "1rem"}}
      (mapc (r/partial road-object opts) objects)])])

(defn road-objects-listing [e! objs-by-type]
  (r/with-let [open (r/atom #{})]
    [:<>
     (mapc (fn [[type objects]]
             ^{:key (str type)}
             [road-objects {:open @open
                            :toggle #(swap! open cu/toggle %)}
              type objects])
           objs-by-type)]))

(defn road-objects-tab [e! _app project]
  []
  [query/query
   {:e! e!
    :query :road/project-intersecting-objects
    :args (select-keys project [:thk.project/id])
    :simple-view [road-objects-listing e!]}])
