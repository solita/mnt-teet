(ns teet.admin.admin-controller
  (:require [tuck.core :as t]
            [teet.common.common-controller :as common-controller]))

(defrecord CreateUser [])
(defrecord UpdateUserForm [form-data])
(defrecord SaveUser [])
(defrecord SaveUserResponse [response])
(defrecord CancelUser [])

(extend-protocol t/Event
  CreateUser
  (process-event [_ app]
    (assoc-in app [:admin :create-user] {}))

  UpdateUserForm
  (process-event [{form-data :form-data} app]
    (update-in app [:admin :create-user] merge form-data))

  SaveUser
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :admin/create-user
           :payload (get-in app [:admin :create-user])
           :result-event ->SaveUserResponse}))

  SaveUserResponse
  (process-event [_ app]
    (-> app
        (update :admin dissoc :create-user)
        common-controller/refresh-page))

  CancelUser
  (process-event [_ app]
    (update app :admin dissoc :create-user)))
