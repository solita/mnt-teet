(ns teet.phase.phase-controller
  (:require [teet.routes :as routes]
            [tuck.core :as t]
            [taoensso.timbre :as log]))

(defrecord UpdatePhaseForm [form-data])

(extend-protocol t/Event
  UpdatePhaseForm
  (process-event [{form-data :form-data} app]
    (update-in app [:project (get-in app [:params :project]) :new-phase]
               merge form-data)))
