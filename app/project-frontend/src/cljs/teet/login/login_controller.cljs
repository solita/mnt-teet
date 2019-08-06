(ns teet.login.login-controller
  (:require [tuck.core :as t]
            [taoensso.timbre :as log]))

(defrecord UpdateLoginForm [form])
(defrecord Login [])

(extend-protocol t/Event
  UpdateLoginForm
  (process-event [{form :form} app]
    (assoc-in app [:login :form] form))

  Login
  (process-event [_ app]
    (log/info "Log in")
    app))
