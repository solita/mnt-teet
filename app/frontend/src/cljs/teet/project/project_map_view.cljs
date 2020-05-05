(ns teet.project.project-map-view
  "Project map component"
  (:require [herb.core :refer [<class]]
            [reagent.core :as r]
            [teet.map.map-view :as map-view]
            [teet.project.project-layers :as project-layers]
            [teet.project.project-style :as project-style]
            [teet.ui.itemlist :as itemlist]
            [teet.ui.common :as common]))

(defn map-style
  []
  {:flex 1})

(defn project-map [e! {:keys [map page] :as app} project]
  (r/with-let [overlays (r/atom [])
               fitted-atom (atom false)
               set-overlays! (fn [new-overlays]
                               (when (not= new-overlays @overlays)
                                 ;; Only set if actually changed (to avoid rerender loop)
                                 (reset! overlays new-overlays)))
               map-object-padding (if (= page :project)
                                    [25 25 25 (+ 100 (* 1.05 (project-style/project-panel-width)))]
                                    [25 25 25 25])]
    [:div {:style {:flex 1
                   :display :flex
                   :flex-direction :column}}
     ;; Add window width as key to force map rerender when window width changes.
     ^{:key (str @common/window-width)}
     [map-view/map-view e!
      {:class (<class map-style)
       :config (:config app)
       :layers (let [opts {:e! e!
                           :app app
                           :project project
                           :set-overlays! set-overlays!}]
                 (reduce (fn [layers layer-fn]
                           (merge layers (layer-fn opts)))
                         {}
                         [#_project-layers/surveys-layer
                          (partial project-layers/project-road-geometry-layer map-object-padding fitted-atom)
                          project-layers/setup-restriction-candidates
                          project-layers/project-drawn-area-layer
                          project-layers/setup-cadastral-unit-candidates
                          project-layers/ags-surveys
                          project-layers/related-restrictions
                          project-layers/related-cadastral-units
                          project-layers/selected-cadastral-units
                          project-layers/selected-restrictions
                          project-layers/highlighted-road-objects]))
       :overlays (into []
                       (concat
                         (for [[_ {:keys [coordinate content-data]}] (:overlays project)]
                           {:coordinate coordinate
                            :content [map-view/overlay {:single-line? false
                                                        :width 200
                                                        :height nil
                                                        :arrow-direction :top}
                                      [itemlist/ItemList {}
                                       (for [[k v] content-data]
                                         [itemlist/Item {:label k} v])]]})
                         @overlays))}
      map]]))
