(ns teet.project.project-controller
  "Controller for project page"
  (:require [tuck.core :as t]
            [teet.common.common-controller :as common-controller]
            [taoensso.timbre :as log]))


(defrecord RestrictionsResult [result])
(defrecord FetchRestrictions [project-id])
(defrecord ClearRestrictions [project-id])
(defrecord FetchCadastralUnits [])
(defrecord CadastralUnitsResult [project-id result])
(defrecord OpenPhaseDialog [])                              ; open add phase modal dialog
(defrecord ClosePhaseDialog [])
(defrecord OpenTaskDialog [phase-id])
(defrecord CloseTaskDialog [])
(defrecord SelectProject [project-id])

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

  CadastralUnitsResult
  (process-event [{:keys [project-id result]} app]
    (assoc-in app [:project project-id :cadastral-units] result))

  FetchCadastralUnits
  (process-event [_ app]
    (let [project (get-in app [:params :project])]
      (t/fx app
            {:tuck.effect/type :rpc
             :endpoint (get-in app [:config :api-url])
             :rpc "thk_project_related_cadastral_units"
             :method :GET
             :args {:project_id project
                    :distance 200}
             :loading-path [:project project :cadastral-units]
             :result-event (partial ->CadastralUnitsResult project)})))

  RestrictionsResult                                        ;This is not used because using state in query component resulted in re-render loop should probably be looked into
  (process-event [{results :result} app]
    (let [project (get-in app [:params :project])]
      (assoc-in app [:project project :restrictions] results)))

  FetchRestrictions
  (process-event [{project-id :project-id} app]
    (t/fx app
      {:tuck.effect/type :rpc
       :endpoint (get-in app [:config :api-url])
       :rpc "thk_project_related_restrictions"
       :method :GET
       :args {:project_id project-id
              :distance 200}
       :loading-path [:project project-id :restrictions]
       :result-event (fn [result]
                       (->RestrictionsResult result))}))

  ClearRestrictions
  (process-event [{project-id :project-id} app]
    (println "Clear restrictions")
    (update-in app [:project project-id] dissoc :restrictions))

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
       :query {}})))
