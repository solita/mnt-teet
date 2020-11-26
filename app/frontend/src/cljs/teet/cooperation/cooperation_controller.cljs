(ns teet.cooperation.cooperation-controller
  (:require [tuck.core :as t]
            [teet.log :as log]
            [teet.snackbar.snackbar-controller :as snackbar-controller]
            [teet.localization :refer [tr]]))

(defrecord SaveApplication [application])
(defrecord ApplicationCreated [save-response])

(extend-protocol t/Event
  SaveApplication
  (process-event [{application :application} app]
    (log/info "Save application: " application)
    app)

  ApplicationCreated
  (process-event [{r :save-response} {params :params :as app}]
    (let [application-id (get-in r [:tempids "new-application"])]
      (t/fx (snackbar-controller/open-snack-bar app (tr [:cooperation :new-application-created]))
            {:tuck.effect/type :navigate
             :page :cooperation-application
             :params (merge
                      {:project (:project params)
                       :third-party (js/decodeURIComponent (:third-party params))
                       :application (str application-id)})}))))
