(ns teet.phase.phase-controller
  (:require [tuck.core :as t]
            [teet.log :as log]
            [teet.common.common-controller :as common-controller]))

(defrecord UpdatePhaseForm [form-data])
(defrecord CreatePhase [])
(defrecord CreatePhaseResult [result])


(extend-protocol t/Event
  UpdatePhaseForm
  (process-event [{form-data :form-data} app]
    (update-in app [:project (get-in app [:params :project]) :new-phase]
               merge form-data))

  CreatePhase
  (process-event [_ app]
    (let [project (get-in app [:params :project])
          [start end] (get-in app [:project project :new-phase :phase/estimated-date-range])
          payload (-> (get-in app [:project project :new-phase])
                      (dissoc :phase/estimated-date-range)
                      (assoc :phase/estimated-start-date start)
                      (assoc :phase/estimated-end-date end))]
      (t/fx (assoc-in app [:project project :create-phase-in-progress?] true)
            {:tuck.effect/type :command!
             :command :phase/create-phase
             :payload (merge {:db/id "new-phase"
                              :thk/id project}
                             payload) ;;TODO pura date-range
             :result-event ->CreatePhaseResult})))

  CreatePhaseResult
  (process-event [{result :result} app]
    (let [project (get-in app [:params :project])]
      (log/info "CREATE PHASE RESULT: " result)
      (t/fx
       (-> app
           (update-in [:project project] dissoc :new-phase)
           (assoc-in [:project project :create-phase-in-progress?] false))
       {:tuck.effect/type :navigate
        :page :project
        :params {:project (get-in app [:params :project])}
        :query {}}
       common-controller/refresh-fx))))
