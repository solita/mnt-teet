(ns teet.link.link-controller
  (:require [tuck.core :as t]
            [teet.common.common-controller :as common-controller]))

(defrecord AddLink [from to type in-progress-atom])

(extend-protocol t/Event
  AddLink
  (process-event [{:keys [from to type in-progress-atom]} app]
    (when in-progress-atom
      (reset! in-progress-atom true))
    (t/fx app
          {:tuck.effect/type :command!
           :command :link/add-link
           :payload {:from from
                     :to to
                     :type type}
           :result-event (fn [_]
                           (when in-progress-atom
                             (reset! in-progress-atom false))
                           (common-controller/->Refresh))})))
