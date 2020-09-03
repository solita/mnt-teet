(ns teet.snackbar.snackbar-controller
  (:require [tuck.core :as t]))

(defrecord CloseSnackbar [])
(defrecord OpenSnackBar [message variant])
(defrecord OpenSnackBarWithOptions [options])

(defn open-snack-bar
  ([app message]
   (open-snack-bar {:app app
                    :message message}))
  ([app message variant]
   (open-snack-bar {:app app
                    :message message
                    :variant variant}))
  ([{:keys [app message variant hide-duration
            action]
     :or {variant :success hide-duration 5000}}]
   (assoc app :snackbar
          {:open? true
           :message message
           :variant variant
           :hide-duration hide-duration
           :action action})))

(extend-protocol t/Event
  OpenSnackBar
  (process-event [{:keys [message variant]} app]
    (open-snack-bar app message variant))

  OpenSnackBarWithOptions
  (process-event [{:keys [options]} app]
    (open-snack-bar (assoc options :app app)))

  CloseSnackbar
  (process-event [_ app]
    (assoc-in app [:snackbar :open?] false)))
