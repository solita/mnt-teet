(ns teet.snackbar.snackbar-controller
  (:require [tuck.core :as t]))

(defrecord CloseSnackbar [])
(defrecord OpenSnackBar [message variant])

(extend-protocol t/Event
  OpenSnackBar
  (process-event [{:keys [message variant]} app]
    (println "opening snackbar" message "variant" variant)
    (assoc-in app [:snackbar]
              {:open? true
               :message message
               :variant variant}))

  CloseSnackbar
  (process-event [_ app]
    (println "Closing snackbar")
    (assoc-in app [:snackbar :open?] false)))
