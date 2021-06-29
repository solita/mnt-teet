(ns teet.admin.admin-controller
  (:require [clojure.string :as str]
            [tuck.core :as t]
            [teet.common.common-controller :as common-controller]
            [teet.localization :refer [tr]]))

(defrecord CreateUser [])
(defrecord UpdateUserForm [form-data])
(defrecord SaveUser [])
(defrecord SaveUserResponse [response])
(defrecord CancelUser [])
(defrecord EditUser [form-data])

(defrecord DeactivateUser [user])
(defrecord ReactivateUser [user])

(defrecord AddIndex [])
(defrecord UpdateIndexForm [form-data])
(defrecord SaveIndexResponse [response])
(defrecord SaveIndex [])
(defrecord CancelIndex [])
(defrecord EditIndex [form-data])
(defrecord EditIndexValues [])
(defrecord CancelIndexValues [])
(defrecord UpdateIndexValues [form-data])
(defrecord SaveIndexValuesResponse [response])
(defrecord SaveIndexValues [])

(defn- ensure-starts-with-double-e
  "If the person id doesn't start with EE, prepend it"
  [person-id]
  (if (str/starts-with? person-id "EE")
    person-id
    (str "EE" person-id)))

(extend-protocol t/Event
  CreateUser
  (process-event [_ app]
    (assoc-in app [:admin :create-user] {:user/phone-number "+372" ;; give default value with the estonian locale
                                         }))

  UpdateUserForm
  (process-event [{form-data :form-data} app]
    (update-in app [:admin :create-user] merge form-data))

  SaveUser
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :admin/create-user
           :success-message (tr [:admin :user-created-successfully])
           :payload (-> (get-in app [:admin :create-user])
                        (update :user/person-id ensure-starts-with-double-e))
           :result-event ->SaveUserResponse}))

  EditUser
  (process-event [{form-data :form-data} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :admin/edit-user
           :success-message (tr [:admin :user-edited-successfully])
           :payload form-data
           :result-event common-controller/->Refresh}))


  DeactivateUser
  (process-event [{user-to-deactivate :user} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :admin/deactivate-user
           :success-message (tr [:admin :deactivated-successfully])
           :payload user-to-deactivate
           :result-event common-controller/->Refresh}))

  ReactivateUser
  (process-event [{user-to-reactivate :user} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :admin/reactivate-user
           :success-message (tr [:admin :reactivated-successfully])
           :payload user-to-reactivate
           :result-event common-controller/->Refresh}))


  SaveUserResponse
  (process-event [_ app]
    (-> app
        (update :admin dissoc :create-user)
        common-controller/refresh-page))

  CancelUser
  (process-event [_ app]
    (update app :admin dissoc :create-user))

  AddIndex
  (process-event [_ app]
    (common-controller/assoc-page-state app {:add-index true}))

  CancelIndex
  (process-event [_ app]
    (update app :route dissoc :add-index))

  UpdateIndexForm
  (process-event [{form-data :form-data} app]
    (update-in app [:route :add-index] merge form-data))

  SaveIndexResponse
  (process-event [_ app]
    (-> app
        (update :route dissoc :add-index)
        common-controller/refresh-page))

  SaveIndex
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :admin/add-index
           :success-message (tr [:admin :index-added-successfully])
           :payload (get-in app [:route :add-index])
           :result-event ->SaveIndexResponse}))

  EditIndex
  (process-event [{form-data :form-data} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :admin/edit-index
           :success-message (tr [:admin :index-edited-successfully])
           :payload form-data
           :result-event common-controller/->Refresh}))

  EditIndexValues
  (process-event [_ app]
    (assoc-in app [:route :edit-index-values] {}))

  CancelIndexValues
  (process-event [_ app]
    (update app :route dissoc :edit-index-values))

  UpdateIndexValues
  (process-event [{form-data :form-data} app]
    (update-in app [:route :edit-index-values] merge form-data))

  SaveIndexValuesResponse
  (process-event [_ app]
    (-> app
        (update :route dissoc :edit-index-values)
        common-controller/refresh-page))

  SaveIndexValues
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :admin/edit-index-values
           :success-message (tr [:admin :index-values-changed])
           :payload (get-in app [:route :edit-index-values])
           :result-event ->SaveIndexValuesResponse}))

  )
