(ns teet.land.owner-opinion-controller
  (:require [tuck.core :as t]
            [teet.common.common-controller :as common-controller]))

(defrecord SubmitOpinionForm [form-data])
(defrecord OpinionFormClose [])
(defrecord OpinionFormOpen [])
(defrecord IncreaseCommentCount [land-unit-id])
(defrecord DecreaseCommentCount [land-unit-id])

(extend-protocol t/Event
  SubmitOpinionForm
  (process-event [{:keys [form-data project-id land-unit-id]} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :land-owner-opinion/submit
           :payload {:form-data form-data
                     :project-id project-id
                     :land-unit-id land-unit-id}
           :result-event common-controller/->Refresh}))

  IncreaseCommentCount
  (process-event [{:keys [land-unit-id]} app]
    (update-in app [:route :project :land-owner-comment-count land-unit-id] inc))

  DecreaseCommentCount
  (process-event [{:keys [land-unit-id]} app]
    (update-in app [:route :project :land-owner-comment-count land-unit-id] dec))

  OpinionFormOpen
  (process-event [_ app]
    (assoc-in app [:query :modal-new-opinion] "true"))

  OpinionFormClose
  (process-event [_ app]
    (update app :query dissoc :modal-new-opinion)))
