(ns teet.project.project-controller
  "Controller for project page"
  (:require [tuck.core :as t]
            [teet.common.common-controller :as common-controller]
            [taoensso.timbre :as log]
            [teet.map.map-controller :as map-controller]))


(defrecord OpenPhaseDialog [])                              ; open add phase modal dialog
(defrecord ClosePhaseDialog [])
(defrecord OpenTaskDialog [phase-id])
(defrecord CloseTaskDialog [])
(defrecord SelectProject [project-id])
(defrecord HighlightCadastralUnit [id])


(defmethod common-controller/map-item-selected
  "geojson_thk_project_pins" [p]
  (->SelectProject (:map/id p)))

(defmethod common-controller/map-item-selected
  "mvt_thk_projects" [p]
  (->SelectProject (:map/id p)))

(defmethod common-controller/map-item-selected
  "geojson_thk_project" [p]
  (->SelectProject (:map/id p)))

(extend-protocol t/Event
  SelectProject
  (process-event [{project-id :project-id} app]
    (t/fx app
      {:tuck.effect/type :navigate
       :page :project
       :params {:project project-id}}))

  OpenPhaseDialog
  (process-event [_ app]
    (t/fx app
      {:tuck.effect/type :navigate
       :page :project
       :params {:project (get-in app [:params :project])}
       :query {:add-phase 1}}))

  ClosePhaseDialog
  (process-event [_ app]
    (t/fx app
      {:tuck.effect/type :navigate
       :page :project
       :params {:project (get-in app [:params :project])}
       :query {}}))

  OpenTaskDialog
  (process-event [{phase-id :phase-id} app]
    (t/fx app
      {:tuck.effect/type :navigate
       :page :project
       :params {:project (get-in app [:params :project])}
       :query {:add-task phase-id}}))

  CloseTaskDialog
  (process-event [_ app]
    (t/fx app
      {:tuck.effect/type :navigate
       :page :project
       :params {:project (get-in app [:params :project])}
       :query {}}))

  HighlightCadastralUnit
  (process-event [{new-highlighted-id :id} app]
    (let [previous-highlighted-id (::highlight-cadastral-unit app)]
      (map-controller/update-features!
       "geojson_thk_project_related_cadastral_units"
       (fn [unit]
         (let [id (.get unit "id")]
           (cond
             (= id previous-highlighted-id)
             (.set unit "selected" false)

             (= id new-highlighted-id)
             (.set unit "selected" true)

             :else
             nil))))
      (assoc app ::highlight-cadastral-unit new-highlighted-id))))

(defn cadastral-units-rpc [project]
  {:rpc "thk_project_related_cadastral_units"
   :args {:project_id project
          :distance 200}})

(defn restrictions-rpc [project]
  {:rpc "thk_project_related_restrictions"
   :args {:project_id project
          :distance 200}})
