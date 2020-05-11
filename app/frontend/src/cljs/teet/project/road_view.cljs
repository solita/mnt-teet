(ns teet.project.road-view
  "Road objects tab"
  (:require [teet.ui.query :as query]
            [reagent.core :as r]
            [teet.ui.typography :as typography]
            [teet.ui.util :refer [mapc]]
            [teet.util.collection :as cu]
            [clojure.string :as str]
            [teet.project.road-controller :as road-controller]))

(defn road-object [{:keys [open toggle]} label {oid :ms:oid :as object}]
  [:div
   [typography/Heading3 {:on-click #(toggle object)}
    (str label " " oid)]
   (when (open object)
     [:div {:style {:margin-left "1rem"}}
      (mapc (fn [[key value]]
              (let [name (name key)]
                [:div
                 [:b (str (if (str/starts-with? name "ms:")
                            (subs name 3)
                            name) ": ")]
                 (str value)]))
            (filter #(not= :geometry (first %))
                    object))])])

(defn road-objects [{:keys [open toggle] :as opts} type objects]
  [:div
   [typography/Heading3 {:on-click #(toggle type)}
    (:title type) " " (count objects)]
   (when (open type)
     [:div {:style {:margin-left "1rem"}}
      (mapc (r/partial road-object opts (:title type)) objects)])])

(defn road-objects-listing [e! objs-by-type]
  (r/with-let [open (r/atom #{})]
    [:<>
     (mapc (fn [[type {:keys [feature-type objects]}]]
             ^{:key (str type)}
             [road-objects {:open @open
                            :toggle (fn [object]
                                      (when (and (map? object)
                                                 (contains? object :geometry))
                                        (e! (road-controller/->HighlightRoadObject
                                             (:geometry object))))
                                      (swap! open cu/toggle object))}
              feature-type objects])
           objs-by-type)]))

(defn road-objects-tab [e! _app project]
  []
  [query/query
   {:e! e!
    :query :road/project-intersecting-objects
    :args (select-keys project [:thk.project/id])
    :simple-view [road-objects-listing e!]}])
