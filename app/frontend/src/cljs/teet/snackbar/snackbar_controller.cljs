(ns teet.snackbar.snackbar-controller
  (:require [tuck.core :as t]))

(defrecord CloseSnackbar [])
(defrecord OpenSnackBar [message variant])

(defn open-snack-bar
  ([app message] (open-snack-bar app message :success))
  ([app message variant]
   (assoc app :snackbar
          {:open? true
           :message message
           :variant variant})))

(extend-protocol t/Event
  OpenSnackBar
  (process-event [{:keys [message variant]} app]
    (open-snack-bar app message variant))

  CloseSnackbar
  (process-event [_ app]
    (println "Closing snackbar")
    (assoc-in app [:snackbar :open?] false)))
