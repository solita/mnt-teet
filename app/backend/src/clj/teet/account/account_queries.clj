(ns teet.account.account-queries
  (:require [datomic.client.api :as d]
            [teet.db-api.core :as db-api :refer [defquery]]))

(defquery :account/account-page
  {:doc "query information about the current logged in user"
   :context {db :db
             user :user}
   :unauthenticated? true
   :args {}}
  (d/pull db '[:user/phone-number :user/email
               :user/given-name :user/family-name]
          (:db/id user)))
