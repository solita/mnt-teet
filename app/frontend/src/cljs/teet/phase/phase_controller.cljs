(ns teet.phase.phase-controller
  (:require [teet.routes :as routes]
            [tuck.core :as t]
            [taoensso.timbre :as log]))

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
    (let [project (get-in app [:params :project])]
      (t/fx (assoc-in app [:project project :create-phase-in-progress?] true)
            {:tuck.effect/type :command!
             :command :phase/create-phase
             :payload (merge {:db/id "new-phase"
                              :thk/id project}
                             (get-in app [:project project :new-phase]))
             :result-event ->CreatePhaseResult})))

  CreatePhaseResult
  (process-event [{result :result} app]
    (log/info "CREATE PHASE RESULT: " result)
    (t/fx (assoc-in app [:project (get-in app [:params :project]) :create-phase-in-progress?] false)
          {:tuck.effect/type :navigate
           :page :project
           :params {:project (get-in app [:params :project])}
           :query {}})))
