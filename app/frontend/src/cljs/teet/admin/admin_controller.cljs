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
(defrecord AddIndexNav [])
(defrecord UpdateAddIndexForm [form-data])
(defrecord SaveIndexResponse [response])
(defrecord SaveIndex [])
(defrecord CancelIndex [])
(defrecord EditIndexForm [])
(defrecord UpdateEditIndexForm [form-data])
(defrecord CancelEditIndex [])
(defrecord DeleteIndex [index-id])
(defrecord DeleteIndexResponse [])
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
  (process-event [_ {:keys [query] :as app}]
    (t/fx (common-controller/assoc-page-state app [:add-index] {:form-open true})
          {:tuck.effect/type :navigate
           :page :admin-indexes
           :query (assoc query :add-index-form 1)}))

  CancelIndex
  (process-event [_ app]
    (-> app
        (common-controller/update-page-state [] dissoc :add-index)
        (dissoc :query)
        common-controller/refresh-page))

  UpdateAddIndexForm
  (process-event [{form-data :form-data} app]
    (common-controller/update-page-state app [:add-index] merge form-data))

  SaveIndexResponse
  (process-event [_ app]
    (-> app
        (common-controller/update-page-state [] dissoc :add-index)
        (dissoc :query)
        common-controller/refresh-page))

  SaveIndex
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :index/add-index
           :success-message (tr [:admin :index-added-successfully])
           :payload (get-in app [:route :admin-indexes :add-index])
           :result-event ->SaveIndexResponse}))

  EditIndexForm
  (process-event [_ app]
    (common-controller/assoc-page-state app [:edit-index] {:form-open true}))

  UpdateEditIndexForm
  (process-event [{form-data :form-data} app]
    (common-controller/update-page-state app [:edit-index] merge form-data {:index-id (get-in app [:params :id])}))

  CancelEditIndex
  (process-event [_ app]
    (common-controller/update-page-state app [] dissoc :edit-index))

  DeleteIndex
  (process-event [{index-id :index-id} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :index/delete-index
           :success-message (tr [:admin :index-deleted-successfully])
           :payload {:index-id (common-controller/->long index-id)}
           :result-event ->DeleteIndexResponse}))

  DeleteIndexResponse
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :navigate
           :page :admin-indexes}
          common-controller/refresh-fx))

  EditIndex
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :index/edit-index
           :success-message (tr [:admin :index-edited-successfully])
           :payload (get-in app [:route :admin-index-page :edit-index])
           :result-event common-controller/->Refresh}))

  EditIndexValues
  (process-event [_ app]
    (common-controller/assoc-page-state app [:edit-index-values] {:form-open true}))

  CancelIndexValues
  (process-event [_ app]
    (common-controller/update-page-state app [] dissoc :edit-index-values))

  UpdateIndexValues
  (process-event [{form-data :form-data} app]
    (common-controller/update-page-state app [:edit-index-values] merge form-data {:index-id (get-in app [:params :id])}))

  SaveIndexValuesResponse
  (process-event [_ app]
    (-> app
        (common-controller/update-page-state [] dissoc :edit-index-values)
        common-controller/refresh-page))

  SaveIndexValues
  (process-event [_ app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :index/edit-index-values
           :success-message (tr [:admin :index-edited-successfully])
           :payload (get-in app [:route :admin-index-page :edit-index-values])
           :result-event ->SaveIndexValuesResponse}))

  )
