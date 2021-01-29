(ns teet.account.account-commands
  (:require [teet.db-api.core :refer [defcommand]]
            [teet.meta.meta-model :as meta-model]))

(defcommand :account/update
  {:doc "Update logged in users email or phone number"
   :context {:keys [db user]}
   :payload payload
   :unauthenticated? true
   :transact [(merge {:db/id (:db/id user)}
                     (select-keys payload
                                  [:user/email :user/phone-number])
                     (meta-model/modification-meta user))]})
