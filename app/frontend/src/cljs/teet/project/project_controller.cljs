(ns teet.project.project-controller
  "Controller for project page"
  (:require [tuck.core :as t]
            [teet.common.common-controller :as common-controller]
            [teet.log :as log]
            [teet.map.map-controller :as map-controller]))


(defrecord OpenActivityDialog [])                              ; open add activity modal dialog
(defrecord CloseActivityDialog [])
(defrecord OpenTaskDialog [activity-id])
(defrecord CloseTaskDialog [])
(defrecord SelectProject [project-id])
(defrecord ToggleCadastralHightlight [id])
(defrecord ToggleRestrictionData [id])
(defrecord UpdateActivityState [id status])

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

  OpenActivityDialog
  (process-event [_ app]
    (t/fx app
      {:tuck.effect/type :navigate
       :page :project
       :params {:project (get-in app [:params :project])}
       :query {:add-activity 1}}))

  CloseActivityDialog
  (process-event [_ app]
    (t/fx app
      {:tuck.effect/type :navigate
       :page :project
       :params {:project (get-in app [:params :project])}
       :query {}}))

  OpenTaskDialog
  (process-event [{activity-id :activity-id} app]
    (t/fx app
      {:tuck.effect/type :navigate
       :page :project
       :params {:project (get-in app [:params :project])}
       :query {:add-task activity-id}}))

  UpdateActivityState
  (process-event [{activity-id :id status :status} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :activity/update-activity
           :payload {:db/id activity-id
                     :activity/status status}
           :result-event common-controller/->Refresh
           :success-message "Activity updated successfully"    ;TODO add to localizations
           }))

  CloseTaskDialog
  (process-event [_ app]
    (t/fx app
      {:tuck.effect/type :navigate
       :page :project
       :params {:project (get-in app [:params :project])}
       :query {}}))

  ToggleRestrictionData
  (process-event [{restriction-id :id} app]
    (log/info "toggle restrictiondata " restriction-id)
    (map-controller/update-features!
      "geojson_thk_project_related_restrictions"
      (fn [unit]
        (let [id (.get unit "id")
              open? (.get unit "selected")]
          (when (= id restriction-id)
            (.set unit "selected" (not open?))))))
    (let [project-id (get-in app [:params :project])]
      (update-in app [:project project-id :restrictions]
        #(map
           (fn [e]
             (if (= (:id e) restriction-id)
               (assoc e :open? (not (:open? e)))
               e))
           %))))

  ToggleCadastralHightlight
  (process-event [{new-highlighted-id :id} app]
    (map-controller/update-features!
     "geojson_thk_project_related_cadastral_units"
     (fn [unit]
       (let [id (.get unit "id")
             open? (.get unit "selected")]
         (when (= id new-highlighted-id)
           (.set unit "selected" (not open?))))))
    (let [project-id (get-in app [:params :project])]
      (update-in app [:project project-id :cadastral-units]
        #(map
           (fn [e]
             (if (= (:id e) new-highlighted-id)
               (assoc e :open? (not (:open? e)))
               e))
           %)))))

(defn cadastral-units-rpc [project]
  {:rpc "thk_project_related_cadastral_units"
   :args {:entity_id (:db/id project)
          :distance 200}})

(defn restrictions-rpc [project]
  {:rpc "thk_project_related_restrictions"
   :args {:entity_id (:db/id project)
          :distance 200}})

(defmethod common-controller/map-item-selected
  "geojson_thk_project_related_cadastral_units"
  [p]
  ;(log/info "cadastral item selected: " p)
  (->ToggleCadastralHightlight (:map/id p)))

(defmethod common-controller/map-item-selected
  "geojson_thk_project_related_restrictions"
  [p]
  ;(log/info "cadastral item selected: " p)
  (->ToggleRestrictionData (:map/id p)))
