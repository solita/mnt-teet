(ns teet.account.account-controller
  (:require [tuck.core :as t]
            [teet.localization :refer [tr]]
            [teet.common.common-controller :as common-controller]))

(defrecord UpdateUser [user-form])

(extend-protocol t/Event
  UpdateUser
  (process-event [{user-form :user-form} app]
    (t/fx app
          {:tuck.effect/type :command!
           :command :account/update
           :success-message (tr [:account :account-edited])
           :payload (select-keys user-form [:user/phone-number :user/email])
           :result-event common-controller/->Refresh})))
