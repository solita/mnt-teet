(ns teet.project.road-view
  "Road objects tab"
  (:require [teet.ui.query :as query]
            [reagent.core :as r]
            [teet.ui.typography :as typography]
            [teet.ui.util :refer [mapc]]
            [teet.util.collection :as cu]
            [clojure.string :as str]
            [teet.project.road-controller :as road-controller]
            [teet.map.openlayers :as openlayers]
            [teet.project.project-map-view :as project-map-view]
            [teet.map.map-view :as map-view]
            [teet.map.map-overlay :as map-overlay]))

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

(defn- road-info-result [{:keys [road-part objects]}]
  (let [{:keys [road carriageway sequence-nr start-m end-m name]} road-part]
    [:div {:style {:height "195px" :overflow-y :scroll}}
     [:div
      [:b (str road " " carriageway " " sequence-nr "  " start-m " - " end-m
               " (" name ")")]
      #_(mapc (fn [[type objects]]
              [:div
               [:b type " " (count objects)]])
            objects)]]))

(defn- road-info [e! point]
  [query/query
   {:e! e!
    :query :road/road-properties-for-coordinate
    :args {:coordinate point}
    :simple-view [road-info-result]}])

(defn road-objects-tab [e! _app project]
  []
  (r/with-let [remove-handler!
               (openlayers/set-click-handler!
                #(project-map-view/register-overlay!
                  :road-at-coordinate
                  {:coordinate (:location %)
                   :content [map-overlay/overlay {:width 200
                                                  :height 200
                                                  :single-line? false}
                             [road-info e! (:location %)]]}))]
    [:<>
     [query/query
      {:e! e!
       :query :road/project-intersecting-objects
       :args (select-keys project [:thk.project/id])
       :simple-view [road-objects-listing e!]}]]
    (finally
      (remove-handler!)
      (project-map-view/remove-overlay! :road-at-coordinate))))
