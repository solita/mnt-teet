(ns teet.navigation.navigation-controller
  (:require [tuck.core :as t]
            [tuck.effect :as tuck-effect]
            [teet.log :as log]))

(defrecord ToggleDrawer [])
(defrecord GoToLogin [])
(defrecord ToggleExtraPanel [extra-panel])
(defrecord CloseExtraPanel [])

(extend-protocol t/Event
  ToggleDrawer
  (process-event [_ app]
    (update-in app [:navigation :open?] not))

  GoToLogin
  (process-event [_ app]
    (log/info "GoToLogin event")
    (t/fx app
          {::tuck-effect/type :navigate
           :page :login}))

  CloseExtraPanel
  (process-event [_ app]
    (assoc-in app [:navigation :extra-panel-open?] false))

  ToggleExtraPanel
  (process-event [{extra-panel :extra-panel} app]
    (let [open? (get-in app [:navigation :extra-panel-open?])]
      (if (= (get-in app [:navigation :extra-panel]) extra-panel)
        (assoc-in app [:navigation :extra-panel-open?] (not open?))
        (-> app
            (assoc-in [:navigation :extra-panel] extra-panel)
            (assoc-in [:navigation :extra-panel-open?] true))))))

